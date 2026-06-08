package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public class LiveSetting {

    public static boolean isBoot() {
        return Prefers.getBoolean("boot_live");
    }

    public static void putBoot(boolean boot) {
        Prefers.put("boot_live", boot);
    }

    public static boolean isAcross() {
        return Prefers.getBoolean("across", true);
    }

    public static void putAcross(boolean across) {
        Prefers.put("across", across);
    }

    public static boolean isChange() {
        return Prefers.getBoolean("change", true);
    }

    public static void putChange(boolean change) {
        Prefers.put("change", change);
    }

    public static boolean isInvert() {
        return Prefers.getBoolean("invert");
    }

    public static void putInvert(boolean invert) {
        Prefers.put("invert", invert);
    }

    public static int getScale() {
        return Prefers.getInt("scale_live", PlayerSetting.getScale());
    }

    public static void putScale(int scale) {
        Prefers.put("scale_live", scale);
    }
}
