package com.fongmi.android.tv.server.process;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses just enough HLS to concatenate segments into one continuous MPEG-TS stream.
 *
 * <p>This is deliberately not a general HLS implementation. It exists for DLNA renderers that
 * cannot read a playlist at all: MPEG-TS is a sequence of self-contained 188-byte packets with
 * no global header, so appending segments in playlist order yields a valid stream. Anything that
 * breaks that assumption (fMP4, discontinuity between encodes) is rejected loudly rather than
 * producing a black screen.</p>
 *
 * <p>AES-128 segment encryption is supported, because the commercial relay endpoints this app
 * casts through encrypt their playlists as an anti-hotlink measure and the in-app player decrypts
 * them transparently — refusing them here would reject exactly the sources users try to cast.
 * Whether a segment really is MPEG-TS is decided by its 0x47 sync byte at stream time, not by the
 * URL suffix: relays commonly serve segments from extension-less endpoints.</p>
 */
final class TsPlaylist {

    private static final Pattern KEY_METHOD = Pattern.compile("METHOD=([A-Za-z0-9-]+)");
    private static final Pattern KEY_URI = Pattern.compile("URI=\"([^\"]+)\"");
    private static final Pattern KEY_IV = Pattern.compile("IV=0[xX]([0-9a-fA-F]+)");
    private static final Pattern MEDIA_SEQUENCE = Pattern.compile("#EXT-X-MEDIA-SEQUENCE:(-?\\d+)");

    enum Kind { VOD, LIVE, UNSUPPORTED }

    /** One media segment plus the decryption context that applies to it. */
    static final class Segment {
        final String url;
        /** Key URI when this segment is encrypted, null otherwise. */
        final String keyUri;
        /** Explicit IV from the #EXT-X-KEY tag, null to derive from {@link #sequence}. */
        final byte[] iv;
        /** Media sequence number of this segment, the default IV source for AES-128. */
        final long sequence;

        Segment(String url, String keyUri, byte[] iv, long sequence) {
            this.url = url;
            this.keyUri = keyUri;
            this.iv = iv;
            this.sequence = sequence;
        }
    }

    static final class Parsed {
        final Kind kind;
        final List<Segment> segments;
        final String reason;
        /** For a master playlist, the first variant URL to follow. Empty otherwise. */
        final String variant;

        Parsed(Kind kind, List<Segment> segments, String reason, String variant) {
            this.kind = kind;
            this.segments = segments;
            this.reason = reason;
            this.variant = variant;
        }
    }

    private TsPlaylist() {
    }

    /** True when a response body starts like an HLS playlist, regardless of its MIME type. */
    static boolean looksLikePlaylist(String text) {
        if (text == null) return false;
        // Strip a UTF-8 BOM: some origins emit one and it would hide the #EXTM3U header.
        String body = text.startsWith("\uFEFF") ? text.substring(1) : text;
        String trimmed = body.trim();
        return !trimmed.isEmpty() && trimmed.startsWith("#EXTM3U");
    }

    /**
     * @param baseUrl the URL the playlist was fetched from, used to resolve relative segment URIs
     */
    static Parsed parse(String text, String baseUrl) {
        // Plain Java on purpose: this class is unit-tested on the JVM, where android.jar is a
        // stub whose methods throw.
        if (!looksLikePlaylist(text)) return unsupported("Not an HLS playlist");
        String body = text.startsWith("\uFEFF") ? text.substring(1) : text;
        String[] lines = body.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);

        List<Segment> segments = new ArrayList<>();
        boolean fmp4 = false;
        boolean discontinuity = false;
        boolean endList = false;
        boolean master = false;
        boolean sampleAes = false;
        boolean byterange = false;
        String currentKey = null;
        byte[] currentIv = null;
        long sequence = 0;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (!line.startsWith("#")) {
                // Both media playlists and master playlists carry URLs as bare lines; what they
                // mean is decided by the tags, so collect first and classify afterwards.
                segments.add(new Segment(resolve(baseUrl, line), currentKey, currentIv, sequence++));
                continue;
            }
            String upper = line.toUpperCase(Locale.US);
            if (upper.startsWith("#EXT-X-STREAM-INF")) master = true;
            if (upper.startsWith("#EXT-X-KEY")) {
                Key key = parseKey(line, baseUrl);
                sampleAes |= key.sampleAes;
                currentKey = key.uri;
                currentIv = key.iv;
            }
            if (upper.startsWith("#EXT-X-MAP")) fmp4 = true;
            // The sequence variant only renumbers the timeline; a real discontinuity switches
            // encoder, which byte concatenation cannot bridge.
            if (upper.startsWith("#EXT-X-DISCONTINUITY") && !upper.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE")) discontinuity = true;
            if (upper.startsWith("#EXT-X-BYTERANGE")) byterange = true;
            if (upper.startsWith("#EXT-X-ENDLIST")) endList = true;
            if (upper.startsWith("#EXT-X-MEDIA-SEQUENCE")) sequence = parseMediaSequence(line);
        }

        if (master) {
            if (segments.isEmpty()) return unsupported("Master playlist has no variant");
            // Follow the first variant. Most renderers would pick by bandwidth, but we have no
            // renderer to negotiate with; the first entry is the conventional default.
            return new Parsed(Kind.UNSUPPORTED, List.of(), "Master playlist: follow a variant", segments.get(0).url);
        }
        if (sampleAes) return unsupported("SAMPLE-AES is not supported");
        if (fmp4) return unsupported("fMP4 segments cannot be byte-concatenated");
        if (byterange) return unsupported("Byte-range segments are not supported");
        if (discontinuity) return unsupported("Discontinuous timeline (encoder change)");
        if (segments.isEmpty()) return unsupported("No media segments in playlist");
        return new Parsed(endList ? Kind.VOD : Kind.LIVE, List.copyOf(segments), "", "");
    }

    private static final class Key {
        String uri;
        byte[] iv;
        boolean sampleAes;
    }

    private static Key parseKey(String line, String baseUrl) {
        Key result = new Key();
        Matcher method = KEY_METHOD.matcher(line);
        if (!method.find()) return result;
        String name = method.group(1).toUpperCase(Locale.US);
        if (name.startsWith("SAMPLE-AES")) result.sampleAes = true;
        if (name.equals("NONE")) return result;
        Matcher uri = KEY_URI.matcher(line);
        if (uri.find()) result.uri = resolve(baseUrl, uri.group(1));
        Matcher iv = KEY_IV.matcher(line);
        if (iv.find()) result.iv = HlsAes.hexIv(iv.group(1));
        return result;
    }

    private static long parseMediaSequence(String line) {
        Matcher matcher = MEDIA_SEQUENCE.matcher(line);
        if (!matcher.find()) return 0;
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }
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
}
