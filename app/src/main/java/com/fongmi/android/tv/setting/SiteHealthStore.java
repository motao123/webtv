package com.fongmi.android.tv.setting;

import android.graphics.Color;
import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SiteHealthStore {

    private static final String KEY = "site_health";
    private static final long SAVE_DELAY = 2000;
    private static final long RETAIN_MS = TimeUnit.DAYS.toMillis(90);
    private static final int COLOR_GOOD = Color.parseColor("#0B8043");
    private static final int COLOR_WARN = Color.parseColor("#FFD54F");
    private static final int COLOR_BAD = Color.parseColor("#FF5252");
    private static final int COLOR_UNKNOWN = Color.parseColor("#80FFFFFF");
    private static final Type TYPE = new TypeToken<Map<String, Health>>() {}.getType();
    private static final Runnable SAVE = () -> Task.execute(SiteHealthStore::saveNow);

    private static final Map<String, Health> items = new LinkedHashMap<>();
    private static boolean loaded;
    private static boolean dirty;

    public static void recordSearch(Site site, boolean success, int count, long cost, String error) {
        if (skip(site)) return;
        synchronized (SiteHealthStore.class) {
            Health health = get(site.getKey());
            health.updatedAt = System.currentTimeMillis();
            health.lastSearchCost = Math.max(0, cost);
            health.lastSearchCount = Math.max(0, count);
            if (success) {
                health.searchSuccess++;
                health.lastSearchSuccessAt = health.updatedAt;
            } else {
                health.searchFail++;
                health.lastSearchFailAt = health.updatedAt;
                health.lastSearchError = trim(error);
            }
            markDirty();
        }
    }

    public static void recordDetail(String key, boolean success, long cost, String error) {
        if (skip(key)) return;
        synchronized (SiteHealthStore.class) {
            Health health = get(key);
            health.updatedAt = System.currentTimeMillis();
            health.lastDetailCost = Math.max(0, cost);
            if (success) {
                health.detailSuccess++;
                health.lastDetailSuccessAt = health.updatedAt;
            } else {
                health.detailFail++;
                health.lastDetailFailAt = health.updatedAt;
                health.lastDetailError = trim(error);
            }
            markDirty();
        }
    }

    public static void recordPlay(String key, boolean success, String error) {
        if (skip(key)) return;
        synchronized (SiteHealthStore.class) {
            Health health = get(key);
            health.updatedAt = System.currentTimeMillis();
            if (success) {
                health.playSuccess++;
                health.lastPlaySuccessAt = health.updatedAt;
            } else {
                health.playFail++;
                health.lastPlayFailAt = health.updatedAt;
                health.lastPlayError = trim(error);
            }
            markDirty();
        }
    }

    public static void sortSites(List<Site> sites) {
        if (!Setting.isSiteHealthSort()) return;
        if (sites == null || sites.size() < 2) return;
        sites.sort((a, b) -> Double.compare(score(b.getKey()), score(a.getKey())));
    }

    public static void sortVods(List<Vod> vods) {
        if (!Setting.isSiteHealthSort()) return;
        if (vods == null || vods.size() < 2) return;
        vods.sort(SiteHealthStore::compareVods);
    }

    public static int compareVods(Vod a, Vod b) {
        if (!Setting.isSiteHealthSort()) return 0;
        return Double.compare(score(b.getSiteKey()), score(a.getSiteKey()));
    }

    public static void clear() {
        synchronized (SiteHealthStore.class) {
            ensureLoaded();
            items.clear();
            dirty = false;
        }
        App.removeCallbacks(SAVE);
        Prefers.remove(KEY);
    }

    public static int getColor(Site site) {
        Status status = getStatus(site);
        if (status == Status.GOOD) return COLOR_GOOD;
        if (status == Status.WARN) return COLOR_WARN;
        if (status == Status.BAD) return COLOR_BAD;
        return COLOR_UNKNOWN;
    }

    public static void flush() {
        App.removeCallbacks(SAVE);
        Task.execute(SiteHealthStore::saveNow);
    }

    private static Status getStatus(Site site) {
        if (skip(site)) return Status.UNKNOWN;
        synchronized (SiteHealthStore.class) {
            Health health = find(site.getKey());
            if (health == null || health.total() == 0) return Status.UNKNOWN;
            double score = health.score();
            if (health.lastPlayFailAt > health.lastPlaySuccessAt && health.playFail >= 3 && score < 0) return Status.BAD;
            if (score >= 20 || health.lastPlaySuccessAt >= health.lastPlayFailAt && health.playSuccess > 0) return Status.GOOD;
            if (score <= -20) return Status.BAD;
            return Status.WARN;
        }
    }

    private static double score(String key) {
        if (skip(key)) return 0;
        synchronized (SiteHealthStore.class) {
            Health health = find(key);
            return health == null ? 0 : health.score();
        }
    }

    private static Health get(String siteKey) {
        ensureLoaded();
        String key = key(siteKey);
        Health health = items.get(key);
        if (health == null) items.put(key, health = new Health());
        return health;
    }

    private static Health find(String siteKey) {
        ensureLoaded();
        return items.get(key(siteKey));
    }

    private static String key(String siteKey) {
        return VodConfig.getCid() + ":" + siteKey;
    }

    private static boolean skip(Site site) {
        return site == null || skip(site.getKey());
    }

    private static boolean skip(String key) {
        return TextUtils.isEmpty(key) || SiteApi.PUSH.equals(key);
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Map<String, Health> restored = App.gson().fromJson(Prefers.getString(KEY), TYPE);
            if (restored != null) items.putAll(restored);
        } catch (Throwable ignored) {
        }
    }

    private static void markDirty() {
        dirty = true;
        App.post(SAVE, SAVE_DELAY);
    }

    private static void saveNow() {
        Map<String, Health> copy;
        synchronized (SiteHealthStore.class) {
            ensureLoaded();
            if (!dirty) return;
            prune();
            copy = new LinkedHashMap<>(items);
            dirty = false;
        }
        Prefers.put(KEY, App.gson().toJson(copy));
    }

    private static void prune() {
        long expire = System.currentTimeMillis() - RETAIN_MS;
        items.entrySet().removeIf(entry -> entry.getValue().updatedAt > 0 && entry.getValue().updatedAt < expire);
    }

    private static String trim(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text.length() <= 120 ? text : text.substring(0, 120);
    }

    private enum Status {GOOD, WARN, BAD, UNKNOWN}

    public static class Health {

        private int searchSuccess;
        private int searchFail;
        private int detailSuccess;
        private int detailFail;
        private int playSuccess;
        private int playFail;
        private int lastSearchCount;
        private long lastSearchCost;
        private long lastDetailCost;
        private long lastSearchSuccessAt;
        private long lastSearchFailAt;
        private long lastDetailSuccessAt;
        private long lastDetailFailAt;
        private long lastPlaySuccessAt;
        private long lastPlayFailAt;
        private long updatedAt;
        private String lastSearchError;
        private String lastDetailError;
        private String lastPlayError;

        private int total() {
            return searchSuccess + searchFail + detailSuccess + detailFail + playSuccess + playFail;
        }

        private double score() {
            int success = searchSuccess + detailSuccess * 2 + playSuccess * 5;
            int fail = searchFail + detailFail * 2 + playFail * 5;
            if (success + fail == 0) return 0;
            double score = 60.0 * (success - fail) / (success + fail + 4.0);
            score += Math.min(lastSearchCount, 20) * 1.2;
            score -= Math.min(lastSearchCost, TimeUnit.SECONDS.toMillis(8)) / 1000.0;
            score -= Math.min(lastDetailCost, TimeUnit.SECONDS.toMillis(8)) / 1500.0;
            if (lastPlaySuccessAt > lastPlayFailAt) score += 18;
            if (lastPlayFailAt > lastPlaySuccessAt) score -= 18;
            return score;
        }
    }
}
