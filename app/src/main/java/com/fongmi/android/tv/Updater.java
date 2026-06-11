package com.fongmi.android.tv;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;

import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

public class Updater implements Download.Callback, UpdateListener {

    private Download download;
    private UpdateDialog dialog;
    private FragmentActivity activity;

    private Updater() {
    }

    public static Updater create() {
        return new Updater();
    }

    private File getFile() {
        return Path.cache("update.apk");
    }

    private String getJson() {
        return Github.getJson(BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi);
    }

    private String getApk() {
        return Github.getApk(BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi);
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
            download = Download.create(getApk(), getFile());
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
        dialog = UpdateDialog.create().title(ResUtil.getString(R.string.update_version, version)).desc(desc).listener(this).show(activity);
    }

    @Override
    public void onConfirm(View view) {
        view.setEnabled(false);
        Notify.show(R.string.update_downloading);
        download.start(this);
    }

    @Override
    public void onCancel(View view) {
        Setting.putUpdate(false);
        if (download != null) download.cancel();
        dismiss();
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress) {
        if (dialog != null) dialog.setProgress(progress);
    }

    @Override
    public void error(String msg) {
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        Task.execute(() -> {
            if (install(file)) {
                App.post(this::dismiss);
                return;
            }
            String path = exportToDownloads(file);
            App.post(() -> {
                if (path != null) Notify.show(ResUtil.getString(R.string.update_export_done, path));
                else Notify.show(R.string.update_export_failed);
                dismiss();
            });
        });
    }

    private boolean install(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                uri = FileProvider.getUriForFile(App.get(), App.get().getPackageName() + ".provider", file);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                uri = Uri.fromFile(file);
            }
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            App.get().startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String exportToDownloads(File file) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, file.getName());
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = App.get().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return null;
                try (OutputStream os = App.get().getContentResolver().openOutputStream(uri); FileInputStream is = new FileInputStream(file)) {
                    if (os == null) return null;
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = is.read(buffer)) > 0) os.write(buffer, 0, read);
                }
                return Environment.DIRECTORY_DOWNLOADS + "/" + file.getName();
            }
            File legacy = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), file.getName());
            if (!legacy.getParentFile().exists() && !legacy.getParentFile().mkdirs()) return null;
            try (FileInputStream is = new FileInputStream(file); java.io.FileOutputStream os = new java.io.FileOutputStream(legacy)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = is.read(buffer)) > 0) os.write(buffer, 0, read);
            }
            return legacy.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }
}