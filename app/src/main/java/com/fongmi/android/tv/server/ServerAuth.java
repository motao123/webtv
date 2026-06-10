package com.fongmi.android.tv.server;

import android.text.TextUtils;

import java.security.SecureRandom;

import fi.iki.elonen.NanoHTTPD;

public class ServerAuth {

    private static final String HEADER = "x-fongmi-token";
    private static final String BEARER = "Bearer ";
    private static final String TOKEN = token();

    public static String tokenValue() {
        return TOKEN;
    }

    // Note: token in query string may leak via Referer headers and logs.
    // Prefer Authorization: Bearer header for API calls where possible.
    // This method exists because QR-code / link-based access requires URL-embedded auth.
    public static String withToken(String url) {
        return url + (url.contains("?") ? "&" : "?") + "token=" + TOKEN;
    }

    public static boolean isLocal(NanoHTTPD.IHTTPSession session) {
        String ip = remoteIp(session);
        return TextUtils.isEmpty(ip) || "127.0.0.1".equals(ip) || "::1".equals(ip) || "localhost".equalsIgnoreCase(ip);
    }

    public static boolean allow(NanoHTTPD.IHTTPSession session, String url) {
        if (!protectedPath(url)) return true;
        if (isLocal(session)) return true;
        return TOKEN.equals(session.getParms().get("token")) || TOKEN.equals(session.getHeaders().get(HEADER)) || bearer(session);
    }

    private static boolean protectedPath(String url) {
        return url.startsWith("/manage/") || url.startsWith("/file") || url.startsWith("/upload") || url.startsWith("/newFolder") || url.startsWith("/delFolder") || url.startsWith("/delFile") || url.startsWith("/debug/") || url.startsWith("/cache") || url.startsWith("/action") || url.startsWith("/proxy") || url.startsWith("/webResource") || url.startsWith("/pan/check") || url.startsWith("/parse");
    }

    private static boolean bearer(NanoHTTPD.IHTTPSession session) {
        String auth = session.getHeaders().get("authorization");
        return auth != null && auth.startsWith(BEARER) && TOKEN.equals(auth.substring(BEARER.length()));
    }

    private static String remoteIp(NanoHTTPD.IHTTPSession session) {
        String ip = session.getHeaders().get("remote-addr");
        return TextUtils.isEmpty(ip) ? session.getHeaders().get("http-client-ip") : ip;
    }

    private static String token() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) builder.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        return builder.toString();
    }
}
