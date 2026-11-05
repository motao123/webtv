package com.fongmi.android.tv.server.process;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Holds the upstream request headers a cast needs, keyed by a short opaque id.
 *
 * <p>A DLNA renderer is handed a single URL and cannot attach Referer / User-Agent / Cookie,
 * while most HLS sources gate their segments on exactly those headers. Encoding them into the
 * playlist URL would work, but that URL is delivered to the television — a cookie would then
 * show up in the TV's logs and in any capture on the same LAN. So only an id travels with the
 * URL and the headers stay in this process.</p>
 *
 * <p>Casting is short-lived: entries expire and are evicted by capacity, and nothing is
 * persisted.</p>
 */
final class HeaderStore {

    private static final int MAX_ENTRIES = 16;
    private static final long TTL_MS = 12L * 60 * 60 * 1000;
    /**
     * These describe the proxy→renderer hop rather than the proxy→origin hop. Forwarding them
     * would corrupt the upstream request (a stale Range or Content-Length is the classic way to
     * get a truncated segment).
     */
    private static final String[] NEVER_FORWARD = {"host", "content-length", "transfer-encoding", "connection", "range", "accept-encoding"};

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, Stored> ENTRIES = Collections.synchronizedMap(
            new LinkedHashMap<String, Stored>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Stored> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    private HeaderStore() {
    }

    static String put(Map<String, String> headers) {
        String id = id();
        ENTRIES.put(id, Stored.of(headers));
        return id;
    }

    /**
     * Distinguishes "a valid context that legitimately has no headers" (a public source) from
     * "no such context". Some callers need a context to exist even when there is nothing to send
     * upstream — an id is also the proof that this process initiated the cast.
     */
    static boolean has(String id) {
        if (id == null || id.isEmpty()) return false;
        Stored stored = ENTRIES.get(id);
        if (stored == null) return false;
        if (stored.expired()) {
            ENTRIES.remove(id);
            return false;
        }
        return true;
    }

    static Map<String, String> get(String id) {
        if (!has(id)) return Map.of();
        return ENTRIES.get(id).headers;
    }

    private static String id() {
        byte[] bytes = new byte[9];
        RANDOM.nextBytes(bytes);
        StringBuilder out = new StringBuilder(18);
        for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b));
        return out.toString();
    }

    private static final class Stored {

        private final Map<String, String> headers;
        private final long createdAt;

        private Stored(Map<String, String> headers, long createdAt) {
            this.headers = headers;
            this.createdAt = createdAt;
        }

        private static Stored of(Map<String, String> source) {
            Map<String, String> clean = new LinkedHashMap<>();
            if (source != null) {
                for (Map.Entry<String, String> entry : source.entrySet()) {
                    String name = entry.getKey();
                    String value = entry.getValue();
                    if (name == null || value == null || name.isEmpty() || value.isEmpty()) continue;
                    if (neverForward(name)) continue;
                    clean.put(name, value);
                }
            }
            return new Stored(Collections.unmodifiableMap(clean), System.currentTimeMillis());
        }

        private boolean expired() {
            return System.currentTimeMillis() - createdAt > TTL_MS;
        }

        private static boolean neverForward(String name) {
            String lower = name.toLowerCase(Locale.US);
            for (String skip : NEVER_FORWARD) if (skip.equals(lower)) return true;
            return false;
        }
    }
}