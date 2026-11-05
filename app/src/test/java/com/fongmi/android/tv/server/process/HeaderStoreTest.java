package com.fongmi.android.tv.server.process;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class HeaderStoreTest {

    @Test
    public void storedHeadersComeBackVerbatim() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Referer", "https://example.com/watch");
        headers.put("User-Agent", "Mozilla/5.0 test");
        headers.put("Origin", "https://example.com");

        String id = HeaderStore.put(headers);
        Map<String, String> restored = HeaderStore.get(id);

        assertEquals(3, restored.size());
        assertEquals("https://example.com/watch", restored.get("Referer"));
        assertEquals("Mozilla/5.0 test", restored.get("User-Agent"));
        assertEquals("https://example.com", restored.get("Origin"));
    }

    @Test
    public void hopByHopHeadersAreDropped() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Referer", "https://example.com");
        headers.put("Host", "wrong.example");
        headers.put("Content-Length", "1234");
        headers.put("Transfer-Encoding", "chunked");
        headers.put("Connection", "keep-alive");
        headers.put("Range", "bytes=0-99");
        headers.put("Accept-Encoding", "gzip");

        Map<String, String> restored = HeaderStore.get(HeaderStore.put(headers));

        // A forwarded Range or Content-Length would describe the renderer's request, not ours,
        // and truncating a segment is exactly the failure it causes.
        assertEquals(1, restored.size());
        assertEquals("https://example.com", restored.get("Referer"));
        assertFalse(restored.containsKey("Host"));
        assertFalse(restored.containsKey("Content-Length"));
        assertFalse(restored.containsKey("Range"));
    }

    @Test
    public void headerNamesAreDroppedCaseInsensitively() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("host", "wrong.example");
        headers.put("CONTENT-LENGTH", "1");
        headers.put("Range", "bytes=0-1");

        assertTrue(HeaderStore.get(HeaderStore.put(headers)).isEmpty());
    }

    @Test
    public void anEmptyContextIsStillAValidContext() {
        // A public HLS source needs no headers, but the cast still needs an id to prove to the
        // server that this process initiated the request. So an empty map is a valid context;
        // the server sends no upstream headers instead of falling back to unrelated defaults.
        String publicId = HeaderStore.put(Map.of());
        assertEquals(18, publicId.length());
        assertTrue(HeaderStore.has(publicId));
        assertTrue(HeaderStore.get(publicId).isEmpty());

        String nullId = HeaderStore.put(null);
        assertTrue(HeaderStore.has(nullId));
        assertTrue(HeaderStore.get(nullId).isEmpty());

        Map<String, String> blanks = new LinkedHashMap<>();
        blanks.put("Referer", "");
        blanks.put("User-Agent", "");
        assertTrue(HeaderStore.get(HeaderStore.put(blanks)).isEmpty());
    }

    @Test
    public void unknownAndNullIdsResolveToNothing() {
        assertTrue(HeaderStore.get(null).isEmpty());
        assertTrue(HeaderStore.get("").isEmpty());
        assertTrue(HeaderStore.get("does-not-exist").isEmpty());
        assertFalse(HeaderStore.has(null));
        assertFalse(HeaderStore.has(""));
        assertFalse(HeaderStore.has("does-not-exist"));
    }

    @Test
    public void idsAreUnpredictableAndDistinct() {
        Map<String, String> headers = Map.of("Referer", "https://example.com");

        String first = HeaderStore.put(headers);
        String second = HeaderStore.put(headers);

        // Same headers, different handle: a fixed id would let a peer that guessed one id read
        // another session's context.
        assertNotEquals(first, second);
        assertEquals(18, first.length());
        assertTrue(first.matches("[0-9a-f]{18}"));
    }

    @Test
    public void capacityEvictsTheOldestEntry() {
        String oldest = HeaderStore.put(Map.of("Referer", "https://oldest.example"));
        for (int i = 0; i < 20; i++) {
            HeaderStore.put(Map.of("Referer", "https://filler-" + i + ".example"));
        }

        // Casting is short-lived; unbounded growth would just leak whatever we were told to send.
        assertTrue(HeaderStore.get(oldest).isEmpty());
    }
}