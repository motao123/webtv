package com.fongmi.android.tv.server;

import android.text.TextUtils;

import com.github.catvod.utils.Prefers;

import java.security.MessageDigest;
import java.security.SecureRandom;

import fi.iki.elonen.NanoHTTPD;

public class ServerAuth {

    private static final String HEADER = "x-fongmi-token";
    private static final String BEARER = "Bearer ";
    private static volatile String TOKEN = token();

    public static String tokenValue() {
        return TOKEN;
    }

    public static String tokenPreview() {
        return TOKEN.substring(0, Math.min(4, TOKEN.length())) + "****";
    }

    public static void resetToken() {
        TOKEN = token();
    }

    public static String ipMode() {
        return Prefers.getString("server_ip_allow", "all");
    }

    public static void setIpMode(String mode) {
        Prefers.put("server_ip_allow", TextUtils.isEmpty(mode) ? "all" : mode);
    }

    // Note: token in query string may leak via Referer headers and logs.
    // Prefer Authorization: Bearer header for API calls where possible.
    // This method exists because QR-code / link-based access requires URL-embedded auth.
    public static String withToken(String url) {
        return url + (url.contains("?") ? "&" : "?") + "token=" + TOKEN;
    }

    public static boolean isLocal(NanoHTTPD.IHTTPSession session) {
        return isLocalIp(remoteIp(session));
    }

    public static boolean isLocalOrLan(NanoHTTPD.IHTTPSession session) {
        String ip = remoteIp(session);
        return isLocalIp(ip) || isLanIp(ip);
    }

    private static boolean isLocalIp(String ip) {
        if (TextUtils.isEmpty(ip)) return false;
        return "127.0.0.1".equals(ip) || "::1".equals(ip) || "localhost".equalsIgnoreCase(ip);
    }

    private static boolean isLanIp(String ip) {
        if (TextUtils.isEmpty(ip)) return false;
        // 169.254.0.0/16 is link-local (cloud metadata on some platforms) and must not count as LAN.
        return ip.startsWith("10.") || ip.startsWith("192.168.") || ip.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }

    public static boolean allow(NanoHTTPD.IHTTPSession session, String url) {
        if (!allowIp(session)) return false;
        if (!protectedPath(url)) return true;
        if (isSensitivePath(url)) return hasToken(session);
        if (isLocal(session) && isLoopbackHost(session)) return true;
        return hasToken(session);
    }

    private static boolean allowIp(NanoHTTPD.IHTTPSession session) {
        String mode = ipMode();
        if ("all".equals(mode)) return true;
        String ip = remoteIp(session);
        if (isLocalIp(ip)) return true;
        return "lan".equals(mode) && isLanIp(ip);
    }

    private static boolean hasToken(NanoHTTPD.IHTTPSession session) {
        return equalsToken(TOKEN, session.getParms().get("token")) || equalsToken(TOKEN, session.getHeaders().get(HEADER)) || bearer(session);
    }

    // Constant-time comparison so response timing cannot be used to recover the token byte by byte.
    private static boolean equalsToken(String expected, String provided) {
        if (expected == null || provided == null) return false;
        return MessageDigest.isEqual(expected.getBytes(), provided.getBytes());
    }

    private static boolean protectedPath(String url) {
        return url.startsWith("/manage/") || url.startsWith("/file") || url.startsWith("/upload") || url.startsWith("/newFolder") || url.startsWith("/delFolder") || url.startsWith("/delFile") || url.startsWith("/debug/") || url.startsWith("/cache") || url.startsWith("/action") || url.startsWith("/proxy") || url.startsWith("/webResource") || url.startsWith("/pan/check") || url.startsWith("/parse") || url.startsWith("/media") || url.startsWith("/tvbus") || url.startsWith("/device") || url.startsWith("/api/playback") || url.startsWith("/playback") || url.startsWith("/m3u8") || url.startsWith("/cast.ts") || url.startsWith("/image/");
    }

    /**
     * State-changing or sensitive paths that must carry the server token even from loopback,
     * so a same-device malicious app/page cannot silently mutate state without the token.
     */
    private static boolean isSensitivePath(String url) {
        return url.startsWith("/manage/") || url.startsWith("/file") || url.startsWith("/upload") || url.startsWith("/newFolder") || url.startsWith("/delFolder") || url.startsWith("/delFile") || url.startsWith("/debug/") || url.startsWith("/cache") || url.startsWith("/action") || url.startsWith("/pan/check") || url.startsWith("/api/playback") || url.startsWith("/playback");
    }

    /**
     * Guards against DNS-rebinding: a loopback request is only trusted when its Host header
     * explicitly names the loopback interface, never an attacker-controlled hostname.
     */
    private static boolean isLoopbackHost(NanoHTTPD.IHTTPSession session) {
        String host = session.getHeaders().get("host");
        if (TextUtils.isEmpty(host)) return false;
        String name = host.trim();
        int colon = name.lastIndexOf(':');
        if (colon > 0) name = name.substring(0, colon);
        if (name.startsWith("[") && name.endsWith("]")) name = name.substring(1, name.length() - 1);
        return isLocalIp(name);
    }

    private static boolean bearer(NanoHTTPD.IHTTPSession session) {
        String auth = session.getHeaders().get("authorization");
        return auth != null && auth.startsWith(BEARER) && TOKEN.equals(auth.substring(BEARER.length()));
    }

    private static String remoteIp(NanoHTTPD.IHTTPSession session) {
        return session.getHeaders().get("remote-addr");
    }

    private static String token() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) builder.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        return builder.toString();
    }
}
