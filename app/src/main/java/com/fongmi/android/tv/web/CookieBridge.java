package com.fongmi.android.tv.web;

import android.text.TextUtils;
import android.webkit.CookieManager;

import java.util.List;

import okhttp3.Headers;
import okhttp3.HttpUrl;

public class CookieBridge {

    public static String get(String url) {
        try {
            return CookieManager.getInstance().getCookie(url);
        } catch (Throwable e) {
            return "";
        }
    }

    public static void set(String url, Headers headers) {
        List<String> cookies = headers.values("Set-Cookie");
        if (cookies.isEmpty()) return;
        CookieManager manager = CookieManager.getInstance();
        for (String cookie : cookies) manager.setCookie(url, cookie);
        manager.flush();
    }

    public static void apply(HttpUrl url, okhttp3.Request.Builder builder, boolean include, boolean hasCookie) {
        if (!include || hasCookie) return;
        String cookie = get(url.toString());
        if (!TextUtils.isEmpty(cookie)) builder.header("Cookie", cookie);
    }
}
