package com.fongmi.android.tv.utils;

import com.github.catvod.utils.Path;

import java.io.File;

public class CacheManager {

    public static long getCacheSize() {
        return getDirSize(Path.exo());
    }

    public static String getCacheSizeFormatted() {
        long size = getCacheSize();
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
        return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    public static void clearCache() {
        Task.execute(() -> Path.clear(Path.exo()));
    }

    private static long getDirSize(File dir) {
        long size = 0;
        if (dir == null || !dir.exists()) return 0;
        if (dir.isFile()) return dir.length();
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File file : files) {
            if (file.isFile()) size += file.length();
            else size += getDirSize(file);
        }
        return size;
    }
}
