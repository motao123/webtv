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
import java.util.concurrent.ConcurrentHashMap;

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
 * AES-128 encrypted segments are decrypted on the fly, because the relay endpoints this app casts
 * through encrypt their playlists and the sources would otherwise be uncancellable in bulk.
 * Sources that cannot be concatenated byte-for-byte (fMP4, discontinuities) are rejected with a
 * plain-text reason instead of producing a silent black screen.</p>
 *
 * <p>The stream is lazy: segments are fetched only as the renderer reads, so start-up is one
 * segment rather than the whole video, and nothing is written to disk.</p>
 */
public class TsCast implements Process {

    public static final String PATH = "/cast.ts";
    public static final String MIME_TS = "video/MP2T";

    private static final String TAG = "ts-cast";
    private static final int MAX_REDIRECTS = 5;
    /** HLS sync byte. Verified instead of trusting URL suffixes: relays serve extension-less segments. */
    private static final int TS_SYNC = 0x47;
    private static final OkHttpClient CLIENT = OkHttp.player().newBuilder()
            .followRedirects(false).followSslRedirects(false).build();
    private static final ConcurrentHashMap<String, byte[]> KEYS = new ConcurrentHashMap<>();

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
        // Validate the first segment before answering: a source that turns out not to be MPEG-TS
        // must produce an explicit error now, not an empty response after the renderer accepted one.
        try {
            if (!stream.prime()) return Nano.error(Response.Status.BAD_REQUEST, "No playable MPEG-TS segments in playlist");
        } catch (Exception e) {
            SpiderDebug.log(TAG, e);
            return Nano.error(Response.Status.BAD_REQUEST, "First segment failed: " + message(e));
        }
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
        okhttp3.Response response = fetchFollowing(url, headers);
        try (response; ResponseBody body = response.body()) {
            if (!response.isSuccessful() || body == null) throw new IOException("Playlist HTTP " + response.code());
            return TsPlaylist.parse(body.string(), url);
        }
    }

    /**
     * Fetches a URL following redirects one hop at a time, re-checking every target with
     * {@link UrlSafety}. Used for playlists, segments and keys alike: a relay can 302 any of them
     * to a CDN node, and a client left to follow redirects on its own would bypass the guard.
     */
    private okhttp3.Response fetchFollowing(String url, Map<String, String> headers) throws IOException {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (!UrlSafety.isSafeHttpUrl(current)) throw new IOException("Unsafe url");
            Request.Builder builder = new Request.Builder().url(current);
            for (Map.Entry<String, String> entry : headers.entrySet()) builder.header(entry.getKey(), entry.getValue());
            okhttp3.Response response = CLIENT.newCall(builder.build()).execute();
            if (!isRedirect(response.code())) return response;
            String location = response.header("Location");
            if (TextUtils.isEmpty(location)) return response;
            response.close();
            if (hop == MAX_REDIRECTS) break;
            String next = java.net.URI.create(current).resolve(location).toString();
            current = next;
        }
        throw new IOException("Too many redirects");
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private byte[] key(String uri, Map<String, String> headers) throws IOException {
        byte[] cached = KEYS.get(uri);
        if (cached != null) return cached;
        okhttp3.Response response = fetchFollowing(uri, headers);
        try (response; ResponseBody body = response.body()) {
            if (!response.isSuccessful() || body == null) throw new IOException("Key HTTP " + response.code());
            byte[] bytes = body.bytes();
            if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) throw new IOException("Invalid key length " + bytes.length);
            // The cache is unbounded by design — keys are 16–32 bytes and few per cast — but a
            // runaway source should not be able to grow it forever.
            if (KEYS.size() > 256) KEYS.clear();
            KEYS.put(uri, bytes);
            return bytes;
        }
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

        private final List<TsPlaylist.Segment> segments;
        private final Map<String, String> headers;
        private int index;
        private okhttp3.Response current;
        private InputStream input;
        /** First byte of the current segment, kept back so callers can verify the sync byte. */
        private int primed = -1;
        private boolean closed;

        private SequenceStream(List<TsPlaylist.Segment> segments, Map<String, String> headers) {
            this.segments = segments;
            this.headers = headers;
        }

        /**
         * Opens the first playable segment and verifies it is really MPEG-TS. Returns false when
         * no segment qualifies; the caller then refuses the cast with an explicit error instead
         * of delivering an empty stream.
         */
        private boolean prime() throws IOException {
            while (index < segments.size()) {
                open(index++);
                if (input != null) return true;
            }
            return false;
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
            if (primed >= 0) {
                buffer[offset] = (byte) primed;
                primed = -1;
                return 1;
            }
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
            String url = segments.get(position).url;
            okhttp3.Response response = null;
            // A single dead segment should not kill a two-hour film. Retry once, then skip:
            // MPEG-TS tolerates a missing chunk far better than a truncated connection.
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    response = fetchFollowing(url, headers);
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
            try {
                input = wrap(position, response.body().byteStream());
            } catch (Exception e) {
                // A key that cannot be fetched is treated like a dead segment: skip it rather
                // than end a two-hour stream.
                response.close();
                SpiderDebug.log(TAG, "skip segment %d: %s", position, message(e));
                return;
            }
            current = response;
            // MPEG-TS packets all start with the same sync byte. Anything else here is not a
            // segment this stream can append, so skip it instead of corrupting the output.
            try {
                int first = input.read();
                if (first != TS_SYNC) {
                    SpiderDebug.log(TAG, "skip segment %d: not MPEG-TS (0x%02x)", position, first);
                    release();
                    return;
                }
                primed = first;
            } catch (IOException e) {
                release();
            }
        }

        private InputStream wrap(int position, InputStream input) throws Exception {
            TsPlaylist.Segment segment = segments.get(position);
            if (segment.keyUri == null) return input;
            byte[] key = key(segment.keyUri, headers);
            byte[] iv = segment.iv != null ? segment.iv : HlsAes.sequenceIv(segment.sequence);
            return HlsAes.decrypt(input, key, iv);
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
            primed = -1;
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
