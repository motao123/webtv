package com.fongmi.android.tv.setting;

import android.Manifest;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.LocaleList;
import android.provider.Settings;
import android.util.DisplayMetrics;

import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.WebViewUtil;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Trans;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class Setting {

    public static final int WALL_AURORA_GLASS = 10;
    public static final int WALL_SUNSET_PRISM = 11;
    public static final int WALL_MINT_GLACIER = 12;
    public static final int WALL_LIQUID_CHROME = 13;
    public static final int WALL_NEON_BERRY = 14;
    public static final int WALL_CHAMPAGNE_MIST = 15;
    public static final int WALL_GLASS_GRADIENT = 16;
    public static final int WALL_DEEP_SPACE_GLASS = 17;
    public static final int WALL_POLAR_LIGHT_GLASS = 18;
    public static final int WALL_NEON_CYBER = 19;
    public static final int WALL_WARM_MOON_GLASS = 20;
    public static final int WALL_CRYSTAL_SKY = 21;
    public static final int WALL_DREAM_PURPLE = 22;
    public static final int WALL_SKY_MINT = 23;
    public static final int WALL_FOREST_MIST = 24;
    public static final int WALL_DAYLIGHT_MINIMAL = 25;
    public static final int WALL_DEEP_SEA = 26;
    public static final int WALL_VIOLET_SMOKE = 27;
    public static final int WALL_ROSE_VEIL = 28;
    public static final int WALL_EMERALD_AURORA = 29;
    public static final int WALL_BLUE_SILK = 30;
    public static final int WALL_PEACH_DAWN = 31;
    public static final int WALL_GRAPHITE_SMOKE = 32;
    public static final int WALL_PASTEL_PRISM = 33;
    public static final int WALL_MIDNIGHT_MOON = 34;
    public static final int WALL_CYAN_CRYSTAL = 35;
    public static final int WALL_LAVENDER_CRYSTAL = 36;
    public static final int WALL_GREEN = 1;

    private static final int[] DEFAULT_WALLS = {
            WALL_GREEN, WALL_DREAM_PURPLE, WALL_LAVENDER_CRYSTAL, WALL_PASTEL_PRISM, WALL_ROSE_VEIL, WALL_VIOLET_SMOKE,
            WALL_NEON_BERRY, WALL_MIDNIGHT_MOON, WALL_NEON_CYBER, WALL_DEEP_SPACE_GLASS, WALL_GRAPHITE_SMOKE,
            WALL_DAYLIGHT_MINIMAL, WALL_SKY_MINT, WALL_POLAR_LIGHT_GLASS, WALL_GLASS_GRADIENT, WALL_CRYSTAL_SKY,
            WALL_BLUE_SILK, WALL_CYAN_CRYSTAL, WALL_MINT_GLACIER, WALL_AURORA_GLASS, WALL_DEEP_SEA,
            WALL_LIQUID_CHROME, WALL_FOREST_MIST, WALL_EMERALD_AURORA, WALL_WARM_MOON_GLASS, WALL_PEACH_DAWN,
            WALL_CHAMPAGNE_MIST, WALL_SUNSET_PRISM
    };

    private static final Type STRING_LIST = new TypeToken<List<String>>() {}.getType();

    public static final int CSP_WARMUP_DISABLED = 0;
    public static final int CSP_WARMUP_DEFAULT = 1;
    public static final int CSP_WARMUP_CUSTOM = 2;

    public static String getDoh() {
        return Prefers.getString("doh");
    }

    public static void putDoh(String doh) {
        Prefers.put("doh", doh);
    }

    public static String getKeyword() {
        return Prefers.getString("keyword");
    }

    public static void putKeyword(String keyword) {
        Prefers.put("keyword", keyword);
    }

    public static String getHot() {
        return Prefers.getString("hot");
    }

    public static void putHot(String hot) {
        Prefers.put("hot", hot);
    }

    public static String getHotTv() {
        return Prefers.getString("hot_tv");
    }

    public static void putHotTv(String hot) {
        Prefers.put("hot_tv", hot);
    }

    public static String getHotMovie() {
        return Prefers.getString("hot_movie");
    }

    public static void putHotMovie(String hot) {
        Prefers.put("hot_movie", hot);
    }

    public static String getHotVariety() {
        return Prefers.getString("hot_variety");
    }

    public static void putHotVariety(String hot) {
        Prefers.put("hot_variety", hot);
    }

    public static String getUa() {
        return Prefers.getString("ua");
    }

    public static void putUa(String ua) {
        Prefers.put("ua", ua);
    }

    public static int getWall() {
        return Prefers.getInt("wall", WALL_GREEN);
    }

    public static void putWall(int wall) {
        Prefers.put("wall", wall);
    }

    public static int nextDefaultWall() {
        int wall = getWall();
        for (int i = 0; i < DEFAULT_WALLS.length; i++) if (DEFAULT_WALLS[i] == wall) return DEFAULT_WALLS[(i + 1) % DEFAULT_WALLS.length];
        return WALL_GREEN;
    }

    public static int[] getDefaultWalls() {
        return DEFAULT_WALLS.clone();
    }

    public static int getDefaultWallIndex(int wall) {
        for (int i = 0; i < DEFAULT_WALLS.length; i++) if (DEFAULT_WALLS[i] == wall) return i;
        return -1;
    }

    public static boolean isBuiltInWall(int wall) {
        return getDefaultWallIndex(wall) != -1;
    }

    public static boolean isBuiltInColorWall(int wall) {
        return false;
    }

    public static boolean isBuiltInDesignWall(int wall) {
        return wall != WALL_GREEN && isBuiltInWall(wall);
    }

    public static int getBuiltInWallColor(int wall) {
        if (wall == WALL_GREEN) return 0xFF40C090;
        if (wall == WALL_EMERALD_AURORA) return 0xFF27B07D;
        if (wall == WALL_NEON_BERRY || wall == WALL_VIOLET_SMOKE) return 0xFF7C4BE2;
        if (wall == WALL_ROSE_VEIL || wall == WALL_CHAMPAGNE_MIST) return 0xFFB27FAE;
        if (wall == WALL_MIDNIGHT_MOON || wall == WALL_NEON_CYBER || wall == WALL_DEEP_SPACE_GLASS) return 0xFF4935B4;
        if (wall == WALL_CYAN_CRYSTAL || wall == WALL_DEEP_SEA) return 0xFF168BA6;
        return 0xFF7560CA;
    }

    public static String getBuiltInWallName(int wall) {
        if (wall == WALL_GREEN) return "经典绿意";
        String[] names = {"蓝紫流光", "珊瑚暮色", "薄荷星云", "银色潮汐", "莓果极光", "香槟晨雾", "玻璃渐变", "深空玻璃", "极光玻璃", "暗夜霓虹", "暖月玻璃", "冰晶幻彩", "梦幻紫霞", "雾青薄荷", "森林雾绿", "雾蓝极简", "深海月影", "紫雾星旋", "玫瑰薄雾", "翡翠极光", "蓝绸流影", "暖桃晨光", "石墨烟岚", "彩虹幻璃", "午夜月影", "水晶青蓝", "薰衣水晶"};
        int index = getDefaultWallIndex(wall) - 1;
        return index < 0 || index >= names.length ? "经典绿意" : names[index];
    }

    public static int getWallType() {
        return Prefers.getInt("wall_type", 0);
    }

    public static void putWallType(int type) {
        Prefers.put("wall_type", type);
    }

    public static void restoreClassicGreenWall() {
        String key = "classic_green_wall_migrated";
        boolean migrated = Prefers.getBoolean(key);
        if (WallpaperMigrationPolicy.shouldRestoreClassicGreen(migrated, getWall(), getWallType())) putWall(WALL_GREEN);
        Prefers.put(key, true);
    }

    public static int getReset() {
        return Prefers.getInt("reset", 0);
    }

    public static void putReset(int reset) {
        Prefers.put("reset", reset);
    }

    public static int getSiteMode() {
        return Prefers.getInt("site_mode");
    }

    public static void putSiteMode(int mode) {
        Prefers.put("site_mode", mode);
    }

    public static int getSyncMode() {
        return Prefers.getInt("sync_mode");
    }

    public static void putSyncMode(int mode) {
        Prefers.put("sync_mode", mode);
    }

    public static String getSyncPaths() {
        return Prefers.getString("sync_paths", "TV\nTVBox\nTVData");
    }

    public static void putSyncPaths(String paths) {
        Prefers.put("sync_paths", paths);
    }

    public static String getLoginStatePaths() {
        return Prefers.getString("login_state_paths");
    }

    public static void putLoginStatePaths(String paths) {
        Prefers.put("login_state_paths", paths);
    }

    public static String getLoginStatePendingPaths() {
        return Prefers.getString("login_state_pending_paths");
    }

    public static void putLoginStatePendingPaths(String paths) {
        Prefers.put("login_state_pending_paths", paths);
    }

    public static String getLoginStateSnapshot() {
        return Prefers.getString("login_state_snapshot");
    }

    public static void putLoginStateSnapshot(String snapshot) {
        Prefers.put("login_state_snapshot", snapshot);
    }

    public static String getLoginStateFindings() {
        return Prefers.getString("login_state_findings");
    }

    public static void putLoginStateFindings(String findings) {
        Prefers.put("login_state_findings", findings);
    }

    public static String getSyncDevice() {
        return Prefers.getString("sync_device");
    }

    public static void putSyncDevice(String uuid) {
        Prefers.put("sync_device", uuid);
    }

    public static boolean isFamilyFilter() {
        return Prefers.getBoolean("family_filter_enabled");
    }

    public static void putFamilyFilter(boolean enabled) {
        Prefers.put("family_filter_enabled", enabled);
    }

    public static String getFamilyFilterKeywords() {
        return Prefers.getString("family_filter_keywords", "情色\n三级片");
    }

    public static void putFamilyFilterKeywords(String keywords) {
        Prefers.put("family_filter_keywords", keywords);
    }

    public static String getFamilyFilterPass() {
        return Prefers.getString("family_filter_pass");
    }

    public static void putFamilyFilterPass(String pass) {
        Prefers.put("family_filter_pass", pass);
    }

    public static boolean isDriveCheck() {
        return Prefers.getBoolean("drive_check", true);
    }

    public static void putDriveCheck(boolean driveCheck) {
        Prefers.put("drive_check", driveCheck);
    }

    public static boolean isWebHomeFullscreen() {
        return Prefers.getBoolean("web_home_fullscreen", true);
    }

    public static void putWebHomeFullscreen(boolean fullscreen) {
        Prefers.put("web_home_fullscreen", fullscreen);
    }

    public static boolean isPlaybackArtworkWall() {
        return Prefers.getBoolean("playback_artwork_wall", true);
    }

    public static void putPlaybackArtworkWall(boolean artworkWall) {
        Prefers.put("playback_artwork_wall", artworkWall);
    }

    public static boolean isCspWarmup() {
        return getCspWarmupMode() != CSP_WARMUP_DISABLED;
    }

    public static void putCspWarmup(boolean warmup) {
        if (warmup) {
            Prefers.put("csp_warmup", true);
            if (getCspWarmupSelectedMode() == CSP_WARMUP_DISABLED) Prefers.put("csp_warmup_mode", CSP_WARMUP_DEFAULT);
        } else {
            Prefers.put("csp_warmup", false);
        }
    }

    public static int getCspWarmupMode() {
        if (!Prefers.getBoolean("csp_warmup")) return CSP_WARMUP_DISABLED;
        return getCspWarmupSelectedMode();
    }

    public static int getCspWarmupSelectedMode() {
        int mode = Prefers.getInt("csp_warmup_mode", CSP_WARMUP_DEFAULT);
        return mode == CSP_WARMUP_CUSTOM ? CSP_WARMUP_CUSTOM : CSP_WARMUP_DEFAULT;
    }

    public static void putCspWarmupMode(int mode) {
        if (mode == CSP_WARMUP_DISABLED) {
            Prefers.put("csp_warmup", false);
        } else {
            Prefers.put("csp_warmup", true);
            Prefers.put("csp_warmup_mode", mode == CSP_WARMUP_CUSTOM ? CSP_WARMUP_CUSTOM : CSP_WARMUP_DEFAULT);
        }
    }

    public static List<String> getCspWarmupSites() {
        try {
            List<String> keys = App.gson().fromJson(Prefers.getString("csp_warmup_sites", "[]"), STRING_LIST);
            if (keys == null) return Collections.emptyList();
            List<String> result = new ArrayList<>();
            for (String key : keys) if (key != null && !key.trim().isEmpty() && !result.contains(key.trim())) result.add(key.trim());
            return result;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public static void putCspWarmupSites(List<String> keys) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (keys != null) for (String key : keys) if (key != null && !key.trim().isEmpty()) result.add(key.trim());
        Prefers.put("csp_warmup_sites", App.gson().toJson(result));
    }

    public static boolean isIncognito() {
        return Prefers.getBoolean("incognito");
    }

    public static void putIncognito(boolean incognito) {
        Prefers.put("incognito", incognito);
    }

    public static String getLanguage() {
        return DisplaySettings.normalizeLanguage(Prefers.getString("language", DisplaySettings.LANGUAGE_ENGLISH));
    }

    public static void putLanguage(String language) {
        String value = DisplaySettings.normalizeLanguage(language);
        Prefers.put("language", value);
        applyLanguage(value);
    }

    public static int getLanguageIndex() {
        return DisplaySettings.languageIndex(getLanguage());
    }

    public static void putLanguageIndex(int index) {
        putLanguage(DisplaySettings.languageAt(index));
    }

    public static void applyLanguage() {
        applyLanguage(getLanguage());
    }

    private static void applyLanguage(String language) {
        if (DisplaySettings.LANGUAGE_SIMPLIFIED.equals(language)) Trans.setTraditional(false);
        else if (DisplaySettings.LANGUAGE_TRADITIONAL.equals(language)) Trans.setTraditional(true);
        else Trans.setTraditional(null);
    }

    public static Context wrapLanguage(Context context) {
        String language = getLanguage();
        applyLanguage(language);
        Locale locale = Locale.forLanguageTag(language);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLocales(new LocaleList(locale));
        return context.createConfigurationContext(config);
    }

    public static int getUiScale() {
        return DisplaySettings.normalizeUiScale(Prefers.getInt("ui_scale", DisplaySettings.UI_SCALE_FOLLOW_SYSTEM));
    }

    public static void putUiScale(int scale) {
        Prefers.put("ui_scale", DisplaySettings.normalizeUiScale(scale));
    }

    public static int getUiScaleIndex() {
        return DisplaySettings.uiScaleIndex(getUiScale());
    }

    public static void putUiScaleIndex(int index) {
        putUiScale(DisplaySettings.uiScaleAt(index));
    }

    public static Context wrapDisplay(Context context) {
        return wrapUiScale(wrapLanguage(context));
    }

    public static Context wrapUiScale(Context context) {
        int scale = getUiScale();
        if (scale == DisplaySettings.UI_SCALE_FOLLOW_SYSTEM) return context;
        Configuration config = new Configuration(context.getResources().getConfiguration());
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int stableDensity = DisplayMetrics.DENSITY_DEVICE_STABLE > 0 ? DisplayMetrics.DENSITY_DEVICE_STABLE : metrics.densityDpi;
        int densityDpi = Math.max(DisplayMetrics.DENSITY_LOW, Math.round(stableDensity * DisplaySettings.uiScaleFactor(scale)));
        config.densityDpi = densityDpi;
        config.fontScale = 1.0f;
        config.screenWidthDp = pxToDp(metrics.widthPixels, densityDpi);
        config.screenHeightDp = pxToDp(metrics.heightPixels, densityDpi);
        config.smallestScreenWidthDp = Math.min(config.screenWidthDp, config.screenHeightDp);
        return context.createConfigurationContext(config);
    }

    private static int pxToDp(int px, int densityDpi) {
        return Math.max(1, Math.round(px * (float) DisplayMetrics.DENSITY_DEFAULT / densityDpi));
    }

    public static boolean isSiteHealthSort() {
        return Prefers.getBoolean("site_health_sort", true);
    }

    public static void putSiteHealthSort(boolean sort) {
        Prefers.put("site_health_sort", sort);
    }

    public static boolean isSiteHealthDialogSort() {
        return Prefers.getBoolean("site_health_dialog_sort");
    }

    public static void putSiteHealthDialogSort(boolean sort) {
        Prefers.put("site_health_dialog_sort", sort);
    }

    public static boolean isWebHomeExtension() {
        return Prefers.getBoolean("web_home_extension", true);
    }

    public static void putWebHomeExtension(boolean extension) {
        Prefers.put("web_home_extension", extension);
    }

    public static boolean isDebugLog() {
        return DebugLogStore.isEnabled();
    }

    public static void putDebugLog(boolean debugLog) {
        DebugLogStore.setEnabled(debugLog);
        if (debugLog) logDebugEnvironment("enable");
    }

    public static void logDebugEnvironment(String reason) {
        boolean hardwareAccelerated = (App.get().getApplicationInfo().flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) != 0;
        SpiderDebug.log("env", "reason=%s app=%s(%s) mode=%s abi=%s debug=%s hardware=%s android=%s sdk=%s incremental=%s manufacturer=%s brand=%s model=%s device=%s product=%s supportedAbis=%s",
                reason,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                BuildConfig.FLAVOR_mode,
                BuildConfig.FLAVOR_abi,
                BuildConfig.DEBUG,
                hardwareAccelerated,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
                Build.VERSION.INCREMENTAL,
                Build.MANUFACTURER,
                Build.BRAND,
                Build.MODEL,
                Build.DEVICE,
                Build.PRODUCT,
                String.join(",", Build.SUPPORTED_ABIS));
        WebViewUtil.logProvider("debug-env");
    }

    public static boolean isShellProxy() {
        return Prefers.getBoolean("shell_proxy");
    }

    public static void putShellProxy(boolean shellProxy) {
        Prefers.put("shell_proxy", shellProxy);
        ProxySetting.apply();
    }

    public static String getShellProxyRules() {
        return Prefers.getString("shell_proxy_rules");
    }

    public static void putShellProxyRules(String rules) {
        Prefers.put("shell_proxy_rules", rules);
        ProxySetting.apply();
    }

    public static void putShellProxyConfig(String url, String rules) {
        Prefers.put("shell_proxy_url", url);
        Prefers.put("shell_proxy_rules", rules);
        Prefers.put("shell_proxy_hosts", "*");
        ProxySetting.apply();
    }

    public static String getShellProxyUrl() {
        return Prefers.getString("shell_proxy_url");
    }

    public static void putShellProxyUrl(String url) {
        Prefers.put("shell_proxy_url", url);
        ProxySetting.apply();
    }

    public static String getShellProxyHosts() {
        return Prefers.getString("shell_proxy_hosts", "*");
    }

    public static void putShellProxyHosts(String hosts) {
        Prefers.put("shell_proxy_hosts", hosts);
        ProxySetting.apply();
    }

    public static boolean getUpdate() {
        return Prefers.getBoolean("update", true);
    }

    public static void putUpdate(boolean update) {
        Prefers.put("update", update);
    }

    /**
     * DLNA renderers that cannot read HLS playlists need the phone to concatenate .ts segments
     * into one continuous MPEG-TS stream. Off by default: it disables seeking and keeps the
     * phone in the stream path, so it would be a regression for HLS-capable devices.
     */
    public static boolean getCastTsCompat() {
        return Prefers.getBoolean("cast_ts_compat", false);
    }

    public static void putCastTsCompat(boolean compat) {
        Prefers.put("cast_ts_compat", compat);
    }

    public static String getMirror() {
        return Prefers.getString("update_mirror", "auto");
    }

    public static void putMirror(String mirror) {
        Prefers.put("update_mirror", mirror);
        Github.setMirror(mirror);
    }

    public static final String UPDATE_PATH_BROWSER = "browser";
    public static final String UPDATE_PATH_APP = "app";

    /**
     * Which action the update prompt offers as its primary button: "browser" hands the
     * download to the system browser so the phone stays free, "app" downloads in the app.
     * Phones only — a TV has no browser, so its prompt always downloads in the app.
     */
    public static String getUpdatePath() {
        return Prefers.getString("update_path", UPDATE_PATH_BROWSER);
    }

    public static void putUpdatePath(String path) {
        Prefers.put("update_path", path);
    }

    public static String getUpdateGithubProxy() {
        return Prefers.getString("update_github_proxy", "direct");
    }

    public static void putUpdateGithubProxy(String id) {
        Prefers.put("update_github_proxy", id);
    }

    public static String getUpdateGithubProxyUrl() {
        return Prefers.getString("update_github_proxy_url", "");
    }

    public static void putUpdateGithubProxyUrl(String url) {
        Prefers.put("update_github_proxy_url", url);
    }

    public static String getUpdateGithubProxyMode() {
        return Prefers.getString("update_github_proxy_mode", "full_url");
    }

    public static void putUpdateGithubProxyMode(String mode) {
        Prefers.put("update_github_proxy_mode", mode);
    }

    public static boolean isAdblock() {
        return Prefers.getBoolean("adblock", true);
    }

    public static void putAdblock(boolean adblock) {
        Prefers.put("adblock", adblock);
    }

    public static boolean isZhuyin() {
        return Prefers.getBoolean("zhuyin");
    }

    public static void putZhuyin(boolean zhuyin) {
        Prefers.put("zhuyin", zhuyin);
    }

    public static boolean isCompactEpisodeTitle() {
        return Prefers.getBoolean("compact_episode_title");
    }

    public static void putCompactEpisodeTitle(boolean compact) {
        Prefers.put("compact_episode_title", compact);
    }

    public static boolean hasFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return true;
        return hasLegacyReadAccess();
    }

    private static boolean hasLegacyReadAccess() {
        int read = ContextCompat.checkSelfPermission(App.get(), Manifest.permission.READ_EXTERNAL_STORAGE);
        int write = ContextCompat.checkSelfPermission(App.get(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasFileManager() {
        return false;
    }
}
