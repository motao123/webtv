package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.server.ServerAuth;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.utils.UrlSafety;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

/**
 * HLS relay.
 *
 * <p>Two callers depend on it. Python live sources emit {@code 127.0.0.1:9978/m3u8} links and
 * rely on the built-in headers below. Casting instead needs two extra things: the real headers
 * the player used, and rewritten URLs addressed to this phone over the LAN — a renderer fetching
 * {@code 127.0.0.1} would be asking itself. Both are selected per request, so the legacy path is
 * untouched.</p>
 */
public class M3u8 implements Process {

    /** Query param carrying a {@link HeaderStore} id. Absent means "use the legacy headers". */
    public static final String PARAM_ID = "id";

    private static final String TAG = "live-m3u8";
    /**
     * Redirects are followed by hand so every hop can be re-validated; see {@link #executeChecked}.
     * The client therefore must not follow them itself.
     */
    private static final int MAX_REDIRECTS = 5;
    private static final OkHttpClient CLIENT = OkHttp.player().newBuilder().followRedirects(false).followSslRedirects(false).build();
    private static final String MIME_M3U8 = "application/vnd.apple.mpegurl; charset=utf-8";
    private static final String DEFAULT_UA = "okhttp/4.9.2";
    private static final String DEFAULT_REFERER = "https://www.4gtv.tv/";
    private static final String DEFAULT_ORIGIN = "https://www.4gtv.tv";
    private static final Pattern URI_ATTR = Pattern.compile("URI=\"([^\"]+)\"");

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return "/m3u8".equals(url);
    }

    /**
     * Registers the headers a cast must send upstream and returns the id to embed in the proxy
     * URL. Public so the casting side can call it directly — it runs in the same process.
     */
    public static String registerHeaders(Map<String, String> headers) {
        return HeaderStore.put(headers);
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        String target = session.getParms().get("url");
        if (!isHttp(target)) return Nano.error(Response.Status.BAD_REQUEST, "Missing or invalid url");
        // The proxy target comes from a query param: without this guard any web page or LAN peer
        // could turn the device into an SSRF relay against the router, intranet or cloud metadata.
        if (!UrlSafety.isSafeHttpUrl(target)) return Nano.error(Response.Status.BAD_REQUEST, "Unsupported url");
        String id = session.getParms().get(PARAM_ID);
        boolean hasContext = HeaderStore.has(id);
        Map<String, String> headers = HeaderStore.get(id);
        // A renderer reaching us over the LAN must be sent LAN-addressed segment URLs; the local
        // player keeps getting loopback ones exactly as before.
        boolean lan = !ServerAuth.isLocal(session);
        okhttp3.Response upstream = null;
        try {
            Request request = request(session, target, headers, hasContext);
            long start = System.currentTimeMillis();
            upstream = executeChecked(request);
            SpiderDebug.log(TAG, "%s %s -> %s in %sms lan=%s stored=%s", request.method(), shortUrl(target), upstream.code(), System.currentTimeMillis() - start, lan, id != null);
            ResponseBody body = upstream.body();
            if (body == null) {
                upstream.close();
                return Nano.error(Response.Status.INTERNAL_ERROR, "Empty upstream body");
            }
            if (isPlaylist(upstream.request().url().toString(), body.contentType())) return playlist(upstream, body, id, lan);
            return stream(upstream, body);
        } catch (Throwable e) {
            if (upstream != null) upstream.close();
            SpiderDebug.log(TAG, e);
            return Nano.error(Response.Status.INTERNAL_ERROR, TextUtils.isEmpty(e.getMessage()) ? e.toString() : e.getMessage());
        }
    }

    private Request request(IHTTPSession session, String target, Map<String, String> headers, boolean hasContext) {
        Request.Builder builder = new Request.Builder().url(target);
        Map<String, String> upstream = headerMap(headers, hasContext);
        for (Map.Entry<String, String> entry : upstream.entrySet()) builder.header(entry.getKey(), entry.getValue());
        String range = session.getHeaders().get("range");
        if (!TextUtils.isEmpty(range)) builder.header("Range", range);
        if (session.getMethod() == NanoHTTPD.Method.HEAD) builder.head();
        return builder.build();
    }

    /**
     * With a stored context the caller's headers are authoritative and used as-is. Without one
     * we fall back to the historical defaults, which the bundled Python live sources depend on.
     *
     * <p>Sending the 4gtv referer to an unrelated source would be worse than sending none: a
     * mismatched referer is itself a reason for a CDN to answer 403.</p>
     */
    private Map<String, String> headerMap(Map<String, String> stored, boolean hasContext) {
        // A valid context with no headers means a public source: send exactly what the caller
        // supplied (nothing). Falling back to the 4gtv defaults here would inject a wrong
        // Referer, which is itself a common reason for a CDN to answer 403.
        if (hasContext) return stored;
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("User-Agent", DEFAULT_UA);
        defaults.put("Referer", DEFAULT_REFERER);
        defaults.put("Origin", DEFAULT_ORIGIN);
        return defaults;
    }

    /**
     * Follows redirects one hop at a time, re-checking every target with {@link UrlSafety}.
     *
     * <p>Checking only the URL from the query param is not enough: a perfectly public host can
     * answer with a 302 pointing at the router, an intranet address or the cloud metadata service,
     * and a client left to follow redirects on its own will happily connect to it — which is the
     * very SSRF the caller's guard exists to prevent. Redirects still work, they are just
     * inspected; the URL of the final hop is what the caller uses as the playlist base, so the
     * rewrite behaviour is unchanged.</p>
     */
    private okhttp3.Response executeChecked(Request request) throws IOException {
        Request current = request;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            okhttp3.Response response = CLIENT.newCall(current).execute();
            if (!isRedirect(response.code())) return response;
            String location = response.header("Location");
            if (TextUtils.isEmpty(location)) return response;
            response.close();
            if (hop == MAX_REDIRECTS) break;
            HttpUrl next = current.url().resolve(location);
            if (next == null || !UrlSafety.isSafeHttpUrl(next.toString())) throw new IOException("Unsafe redirect target");
            current = current.newBuilder().url(next).build();
        }
        throw new IOException("Too many redirects");
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private Response playlist(okhttp3.Response upstream, ResponseBody body, String id, boolean lan) throws IOException {
        try (upstream; body) {
            if (!upstream.isSuccessful()) return error(status(upstream.code()), "Playlist HTTP " + upstream.code());
            String text = body.string();
            if (!looksLikePlaylist(text)) return Nano.error(Response.Status.BAD_REQUEST, "Invalid playlist");
            String rewritten = rewrite(upstream.request().url(), text, id, lan);
            byte[] bytes = rewritten.getBytes(StandardCharsets.UTF_8);
            SpiderDebug.log(TAG, "playlist bytes=%s rewritten=%s url=%s", text.length(), bytes.length, shortUrl(upstream.request().url().toString()));
            return noCache(NanoHTTPD.newFixedLengthResponse(Response.Status.OK, MIME_M3U8, new ByteArrayInputStream(bytes), bytes.length));
        }
    }

    private Response stream(okhttp3.Response upstream, ResponseBody body) {
        long length = body.contentLength();
        String mime = body.contentType() == null ? "application/octet-stream" : body.contentType().toString();
        InputStream input = new CloseResponseInputStream(body.byteStream(), upstream);
        Response response = length >= 0
                ? NanoHTTPD.newFixedLengthResponse(status(upstream.code()), mime, input, length)
                : NanoHTTPD.newChunkedResponse(status(upstream.code()), mime, input);
        copyHeader(upstream, response, "Accept-Ranges");
        copyHeader(upstream, response, "Content-Range");
        copyHeader(upstream, response, "Cache-Control");
        return response;
    }

    private String rewrite(HttpUrl base, String text, String id, boolean lan) {
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder output = new StringBuilder(text.length() + 512);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                line = proxy(resolve(base, trimmed), id, lan);
            } else if (trimmed.startsWith("#") && trimmed.contains("URI=\"")) {
                line = rewriteUriAttributes(base, line, id, lan);
            }
            output.append(line);
            if (i + 1 < lines.length) output.append('\n');
        }
        return output.toString();
    }

    private String rewriteUriAttributes(HttpUrl base, String line, String id, boolean lan) {
        Matcher matcher = URI_ATTR.matcher(line);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String replacement = "URI=\"" + proxy(resolve(base, matcher.group(1)), id, lan) + "\"";
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String resolve(HttpUrl base, String value) {
        HttpUrl resolved = base.resolve(value);
        return resolved == null ? value : resolved.toString();
    }

    /**
     * Rewritten entries must come back through us, and carry the context id so segments keep
     * using the same upstream headers. When the caller is on the LAN (a renderer) the address
     * must be our LAN one and must carry the server token, otherwise the fetch is rejected.
     */
    private String proxy(String target, String id, boolean lan) {
        StringBuilder path = new StringBuilder("/m3u8?url=").append(URLEncoder.encode(target, StandardCharsets.UTF_8));
        if (!TextUtils.isEmpty(id)) path.append('&').append(PARAM_ID).append('=').append(id);
        if (!lan) return Server.get().getAddress(path.toString());
        return ServerAuth.withToken(Server.get().getAddress(false) + path);
    }

    private boolean isPlaylist(String url, MediaType type) {
        String mime = type == null ? "" : type.toString().toLowerCase(Locale.US);
        if (mime.contains("mpegurl") || mime.contains("m3u8")) return true;
        String lower = url.toLowerCase(Locale.US);
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        return lower.endsWith(".m3u8") || lower.endsWith(".m3u");
    }

    private boolean looksLikePlaylist(String text) {
        return !TextUtils.isEmpty(text) && text.trim().startsWith("#EXTM3U");
    }

    private boolean isHttp(String url) {
        return !TextUtils.isEmpty(url) && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private Response noCache(Response response) {
        response.addHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.addHeader("Pragma", "no-cache");
        return response;
    }

    private Response error(Response.IStatus status, String message) {
        return NanoHTTPD.newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, message);
    }

    private void copyHeader(okhttp3.Response source, Response target, String name) {
        String value = source.header(name);
        if (!TextUtils.isEmpty(value)) target.addHeader(name, value);
    }

    private Response.IStatus status(int code) {
        Response.Status status = Response.Status.lookup(code);
        return status == null ? Response.Status.INTERNAL_ERROR : status;
    }

    private String shortUrl(String url) {
        return url.length() <= 180 ? url : url.substring(0, 180) + "...";
    }

    private static class CloseResponseInputStream extends FilterInputStream {

        private final okhttp3.Response response;

        private CloseResponseInputStream(InputStream input, okhttp3.Response response) {
            super(input);
            this.response = response;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                response.close();
            }
        }
    }
}