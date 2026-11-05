package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.utils.UrlSafety;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;

/**
 * Serves an HLS source as one continuous MPEG-TS stream for DLNA renderers that cannot read a
 * playlist at all.
 *
 * <p>A MPEG-TS stream is a sequence of self-contained 188-byte packets with no global header,
 * so {@code .ts} segments can simply be appended in playlist order — that is exactly what
 * {@code ffmpeg -c copy -f mpegts} does, and it needs neither a native binary nor re-encoding.
 * Sources that cannot be concatenated byte-for-byte (fMP4, AES-128, discontinuities) are
 * rejected with a plain-text reason instead of producing a silent black screen.</p>
 *
 * <p>The stream is lazy: segments are fetched only as the renderer reads, so start-up is one
 * segment rather than the whole video, and nothing is written to disk.</p>
 */
public class TsCast implements Process {

    public static final String PATH = "/cast.ts";
    public static final String MIME_TS = "video/MP2T";

    private static final String TAG = "ts-cast";
    private static final int MAX_REDIRECTS = 5;
    private static final OkHttpClient CLIENT = OkHttp.player().newBuilder()
            .followRedirects(false).followSslRedirects(false).build();

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return PATH.equals(url);
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        String target = session.getParms().get("url");
        if (TextUtils.isEmpty(target) || !(target.startsWith("http://") || target.startsWith("https://"))) {
            return Nano.error(Response.Status.BAD_REQUEST, "Missing or invalid url");
        }
        String id = session.getParms().get(M3u8.PARAM_ID);
        // A public source legitimately has no headers; the id itself proves this process started
        // the cast. Requiring a non-empty header map would reject exactly those sources.
        if (!HeaderStore.has(id)) return Nano.error(Response.Status.BAD_REQUEST, "Missing or expired cast context");
        Map<String, String> headers = HeaderStore.get(id);

        TsPlaylist.Parsed parsed;
        try {
            // Follow master playlists here, on the server. A 302 would leave the renderer to
            // re-issue the request, and many DLNA renderers simply do not follow redirects.
            parsed = loadPlaylist(target, headers, 0);
            for (int hop = 0; hop < MAX_REDIRECTS && !TextUtils.isEmpty(parsed.variant); hop++) {
                parsed = loadPlaylist(parsed.variant, headers, 0);
            }
        } catch (Exception e) {
            SpiderDebug.log(TAG, e);
            return Nano.error(Response.Status.BAD_REQUEST, "Playlist unavailable: " + message(e));
        }
        if (parsed.kind == TsPlaylist.Kind.UNSUPPORTED) {
            // A variant chain still pointing at a master playlist is a broken source, not a
            // capability gap. Say so rather than looping forever.
            String reason = TextUtils.isEmpty(parsed.variant) ? parsed.reason : "Nested master playlists";
            return Nano.error(Response.Status.BAD_REQUEST, "Unsupported for TS cast: " + reason);
        }
        if (parsed.kind != TsPlaylist.Kind.VOD) {
            // A rolling live playlist would need continuous re-fetching and timeline repair;
            // refuse it explicitly rather than ending the stream after the first window.
            return Nano.error(Response.Status.BAD_REQUEST, "Live playlists are not supported for TS cast");
        }

        SpiderDebug.log(TAG, "start segments=%d url=%s", parsed.segments.size(), shortUrl(target));
        SequenceStream stream = new SequenceStream(parsed.segments, headers);
        Response response = NanoHTTPD.newChunkedResponse(Response.Status.OK, MIME_TS, stream);
        response.addHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.addHeader("Pragma", "no-cache");
        response.addHeader("X-Content-Type-Options", "nosniff");
        // A live TV keeps the connection open for the whole film; without keep-alive some
        // renderers interpret the end of the first write as end of stream.
        response.addHeader("Connection", "keep-alive");
        return response;
    }

    private TsPlaylist.Parsed loadPlaylist(String url, Map<String, String> headers, int depth) throws IOException {
        if (depth > MAX_REDIRECTS) throw new IOException("Too many redirects");
        if (!UrlSafety.isSafeHttpUrl(url)) throw new IOException("Unsafe playlist url");
        okhttp3.Response response = fetch(url, headers);
        try (response; ResponseBody body = response.body()) {
            if (isRedirect(response.code())) {
                String location = response.header("Location");
                if (TextUtils.isEmpty(location)) throw new IOException("Redirect without location");
                String next = java.net.URI.create(url).resolve(location).toString();
                return loadPlaylist(next, headers, depth + 1);
            }
            if (!response.isSuccessful() || body == null) throw new IOException("Playlist HTTP " + response.code());
            return TsPlaylist.parse(body.string(), url);
        }
    }

    private okhttp3.Response fetch(String url, Map<String, String> headers) throws IOException {
        if (!UrlSafety.isSafeHttpUrl(url)) throw new IOException("Unsafe segment url");
        Request.Builder builder = new Request.Builder().url(url);
        for (Map.Entry<String, String> entry : headers.entrySet()) builder.header(entry.getKey(), entry.getValue());
        return CLIENT.newCall(builder.build()).execute();
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private static String message(Throwable e) {
        return TextUtils.isEmpty(e.getMessage()) ? e.toString() : e.getMessage();
    }

    private static String shortUrl(String url) {
        return url.length() <= 140 ? url : url.substring(0, 140) + "...";
    }

    /**
     * Concatenates segments lazily. Each segment's response is held only while its bytes are
     * being read, so memory stays flat and a disconnected renderer releases the upstream
     * connection on {@link #close()}.
     */
    private final class SequenceStream extends InputStream {

        private final List<String> segments;
        private final Map<String, String> headers;
        private int index;
        private okhttp3.Response current;
        private InputStream input;
        private boolean closed;

        private SequenceStream(List<String> segments, Map<String, String> headers) {
            this.segments = segments;
            this.headers = headers;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (closed) throw new IOException("Stream closed");
            if (length == 0) return 0;
            while (true) {
                if (input == null) {
                    if (index >= segments.size()) return -1;
                    open(index++);
                }
                int n = input.read(buffer, offset, length);
                if (n > 0) return n;
                if (n < 0) {
                    release();
                    continue;
                }
                // A zero-length read would emit an empty chunk, which some renderers treat as
                // end of stream. Keep pulling instead.
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("cast stopped");
            }
        }

        private void open(int position) throws IOException {
            String url = segments.get(position);
            okhttp3.Response response = null;
            // A single dead segment should not kill a two-hour film. Retry once, then skip:
            // MPEG-TS tolerates a missing chunk far better than a truncated connection.
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    response = fetch(url, headers);
                    if (!response.isSuccessful() || response.body() == null) {
                        response.close();
                        response = null;
                        continue;
                    }
                    break;
                } catch (IOException e) {
                    if (response != null) response.close();
                    response = null;
                    if (attempt == 1) {
                        SpiderDebug.log(TAG, "skip segment %d: %s", position, message(e));
                        return;
                    }
                }
            }
            if (response == null) {
                SpiderDebug.log(TAG, "skip segment %d: upstream failed", position);
                return;
            }
            current = response;
            input = response.body().byteStream();
        }

        private void release() {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
                input = null;
            }
            if (current != null) {
                current.close();
                current = null;
            }
        }

        @Override
        public void close() throws IOException {
            closed = true;
            release();
            super.close();
        }

        @Override
        public int available() {
            // A declared length here is a guess at best: the remaining segments have not been
            // fetched yet. Returning 0 keeps NanoHTTPD from ever treating the stream as finite.
            return 0;
        }
    }
}