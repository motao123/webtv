package com.fongmi.android.tv.ui.dialog;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.service.ManageService;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class ManagePageDialog {

    private ManagePageDialog() {
    }

    public static void show(Fragment fragment) {
        show(fragment.requireActivity());
    }

    public static void show(FragmentActivity activity) {
        ManageService.start(activity);
        String localUrl = ManageService.getLocalUrl();
        String lanUrl = ManageService.getLanUrl();
        SpiderDebug.log("server", "manage page ready url=%s lan=%s", localUrl, lanUrl);
        String message = activity.getString(R.string.manage_page_dialog_message, lanUrl, localUrl);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.setting_manage_page)
                .setMessage(message)
                .setNegativeButton(R.string.manage_page_stop, null)
                .setNeutralButton(R.string.manage_page_copy_url, null)
                .setPositiveButton(R.string.manage_page_open_browser, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> open(activity, localUrl));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> copy(activity, lanUrl));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> { ManageService.stop(activity); dialog.dismiss(); });
        });
        dialog.show();
    }

    private static void open(FragmentActivity activity, String url) {
        if (ManageService.shouldOpenBackgroundPowerSettings(activity)) {
            showBatteryDialog(activity, () -> openBrowser(activity, url));
        } else {
            openBrowser(activity, url);
        }
    }

    private static void showBatteryDialog(FragmentActivity activity, Runnable openAction) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.manage_page_battery_title)
                .setMessage(activity.getString(R.string.manage_page_battery_message, ManageService.getBackgroundPowerGuide(activity)))
                .setNegativeButton(R.string.manage_page_open_anyway, null)
                .setPositiveButton(R.string.manage_page_battery_allow, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> { ManageService.confirmBackgroundPowerHandled(); dialog.dismiss(); openAction.run(); });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (ManageService.openBackgroundPowerSettings(activity)) dialog.dismiss();
                else Notify.show(R.string.manage_page_battery_open_failed);
            });
        });
        dialog.show();
    }

    private static void openBrowser(FragmentActivity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Notify.show(R.string.manage_page_no_browser);
        }
    }

    private static void copy(FragmentActivity activity, String url) {
        ClipboardManager manager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) return;
        manager.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.setting_manage_page), url));
        Notify.show(R.string.manage_page_url_copied);
    }
}
