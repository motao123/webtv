package com.fongmi.android.tv.ui.activity;

import android.app.Activity;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;

public class VodActivityRouter {

    public static void loadVod(Activity activity, Config config) {
        VodConfig.load(config, callback(activity));
    }

    public static void loadLive(Activity activity, Config config) {
        LiveConfig.load(config, callback(activity));
    }

    public static void loadWall(Activity activity, Config config) {
        Setting.putWall(0);
        WallConfig.load(config, callback(activity));
    }

    private static Callback callback(Activity activity) {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(activity);
            }

            @Override
            public void success() {
                Notify.dismiss();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }
}
