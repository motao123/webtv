package com.github.catvod.crawler;

import android.text.TextUtils;

import com.orhanobut.logger.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;

public class SpiderDebug {

    private static final String TAG = SpiderDebug.class.getSimpleName();

    public static void log(Throwable th) {
        log(TAG, th);
    }

    public static void log(String tag, Throwable th) {
        if (th == null) return;
        if (!DebugLogStore.isEnabled()) return;
        StringWriter writer = new StringWriter();
        th.printStackTrace(new PrintWriter(writer));
        Logger.t(tag).e(writer.toString());
        DebugLogStore.add(tag, writer.toString());
    }

    public static void log(String msg) {
        if (TextUtils.isEmpty(msg)) return;
        if (!DebugLogStore.isEnabled()) return;
        Logger.t(TAG).d(msg);
        DebugLogStore.add(TAG, msg);
    }

    public static void log(String tag, String msg, Object... args) {
        if (TextUtils.isEmpty(msg)) return;
        if (!DebugLogStore.isEnabled()) return;
        Logger.t(tag).d(msg, args);
        DebugLogStore.add(tag, format(msg, args));
    }

    private static String format(String msg, Object... args) {
        try {
            return args == null || args.length == 0 ? msg : String.format(Locale.US, msg, args);
        } catch (Throwable e) {
            return msg;
        }
    }
}
