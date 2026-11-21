package com.fongmi.android.tv.server.process;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
        assertEquals("https://cdn.example.com/hls/movie/seg0.ts", parsed.segments.get(0).url);
        assertEquals("https://cdn.example.com/hls/movie/seg1.ts", parsed.segments.get(1).url);
        assertEquals("https://other.example.com/abs/seg2.ts", parsed.segments.get(2).url);
        for (int i = 0; i < parsed.segments.size(); i++) {
            assertNull(parsed.segments.get(i).keyUri);
            assertEquals("segment " + i + " sequence", i, parsed.segments.get(i).sequence);
        }
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
    public void aes128SegmentsAreAcceptedWithResolvedKeyUriAndSequence() {
        String playlist = """
                #EXTM3U
                #EXT-X-MEDIA-SEQUENCE:7
                #EXT-X-KEY:METHOD=AES-128,URI="key.key"
                #EXTINF:10.0,
                seg0.ts
                #EXTINF:10.0,
                seg1.ts
                #EXT-X-ENDLIST
                """;

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        // The relay endpoints users cast through encrypt segments as an anti-hotlink measure;
        // the phone decrypts them instead of refusing the source outright.
        assertEquals(TsPlaylist.Kind.VOD, parsed.kind);
        assertEquals(2, parsed.segments.size());
        assertEquals("https://cdn.example.com/hls/movie/key.key", parsed.segments.get(0).keyUri);
        assertEquals("https://cdn.example.com/hls/movie/key.key", parsed.segments.get(1).keyUri);
        assertEquals(7, parsed.segments.get(0).sequence);
        assertEquals(8, parsed.segments.get(1).sequence);
        assertNull(parsed.segments.get(0).iv);
    }

    @Test
    public void explicitKeyIvIsKeptOnEveryFollowingSegment() {
        String playlist = """
                #EXTM3U
                #EXT-X-KEY:METHOD=AES-128,URI="k.key",IV=0x1234567890abcdef1234567890abcdef
                #EXTINF:10.0,
                a.ts
                #EXT-X-ENDLIST
                """;

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        TsPlaylist.Segment segment = parsed.segments.get(0);
        assertNotNull(segment.iv);
        assertEquals(16, segment.iv.length);
        assertEquals((byte) 0x12, segment.iv[0]);
        assertEquals((byte) 0xef, segment.iv[15]);
    }

    @Test
    public void methodNoneStopsEncryptionForLaterSegments() {
        String playlist = """
                #EXTM3U
                #EXT-X-KEY:METHOD=AES-128,URI="k1.key"
                #EXTINF:10.0,
                a.ts
                #EXT-X-KEY:METHOD=NONE
                #EXTINF:10.0,
                b.ts
                #EXT-X-ENDLIST
                """;

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        assertEquals(TsPlaylist.Kind.VOD, parsed.kind);
        assertEquals("https://cdn.example.com/hls/movie/k1.key", parsed.segments.get(0).keyUri);
        assertNull(parsed.segments.get(1).keyUri);
    }

    @Test
    public void aNewKeyTagSwitchesTheKeyForLaterSegments() {
        String playlist = """
                #EXTM3U
                #EXT-X-KEY:METHOD=AES-128,URI="k1.key",IV=0x00000000000000000000000000000001
                #EXTINF:10.0,
                a.ts
                #EXT-X-KEY:METHOD=AES-128,URI="k2.key"
                #EXTINF:10.0,
                b.ts
                #EXT-X-ENDLIST
                """;

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        assertEquals("https://cdn.example.com/hls/movie/k1.key", parsed.segments.get(0).keyUri);
        assertEquals("https://cdn.example.com/hls/movie/k2.key", parsed.segments.get(1).keyUri);
        assertNotNull(parsed.segments.get(0).iv);
        assertNull(parsed.segments.get(1).iv);
    }

    @Test
    public void shortIvLiteralIsLeftPaddedToSixteenBytes() {
        String playlist = """
                #EXTM3U
                #EXT-X-KEY:METHOD=AES-128,URI="k.key",IV=0x1
                #EXTINF:10.0,
                a.ts
                #EXT-X-ENDLIST
                """;

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        byte[] expected = new byte[16];
        expected[15] = 0x01;
        assertArrayEquals(expected, parsed.segments.get(0).iv);
    }

    @Test
    public void sampleAesIsRejected() {
        String playlist = """
                #EXTM3U
                #EXT-X-KEY:METHOD=SAMPLE-AES,URI="skd://key"
                #EXTINF:10.0,
                a.ts
                #EXT-X-ENDLIST
                """;

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        assertEquals(TsPlaylist.Kind.UNSUPPORTED, parsed.kind);
        assertTrue(parsed.reason.contains("SAMPLE-AES"));
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
    public void extensionLessSegmentsAreAcceptedAndLeftToTheSyncByteCheck() {
        String playlist = """
                #EXTM3U
                #EXTINF:4.0,
                https://relay.example.com/getSeg?key=abc
                #EXT-X-ENDLIST
                """;

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        // Relay endpoints serve segments from extension-less URLs; the stream validates the
        // 0x47 sync byte instead of trusting the name.
        assertEquals(TsPlaylist.Kind.VOD, parsed.kind);
        assertEquals(1, parsed.segments.size());
    }

    @Test
    public void byterangeIsRejected() {
        String playlist = """
                #EXTM3U
                #EXTINF:10.0,
                #EXT-X-BYTERANGE:5000@0
                movie.ts
                #EXT-X-ENDLIST
                """;

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        assertEquals(TsPlaylist.Kind.UNSUPPORTED, parsed.kind);
        assertTrue(parsed.reason.contains("Byte-range"));
    }

    @Test
    public void discontinuitySequenceAloneIsNotADiscontinuity() {
        String playlist = """
                #EXTM3U
                #EXT-X-DISCONTINUITY-SEQUENCE:4
                #EXTINF:10.0,
                a.ts
                #EXT-X-ENDLIST
                """;

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        assertEquals(TsPlaylist.Kind.VOD, parsed.kind);
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
        assertTrue(parsed.segments.get(0).url.contains("token=abc"));
    }

    @Test
    public void nonPlaylistInputIsRejected() {
        TsPlaylist.Parsed parsed = TsPlaylist.parse("<html>not a playlist</html>", BASE);

        assertEquals(TsPlaylist.Kind.UNSUPPORTED, parsed.kind);
        assertFalse(parsed.reason.isEmpty());
    }

    @Test
    public void byteOrderMarkAndNullInputAreHandled() {
        String playlist = "\uFEFF#EXTM3U\n#EXTINF:10.0,\nseg0.ts\n#EXT-X-ENDLIST\n";

        TsPlaylist.Parsed parsed = TsPlaylist.parse(playlist, BASE);

        assertEquals(TsPlaylist.Kind.VOD, parsed.kind);
        assertEquals(1, parsed.segments.size());
        assertEquals(TsPlaylist.Kind.UNSUPPORTED, TsPlaylist.parse(null, BASE).kind);
    }

    @Test
    public void looksLikePlaylistIgnoresMimeAndTrustsOnlyTheContent() {
        // The relay endpoints users cast through answer text/plain; the body decides.
        assertTrue(TsPlaylist.looksLikePlaylist("#EXTM3U\n#EXTINF:10.0,\nseg0.ts\n"));
        assertTrue(TsPlaylist.looksLikePlaylist("\uFEFF#EXTM3U\n"));
        assertTrue(TsPlaylist.looksLikePlaylist("  \n #EXTM3U ..."));
        assertFalse(TsPlaylist.looksLikePlaylist("<pre>链接失效了</pre>"));
        assertFalse(TsPlaylist.looksLikePlaylist(""));
        assertFalse(TsPlaylist.looksLikePlaylist(null));
    }

    @Test
    public void relativeUrisResolveAgainstPlaylistUrl() {
        assertEquals("https://cdn.example.com/hls/movie/a.ts", TsPlaylist.resolve(BASE, "a.ts"));
        assertEquals("https://cdn.example.com/a.ts", TsPlaylist.resolve(BASE, "/a.ts"));
        assertEquals("https://other.example.com/a.ts", TsPlaylist.resolve(BASE, "https://other.example.com/a.ts"));
    }

    @Test
    public void sequenceIvEncodesTheSequenceNumberBigEndian() {
        byte[] zero = HlsAes.sequenceIv(0);
        byte[] one = HlsAes.sequenceIv(1);
        byte[] large = HlsAes.sequenceIv(0x0102_0304_0506_0708L);

        assertEquals(16, zero.length);
        assertEquals(1, one[15]);
        for (int i = 0; i < 15; i++) assertEquals(0, one[i]);
        // A 64-bit value occupies the tail of the 128-bit big-endian IV; the head stays zero.
        assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8}, large);
    }

    @Test
    public void aesRoundTripDecryptsTheSegmentStream() throws Exception {
        // javax.crypto behaves identically on the JVM and on Android, so a round trip here pins
        // down the exact padding and IV semantics used at stream time.
        byte[] key = new byte[16];
        for (int i = 0; i < key.length; i++) key[i] = (byte) i;
        byte[] iv = HlsAes.hexIv("0x0102030405060708090a0b0c0d0e0f10");

        byte[] plaintext = new byte[5000]; // not a multiple of 16, so PKCS7 padding applies
        Arrays.fill(plaintext, (byte) 0x47); // a run of TS sync bytes
        javax.crypto.Cipher encryptor = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding");
        encryptor.init(javax.crypto.Cipher.ENCRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"), new javax.crypto.spec.IvParameterSpec(iv));
        byte[] ciphertext = encryptor.doFinal(plaintext);

        java.io.InputStream decrypted = HlsAes.decrypt(new java.io.ByteArrayInputStream(ciphertext), key, iv);
        byte[] result = decrypted.readAllBytes();
        decrypted.close();

        assertArrayEquals(plaintext, result);
    }
}
