package com.fongmi.android.tv.server.process;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses just enough HLS to concatenate segments into one continuous MPEG-TS stream.
 *
 * <p>This is deliberately not a general HLS implementation. It exists for DLNA renderers that
 * cannot read a playlist at all: MPEG-TS is a sequence of self-contained 188-byte packets with
 * no global header, so appending segments in playlist order yields a valid stream. Anything that
 * breaks that assumption (fMP4, encrypted segments, discontinuity between encodes) is rejected
 * loudly rather than producing a black screen.</p>
 */
final class TsPlaylist {

    enum Kind { VOD, LIVE, UNSUPPORTED }

    static final class Parsed {
        final Kind kind;
        final List<String> segments;
        final String reason;
        /** For a master playlist, the first variant URL to follow. Empty otherwise. */
        final String variant;

        Parsed(Kind kind, List<String> segments, String reason, String variant) {
            this.kind = kind;
            this.segments = segments;
            this.reason = reason;
            this.variant = variant;
        }
    }

    private TsPlaylist() {
    }

    /**
     * @param baseUrl the URL the playlist was fetched from, used to resolve relative segment URIs
     */
    static Parsed parse(String text, String baseUrl) {
        // Plain Java on purpose: this class is unit-tested on the JVM, where android.jar is a
        // stub whose methods throw. A UTF-8 BOM is stripped because some origins emit one and
        // it would otherwise hide the #EXTM3U header on the very first line.
        if (text == null) return unsupported("Not an HLS playlist");
        String body = text.startsWith("\uFEFF") ? text.substring(1) : text;
        if (body.trim().isEmpty() || !body.trim().startsWith("#EXTM3U")) {
            return unsupported("Not an HLS playlist");
        }
        String[] lines = body.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        List<String> entries = new ArrayList<>();
        boolean encrypted = false;
        boolean fmp4 = false;
        boolean discontinuity = false;
        boolean endList = false;
        boolean master = false;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (!line.startsWith("#")) {
                // Both media playlists and master playlists carry URLs as bare lines; what they
                // mean is decided by the tags, so collect first and classify afterwards.
                entries.add(resolve(baseUrl, line));
                continue;
            }
            String upper = line.toUpperCase(Locale.US);
            if (upper.startsWith("#EXT-X-STREAM-INF")) master = true;
            if (upper.startsWith("#EXT-X-KEY") && !upper.contains("METHOD=NONE")) encrypted = true;
            if (upper.startsWith("#EXT-X-MAP")) fmp4 = true;
            if (upper.startsWith("#EXT-X-DISCONTINUITY")) discontinuity = true;
            if (upper.startsWith("#EXT-X-ENDLIST")) endList = true;
        }

        if (master) {
            if (entries.isEmpty()) return unsupported("Master playlist has no variant");
            // Follow the first variant. Most renderers would pick by bandwidth, but we have no
            // renderer to negotiate with; the first entry is the conventional default.
            return new Parsed(Kind.UNSUPPORTED, List.of(), "Master playlist: follow a variant", entries.get(0));
        }
        if (encrypted) return unsupported("AES-128 encrypted segments need decryption");
        if (fmp4) return unsupported("fMP4 segments cannot be byte-concatenated");
        if (discontinuity) return unsupported("Discontinuous timeline (encoder change)");
        if (entries.isEmpty()) return unsupported("No media segments in playlist");
        for (String segment : entries) {
            if (!looksTs(segment)) return unsupported("Non-TS segment: " + shortUrl(segment));
        }
        return new Parsed(endList ? Kind.VOD : Kind.LIVE, List.copyOf(entries), "", "");
    }

    private static boolean looksTs(String url) {
        String lower = url.toLowerCase(Locale.US);
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        return lower.endsWith(".ts") || lower.endsWith(".mpegts") || lower.endsWith(".m2ts");
    }

    static String resolve(String base, String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        try {
            return URI.create(base).resolve(value).toString();
        } catch (Exception e) {
            int cut = base.lastIndexOf('/');
            return cut < 0 ? value : base.substring(0, cut + 1) + value;
        }
    }

    private static Parsed unsupported(String reason) {
        return new Parsed(Kind.UNSUPPORTED, List.of(), reason, "");
    }

    private static String shortUrl(String url) {
        return url.length() <= 80 ? url : url.substring(0, 80) + "...";
    }
}