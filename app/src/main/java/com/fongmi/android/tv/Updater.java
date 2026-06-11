package com.fongmi.android.tv;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import org.json.JSONObject;

public class Updater implements UpdateListener {

    private static final String GITHUB_RELEASE = "https://github.com/motao123/webtv/releases/latest";
    private static final String CNB_RELEASE = "https://cnb.cool/code_free/webtv/-/git/raw/main/apk";

    private UpdateDialog dialog;
    private FragmentActivity activity;

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    private String getJson() {
        return Github.getJson(BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi);
    }

    public Updater force() {
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    public void start(FragmentActivity activity) {
        this.activity = activity;
        if (!Setting.getUpdate()) return;
        Task.execute(() -> doInBackground(activity));
    }

    private void doInBackground(FragmentActivity activity) {
        try {
            JSONObject object = new JSONObject(OkHttp.string(getJson()));
            String name = object.optString("name");
            String desc = object.optString("desc");
            int code = object.optInt("code");
            if (code <= BuildConfig.VERSION_CODE) {
                App.post(() -> Notify.show(R.string.update_latest));
                return;
            }
            App.post(() -> show(activity, name, desc));
        } catch (Exception e) {
            SpiderDebug.log(e);
            App.post(() -> Notify.show(R.string.update_failed));
        }
    }

    private void show(FragmentActivity activity, String version, String desc) {
        dismiss();
        String apkName = BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi + ".apk";
        String githubUrl = GITHUB_RELEASE;
        String cnbUrl = CNB_RELEASE + "/" + apkName;
        dialog = UpdateDialog.create()
                .title(ResUtil.getString(R.string.update_version, version))
                .desc(desc + "\n\n" + ResUtil.getString(R.string.update_manual_msg))
                .listener(this)
                .show(activity);
    }

    @Override
    public void onConfirm(View view) {
        String apkName = BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi + ".apk";
        String url = Github.getApk(BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi);
        copyAndOpen(url);
        dismiss();
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        dismiss();
    }

    private void copyAndOpen(String url) {
        try {
            ClipboardManager cm = (ClipboardManager) App.get().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("update", url));
        } catch (Exception ignored) {
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            App.get().startActivity(intent);
        } catch (Exception e) {
            Notify.show(ResUtil.getString(R.string.update_failed));
        }
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }
}
