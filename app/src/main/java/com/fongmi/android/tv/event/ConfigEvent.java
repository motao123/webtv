package com.fongmi.android.tv.event;

import com.fongmi.android.tv.setting.LiveSetting;

import org.greenrobot.eventbus.EventBus;

public record ConfigEvent(Type type) {

    public static void common() {
        EventBus.getDefault().post(new ConfigEvent(Type.COMMON));
    }

    public static void vod() {
        EventBus.getDefault().post(new ConfigEvent(Type.VOD));
    }

    public static void live() {
        EventBus.getDefault().post(new ConfigEvent(Type.LIVE));
    }

    public static void wall() {
        EventBus.getDefault().post(new ConfigEvent(Type.WALL));
    }

    public static void boot() {
        EventBus.getDefault().post(new ConfigEvent(Type.BOOT));
        LiveSetting.putBoot(false);
    }

    public boolean isVod() {
        return type == Type.VOD;
    }

    public boolean isLive() {
        return type == Type.LIVE;
    }

    public enum Type {
        COMMON, VOD, LIVE, WALL, BOOT
    }
}
