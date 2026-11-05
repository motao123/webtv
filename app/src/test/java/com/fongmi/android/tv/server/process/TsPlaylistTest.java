package com.fongmi.android.tv.server.process;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TsPlaylistTest {

    private static final String BASE = "https://cdn.example.com/hls/movie/index.m3u8";

    @Test
    public void vodPlaylistWithTsSegmentsIsAcceptedInOrder() {
        String playlist = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-TARGETDURATION:10
                #EXT-X-MEDIA-SEQUENCE:0
                #EXTINF:10.0,
                seg0.ts
                #EXTINF:10.0,
                seg1.ts
                #EXTINF:10.0,
                https://other.example.com/abs/seg2.ts
                #EXT-X-ENDLIST
                """;

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        assertEquals(TsPlaylist.Kind.VOD, parsed.kind);
        assertEquals(3, parsed.segments.size());
        assertEquals("https://cdn.example.com/hls/movie/seg0.ts", parsed.segments.get(0));
        assertEquals("https://cdn.example.com/hls/movie/seg1.ts", parsed.segments.get(1));
        assertEquals("https://other.example.com/abs/seg2.ts", parsed.segments.get(2));
        assertTrue(parsed.variant.isEmpty());
        assertTrue(parsed.reason.isEmpty());
    }

    @Test
    public void missingEndListIsTreatedAsLiveAndRejected() {
        String playlist = """
                #EXTM3U
                #EXT-X-TARGETDURATION:10
                #EXTINF:10.0,
                seg0.ts
                #EXTINF:10.0,
                seg1.ts
                """;

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        // A rolling playlist cannot be concatenated from one snapshot; the caller refuses it
        // with an explicit message rather than ending the stream mid-film.
        assertEquals(TsPlaylist.Kind.LIVE, parsed.kind);
    }

    @Test
    public void masterPlaylistReturnsFirstVariantToFollow() {
        String playlist = """
                #EXTM3U
                #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=1280x720
                720/index.m3u8
                #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1920x1080
                1080/index.m3u8
                """;

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        assertEquals(TsPlaylist.Kind.UNSUPPORTED, parsed.kind);
        assertEquals("https://cdn.example.com/hls/movie/720/index.m3u8", parsed.variant);
        assertTrue(parsed.reason.contains("Master playlist"));
    }

    @Test
    public void encryptedSegmentsAreRejectedWithReason() {
        String playlist = """
                #EXTM3U
                #EXT-X-KEY:METHOD=AES-128,URI="key.key"
                #EXTINF:10.0,
                seg0.ts
                #EXT-X-ENDLIST
                """;

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        assertEquals(TsPlaylist.Kind.UNSUPPORTED, parsed.kind);
        assertTrue(parsed.reason.contains("AES-128"));
    }

    @Test
    public void fmp4SegmentsAreRejectedRatherThanSilentlyFailing() {
        String playlist = """
                #EXTM3U
                #EXT-X-MAP:URI="init.mp4"
                #EXTINF:4.0,
                seg0.m4s
                #EXT-X-ENDLIST
                """;

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        assertEquals(TsPlaylist.Kind.UNSUPPORTED, parsed.kind);
        assertTrue(parsed.reason.contains("fMP4"));
    }

    @Test
    public void nonTsExtensionIsRejected() {
        String playlist = """
                #EXTM3U
                #EXTINF:4.0,
                seg0.m4s
                #EXT-X-ENDLIST
                """;

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        assertEquals(TsPlaylist.Kind.UNSUPPORTED, parsed.kind);
        assertTrue(parsed.reason.contains("Non-TS"));
    }

    @Test
    public void discontinuityIsRejected() {
        String playlist = """
                #EXTM3U
                #EXTINF:10.0,
                seg0.ts
                #EXT-X-DISCONTINUITY
                #EXTINF:10.0,
                seg1.ts
                #EXT-X-ENDLIST
                """;

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        assertEquals(TsPlaylist.Kind.UNSUPPORTED, parsed.kind);
        assertTrue(parsed.reason.contains("Discontinuous"));
    }

    @Test
    public void crlfAndQueryStringsAreHandled() {
        String playlist = "#EXTM3U\r\n#EXTINF:10.0,\r\nseg0.ts?token=abc\r\n#EXT-X-ENDLIST\r\n";

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        assertEquals(TsPlaylist.Kind.VOD, parsed.kind);
        assertEquals(1, parsed.segments.size());
        assertTrue(parsed.segments.get(0).contains("token=abc"));
    }

    @Test
    public void nonPlaylistInputIsRejected() {
        TsPlaylist.Parsed parsed = TsPlaylist.parse("<html>not a playlist</html>", BASE);

        assertEquals(TsPlaylist.Kind.UNSUPPORTED, parsed.kind);
        assertFalse(parsed.reason.isEmpty());
    }

    @Test
    public void relativeUrisResolveAgainstPlaylistUrl() {
        assertEquals("https://cdn.example.com/hls/movie/a.ts", TsPlaylist.resolve(BASE, "a.ts"));
        assertEquals("https://cdn.example.com/a.ts", TsPlaylist.resolve(BASE, "/a.ts"));
        assertEquals("https://other.example.com/a.ts", TsPlaylist.resolve(BASE, "https://other.example.com/a.ts"));
    }
}