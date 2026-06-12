package com.fongmi.android.tv.ui.dialog;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.LoginStateSync;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LoginStateLearnDialog {

    private static final int MAX_SECTION_ROWS = 4;

    private LoginStateLearnDialog() {
    }

    public static void show(Fragment fragment, Runnable callback) {
        show(fragment.requireActivity(), callback);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        boolean learning = LoginStateSync.hasLearningSnapshot();
        int learned = LoginStateSync.learnedCount();
        final AlertDialog[] dialogRef = new AlertDialog[1];
        Runnable resetAction = () -> {
            LoginStateSync.resetLearningResults();
            Notify.show(R.string.login_state_reset_results_done);
            if (callback != null) callback.run();
            AlertDialog current = dialogRef[0];
            if (current != null) current.dismiss();
            if (!activity.isFinishing()) show(activity, callback);
        };
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(view(activity, learning, learned, resetAction))
                .setNegativeButton(R.string.dialog_negative, null)
                .setNeutralButton(R.string.login_state_manage_paths, null)
                .setPositiveButton(learning ? R.string.login_state_finish : R.string.login_state_start, null)
                .create();
        dialogRef[0] = dialog;
        dialog.setOnShowListener(d -> {
            resize(dialog, activity);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialog.dismiss();
                LoginStatePathDialog.show(activity, callback);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialog.dismiss();
                run(activity, learning, callback);
            });
        });
        dialog.show();
    }

    private static LinearLayoutCompat view(FragmentActivity activity, boolean learning, int learned, Runnable resetAction) {
        LinearLayoutCompat container = new LinearLayoutCompat(activity);
        container.setOrientation(LinearLayoutCompat.VERTICAL);
        container.setPadding(ResUtil.dp2px(24), ResUtil.dp2px(18), ResUtil.dp2px(24), 0);

        addHeader(activity, container, resetAction);

        TextView message = new TextView(activity);
        message.setText(activity.getString(learning ? R.string.login_state_learning_message : R.string.login_state_message, learned));
        message.setTextColor(0xDE000000);
        message.setTextSize(14);
        message.setLineSpacing(0, 1.12f);
        container.addView(message, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setScrollbarFadingEnabled(false);
        scroll.addView(quickView(activity), new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int height = Math.max(ResUtil.dp2px(190), Math.min(ResUtil.dp2px(300), (int) (ResUtil.getScreenHeight(activity) * 0.34f)));
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        params.topMargin = ResUtil.dp2px(12);
        container.addView(scroll, params);
        return container;
    }

    private static void addHeader(FragmentActivity activity, LinearLayoutCompat container, Runnable resetAction) {
        LinearLayoutCompat row = new LinearLayoutCompat(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayoutCompat.HORIZONTAL);
        TextView title = new TextView(activity);
        title.setText(R.string.setting_login_state);
        title.setTextColor(0xDE000000);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (!LoginStateSync.pendingPaths().isEmpty() || !LoginStateSync.findings().isEmpty()) row.addView(resetButton(activity, resetAction), new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(36)));
        container.addView(row, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static TextView resetButton(FragmentActivity activity, Runnable resetAction) {
        TextView button = new TextView(activity);
        button.setText(R.string.login_state_reset_results);
        button.setTextColor(0xFF1A73E8);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(ResUtil.dp2px(36));
        button.setPadding(ResUtil.dp2px(12), 0, ResUtil.dp2px(12), 0);
        button.setClickable(true);
        button.setFocusable(true);
        TypedValue value = new TypedValue();
        if (activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true)) button.setBackgroundResource(value.resourceId);
        button.setOnClickListener(v -> resetAction.run());
        return button;
    }

    private static void resize(AlertDialog dialog, FragmentActivity activity) {
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(activity);
        int width = Math.min((int) (ResUtil.getScreenWidth(activity) * (land ? 0.68f : 0.92f)), ResUtil.dp2px(640));
        params.width = Math.max(width, ResUtil.dp2px(320));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
    }

    private static LinearLayoutCompat quickView(FragmentActivity activity) {
        LinearLayoutCompat container = new LinearLayoutCompat(activity);
        container.setOrientation(LinearLayoutCompat.VERTICAL);
        container.setPadding(0, 0, 0, ResUtil.dp2px(2));
        List<LoginStateSync.Candidate> findings = LoginStateSync.findings();
        addSection(activity, container, R.string.login_state_learned_title, pathItems(LoginStateSync.learnedPaths(), R.string.login_state_state_selected), 0xFFEAF3FF, 0xFF174EA6);
        addSection(activity, container, R.string.login_state_pending_title, pendingCandidates(findings), LoginStateSync.pendingPaths(), 0xFFFFF4E5, 0xFF9A5B00);
        addSection(activity, container, R.string.login_state_findings_title, candidateItems(findings), 0xFFEAF7EE, 0xFF137333);
        if (container.getChildCount() == 0) addEmpty(activity, container);
        return container;
    }

    private static List<QuickItem> pathItems(List<String> paths, int reasonRes) {
        List<QuickItem> result = new ArrayList<>();
        for (String path : paths) result.add(new QuickItem(path, ResUtil.getString(reasonRes), LoginStateSync.displayPath(path)));
        return result;
    }

    private static List<QuickItem> candidateItems(List<LoginStateSync.Candidate> candidates) {
        List<QuickItem> result = new ArrayList<>();
        for (LoginStateSync.Candidate item : candidates) result.add(new QuickItem(item.getPath(), item.getReason(), item.getDisplayPath()));
        return result;
    }

    private static List<QuickItem> pendingCandidates(List<LoginStateSync.Candidate> findings) {
        Map<String, LoginStateSync.Candidate> byPath = new LinkedHashMap<>();
        for (LoginStateSync.Candidate item : findings) byPath.put(LoginStateSync.normalizePath(item.getPath()), item);
        List<QuickItem> result = new ArrayList<>();
        for (String path : LoginStateSync.pendingPaths()) {
            path = LoginStateSync.normalizePath(path);
            LoginStateSync.Candidate item = byPath.get(path);
            result.add(item == null ? new QuickItem(path, ResUtil.getString(R.string.login_state_state_pending), LoginStateSync.displayPath(path)) : new QuickItem(item.getPath(), item.getReason(), item.getDisplayPath()));
        }
        return result;
    }

    private static void addSection(FragmentActivity activity, LinearLayoutCompat parent, int titleRes, List<QuickItem> items, List<String> fallbackPaths, int bgColor, int accentColor) {
        if ((items == null || items.isEmpty()) && (fallbackPaths == null || fallbackPaths.isEmpty())) return;
        LinearLayoutCompat card = sectionCard(activity, titleRes, bgColor, accentColor);
        if (items != null && !items.isEmpty()) {
            int count = 0;
            for (QuickItem item : items) {
                if (count++ >= MAX_SECTION_ROWS) break;
                addRow(activity, card, item.path, item.reason, item.displayPath);
            }
            if (items.size() > MAX_SECTION_ROWS) addMore(activity, card, items.size() - MAX_SECTION_ROWS);
        } else {
            int count = 0;
            for (String path : fallbackPaths) {
                if (count++ >= MAX_SECTION_ROWS) break;
                addRow(activity, card, path, ResUtil.getString(R.string.login_state_state_selected), LoginStateSync.displayPath(path));
            }
            if (fallbackPaths.size() > MAX_SECTION_ROWS) addMore(activity, card, fallbackPaths.size() - MAX_SECTION_ROWS);
        }
        parent.addView(card, sectionParams(parent));
    }

    private static void addSection(FragmentActivity activity, LinearLayoutCompat parent, int titleRes, List<QuickItem> items, int bgColor, int accentColor) {
        if (items.isEmpty()) return;
        LinearLayoutCompat card = sectionCard(activity, titleRes, bgColor, accentColor);
        int count = 0;
        for (QuickItem item : items) {
            if (count++ >= MAX_SECTION_ROWS) break;
            addRow(activity, card, item.path, item.reason, item.displayPath);
        }
        if (items.size() > MAX_SECTION_ROWS) addMore(activity, card, items.size() - MAX_SECTION_ROWS);
        parent.addView(card, sectionParams(parent));
    }

    private static LinearLayoutCompat sectionCard(FragmentActivity activity, int titleRes, int bgColor, int accentColor) {
        LinearLayoutCompat card = new LinearLayoutCompat(activity);
        card.setOrientation(LinearLayoutCompat.VERTICAL);
        card.setPadding(ResUtil.dp2px(12), ResUtil.dp2px(9), ResUtil.dp2px(12), ResUtil.dp2px(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(bgColor);
        background.setCornerRadius(ResUtil.dp2px(8));
        background.setStroke(1, Color.argb(42, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
        card.setBackground(background);
        TextView title = new TextView(activity);
        title.setText(titleRes);
        title.setTextColor(accentColor);
        title.setTextSize(13);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private static LinearLayoutCompat.LayoutParams sectionParams(LinearLayoutCompat parent) {
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (parent.getChildCount() > 0) params.topMargin = ResUtil.dp2px(8);
        return params;
    }

    private static void addRow(FragmentActivity activity, LinearLayoutCompat parent, String path, String reason, String displayPath) {
        TextView row = new TextView(activity);
        String meta = TextUtils.isEmpty(reason) ? "" : reason + "\n";
        row.setText(meta + (TextUtils.isEmpty(displayPath) ? LoginStateSync.displayPath(path) : displayPath));
        row.setTextColor(0xCC000000);
        row.setTextSize(12);
        row.setLineSpacing(0, 1.12f);
        row.setTextIsSelectable(true);
        row.setPadding(0, ResUtil.dp2px(7), 0, 0);
        parent.addView(row, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void addMore(FragmentActivity activity, LinearLayoutCompat parent, int count) {
        TextView more = new TextView(activity);
        more.setText(activity.getString(R.string.login_state_more_count, count));
        more.setTextColor(0x99000000);
        more.setTextSize(12);
        more.setPadding(0, ResUtil.dp2px(7), 0, 0);
        parent.addView(more, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void addEmpty(FragmentActivity activity, LinearLayoutCompat parent) {
        TextView empty = new TextView(activity);
        empty.setText(R.string.login_state_empty);
        empty.setTextColor(0x99000000);
        empty.setTextSize(13);
        empty.setGravity(android.view.Gravity.CENTER);
        empty.setPadding(0, ResUtil.dp2px(28), 0, ResUtil.dp2px(28));
        parent.addView(empty, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void run(FragmentActivity activity, boolean finish, Runnable callback) {
        Task.execute(() -> {
            if (finish) finish(activity, callback);
            else begin(callback);
        });
    }

    private static void begin(Runnable callback) {
        LoginStateSync.beginLearning();
        App.post(() -> {
            Notify.show(R.string.login_state_started);
            if (callback != null) callback.run();
        });
    }

    private static void finish(FragmentActivity activity, Runnable callback) {
        LoginStateSync.LearnResult result = LoginStateSync.finishLearning();
        int selected = result.getSelected().size();
        int pending = LoginStateSync.pendingPaths().size();
        App.post(() -> {
            Notify.show(App.get().getString(R.string.login_state_finished, selected, pending));
            if (callback != null) callback.run();
            if (!activity.isFinishing()) show(activity, callback);
        });
    }

    private static class QuickItem {

        private final String path;
        private final String reason;
        private final String displayPath;

        private QuickItem(String path, String reason, String displayPath) {
            this.path = path;
            this.reason = reason;
            this.displayPath = displayPath;
        }
    }
}
