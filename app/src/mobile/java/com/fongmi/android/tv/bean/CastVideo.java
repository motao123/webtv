package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.server.ServerAuth;
import com.fongmi.android.tv.server.process.M3u8;
import com.fongmi.android.tv.server.process.TsCast;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

public record CastVideo(String name, String url, long position, Map<String, String> headers) {

    public CastVideo {
        // /file is a token-required path, so a locally stored video must carry the token too —
        // otherwise the renderer gets a 401 while the in-app player (which reads the file
        // directly) works fine.
        if (url.startsWith("file")) url = ServerAuth.withToken(Server.get().getAddress() + "/" + url.replace(Path.rootPath(), "").replace("://", ""));
        if (url.contains("127.0.0.1")) url = url.replace("127.0.0.1", Util.getIp());
    }

    /**
     * Builds the URL to hand the renderer. Called at cast time, not construction time: the
     * compatibility switch lives in the device-selection dialog, which is shown after this
     * record has already been created.
     *
     * @param tsCompat true for renderers that cannot read HLS playlists at all; the phone then
     *                 concatenates the .ts segments into one continuous MPEG-TS stream.
     */
    public String castUrl(boolean tsCompat) {
        return tsCompat ? compatUrl(url, headers) : relay(url, headers);
    }

    /** True when the resolved URL is our continuous MPEG-TS endpoint. */
    public static boolean isTsCast(String url) {
        return url != null && url.contains(TsCast.PATH);
    }

    /**
     * A renderer only ever receives a URL, so it cannot attach the Referer / User-Agent / Cookie
     * that most HLS sources check on every segment — which is why such a source plays fine in the
     * app but fails on a TV. When the player needed headers we relay through this device instead:
     * it fetches with the real headers and hands the rewritten playlist to the TV.
     *
     * <p>Links that need no headers are still cast directly, so they pay no relay cost and the
     * phone stays out of the stream path.</p>
     */
    private static String relay(String url, Map<String, String> headers) {
        // Already routed through us: Python live sources emit 127.0.0.1/m3u8 links, which the step
        // above rewrote to the LAN address. The renderer now arrives over the LAN, so the URL has
        // to carry the server token or the fetch is rejected.
        if (url.contains("/m3u8?")) return url.contains("token=") ? url : ServerAuth.withToken(url);
        if (!isPlaylist(url) || headers == null || headers.isEmpty()) return url;
        String id = register(url, headers);
        if (id.isEmpty()) return url;
        String path = "/m3u8?url=" + URLEncoder.encode(url, StandardCharsets.UTF_8) + "&" + M3u8.PARAM_ID + "=" + id;
        return ServerAuth.withToken(Server.get().getAddress(false) + path);
    }

    /**
     * Compatibility mode. Only HLS sources with request headers need it; a plain direct link is
     * already a single continuous stream and must be left alone.
     */
    private static String compatUrl(String url, Map<String, String> headers) {
        if (url.contains("/m3u8?")) {
            // Already a relayed playlist. Swap the endpoint, dropping the old token so we do not
            // end up with two; withToken would append a second one.
            String base = url.substring(0, url.indexOf("/m3u8?"));
            String query = url.substring(url.indexOf("/m3u8?") + "/m3u8?".length());
            StringBuilder clean = new StringBuilder();
            for (String pair : query.split("&")) {
                // Drop both the old token (withToken appends a fresh one) and any previous
                // context id — leaving two id params makes the server pick whichever NanoHTTPD
                // happened to parse last.
                if (pair.startsWith("token=") || pair.startsWith(M3u8.PARAM_ID + "=")) continue;
                if (clean.length() > 0) clean.append('&');
                clean.append(pair);
            }
            String id = register(url, headers);
            if (!id.isEmpty()) clean.append('&').append(M3u8.PARAM_ID).append('=').append(id);
            return ServerAuth.withToken(base + TsCast.PATH + "?" + clean);
        }
        if (!isPlaylist(url)) return url;
        String id = register(url, headers);
        if (id.isEmpty()) return url;
        String path = TsCast.PATH + "?url=" + URLEncoder.encode(url, StandardCharsets.UTF_8) + "&" + M3u8.PARAM_ID + "=" + id;
        return ServerAuth.withToken(Server.get().getAddress(false) + path);
    }

    private static String register(String url, Map<String, String> headers) {
        // A public source has no headers but still needs a context, so the server can tell a
        // cast we initiated from a stray LAN request. An empty map is a valid context.
        return M3u8.registerHeaders(headers == null ? Map.of() : headers);
    }

    /**
     * Kept deliberately loose: a missed playlist means a cast that silently plays nothing, while
     * a false positive merely adds one local hop. Plenty of sources hide the format behind a
     * query, so "m3u8 mentioned anywhere" is the safer trade than a strict suffix test.
     */
    private static boolean isPlaylist(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.contains(".m3u8") || lower.contains(".m3u") || lower.contains("m3u8");
    }
}