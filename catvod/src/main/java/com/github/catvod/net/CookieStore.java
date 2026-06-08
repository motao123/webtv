package com.github.catvod.net;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

public class CookieStore {

    private static final ConcurrentHashMap<String, String> COOKIES = new ConcurrentHashMap<>();

    public static void save(Request request) {
        if (request == null) return;
        save(request.url(), request.header("Cookie"));
    }

    public static void save(Response response) {
        if (response == null) return;
        List<String> values = response.headers("Set-Cookie");
        if (values == null || values.isEmpty()) return;
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            String item = value.split(";", 2)[0].trim();
            if (item.isEmpty()) continue;
            if (builder.length() > 0) builder.append("; ");
            builder.append(item);
        }
        save(response.request().url(), builder.toString());
    }

    public static void save(HttpUrl url, String cookie) {
        if (url == null || cookie == null || cookie.trim().isEmpty()) return;
        String host = url.host().toLowerCase(Locale.ROOT);
        COOKIES.put(host, merge(COOKIES.get(host), cookie));
    }

    public static String get(String host) {
        if (host == null || host.trim().isEmpty()) return "";
        return COOKIES.getOrDefault(host.toLowerCase(Locale.ROOT), "");
    }

    public static String find(String keyword) {
        String key = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : COOKIES.entrySet()) {
            if (!entry.getKey().contains(key)) continue;
            if (builder.length() > 0) builder.append("; ");
            builder.append(entry.getValue());
        }
        return merge(builder.toString());
    }

    private static String merge(String... cookies) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String cookie : cookies) {
            if (cookie == null || cookie.trim().isEmpty()) continue;
            for (String part : cookie.split(";")) {
                String item = part.trim();
                if (item.isEmpty() || !item.contains("=")) continue;
                String[] pair = item.split("=", 2);
                values.put(pair[0].trim(), pair[1].trim());
            }
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (builder.length() > 0) builder.append("; ");
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }
}
