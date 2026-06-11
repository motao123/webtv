package com.fongmi.android.tv.utils;

import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;

import org.json.JSONObject;

public class Github {

    private static final String GITHUB = "https://github.com/motao123/webtv/releases/latest/download";
    private static final String CNB = "https://cnb.cool/code_free/webtv/-/git/raw/main";
    private static final String GITEE = "https://gitee.com/pigfei/webtv/raw/main";
    private static final String GEO_URL = "https://ip-api.com/json?fields=countryCode";
    private static final String PREF_MIRROR = "update_mirror";

    private static volatile String baseUrl;

    public static String getUrl() {
        if (baseUrl == null) baseUrl = resolveBase();
        return baseUrl;
    }

    public static void setMirror(String mirror) {
        Prefers.put(PREF_MIRROR, mirror);
        if ("github".equals(mirror)) baseUrl = GITHUB;
        else if ("cnb".equals(mirror)) baseUrl = CNB;
        else if ("gitee".equals(mirror)) baseUrl = GITEE;
        else baseUrl = null;
    }

    public static String getMirror() {
        return Prefers.getString(PREF_MIRROR, "auto");
    }

    private static String resolveBase() {
        String mirror = getMirror();
        if ("github".equals(mirror)) return GITHUB;
        if ("cnb".equals(mirror)) return CNB;
        if (isChina()) return CNB;
        return GITHUB;
    }

    private static boolean isChina() {
        String cached = Prefers.getString("geo_country");
        if (cached != null && !cached.isEmpty()) return "CN".equals(cached);
        if (isZhLocale()) {
            Prefers.put("geo_country", "CN");
            return true;
        }
        try {
            String json = OkHttp.string(GEO_URL);
            String code = new JSONObject(json).optString("countryCode", "");
            Prefers.put("geo_country", code);
            SpiderDebug.log("Github", "geo: " + code + " -> " + ("CN".equals(code) ? "cnb.cool" : "github"));
            return "CN".equals(code);
        } catch (Exception e) {
            SpiderDebug.log(e);
            return false;
        }
    }

    private static boolean isZhLocale() {
        java.util.Locale locale = java.util.Locale.getDefault();
        return "CN".equalsIgnoreCase(locale.getCountry()) || "zh".equalsIgnoreCase(locale.getLanguage());
    }

    private static String getUrl(String name) {
        String base = getUrl();
        return base + (base.contains("/releases/") ? "/" : "/apk/") + name;
    }

    public static String getJson(String name) {
        return getUrl(name + ".json");
    }

    public static String getApk(String name) {
        return getUrl(name + ".apk");
    }

}
