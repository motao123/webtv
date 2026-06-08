package com.fongmi.quickjs.utils;

import android.text.TextUtils;
import android.util.LruCache;

import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Asset;

public class Module {

    private static final int MAX_SIZE = 50;
    private final LruCache<String, String> cache;

    public Module() {
        cache = new LruCache<>(MAX_SIZE);
    }

    public static Module get() {
        return Loader.INSTANCE;
    }

    public String fetch(String name) {
        String content = cache.get(name);
        if (!TextUtils.isEmpty(content)) return content;
        if (name.startsWith("http")) cache.put(name, content = OkHttp.string(name));
        else if (name.startsWith("assets")) cache.put(name, content = Asset.read(name));
        else if (name.startsWith("lib/")) cache.put(name, content = Asset.read("js/" + name));
        return content;
    }

    public void clear() {
        cache.evictAll();
    }

    private static class Loader {
        static volatile Module INSTANCE = new Module();
    }
}
