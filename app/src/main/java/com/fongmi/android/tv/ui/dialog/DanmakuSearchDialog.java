package com.fongmi.android.tv.ui.dialog;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.databinding.DialogDanmakuSearchBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.ui.adapter.DanmakuAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public final class DanmakuSearchDialog extends BaseBottomSheetDialog implements DanmakuAdapter.OnClickListener, Callback {

    private final DanmakuAdapter adapter;
    private DialogDanmakuSearchBinding binding;
    private PlayerManager player;

    public static DanmakuSearchDialog create() {
        return new DanmakuSearchDialog();
    }

    public DanmakuSearchDialog() {
        this.adapter = new DanmakuAdapter(this);
    }

    public DanmakuSearchDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof DanmakuSearchDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDanmakuSearchBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        setKeyword(player.getMetadata().title);
        Util.showKeyboard(binding.keyword);
    }

    @Override
    protected void initEvent() {
        binding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH && !binding.keyword.getText().toString().trim().isEmpty()) search();
            return true;
        });
        binding.keyword.setOnKeyListener((view, keyCode, event) -> {
            if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event) && binding.recycler.getVisibility() == VISIBLE) return binding.recycler.requestFocus();
            return false;
        });
    }

    @Override
    public void onItemClick(Danmaku item) {
        player.setDanmaku(item.isSelected() ? Danmaku.empty() : item);
        dismiss();
    }

    private void setKeyword(CharSequence text) {
        binding.keyword.setText(text);
        binding.keyword.setSelection(text.length());
    }

    private void showProgress() {
        binding.recycler.setVisibility(GONE);
        binding.progress.setVisibility(VISIBLE);
    }

    private void hideProgress(boolean empty) {
        binding.progress.setVisibility(GONE);
        binding.recycler.setVisibility(empty ? GONE : VISIBLE);
    }

    private void search() {
        showProgress();
        adapter.clear();
        Util.hideKeyboard(binding.keyword);
        DanmakuApi.newCall(binding.keyword.getText().toString().trim(), player.getMetadata().artist.toString().trim()).enqueue(this);
    }

    private void onSuccess(List<Danmaku> items) {
        adapter.addAll(items);
        hideProgress(items.isEmpty());
        binding.recycler.requestFocus();
    }

    private void onError(Exception e) {
        hideProgress(true);
        Notify.show(e.getMessage());
    }

    @Override
    public void onResponse(@NonNull Call call, @NonNull Response response) {
        try {
            List<Danmaku> items = Danmaku.arrayFrom(response.body().string());
            if (items.isEmpty()) throw new Exception(ResUtil.getString(R.string.error_empty));
            else App.post(() -> onSuccess(items));
        } catch (Exception e) {
            App.post(() -> onError(e));
        }
    }

    @Override
    public void onFailure(@NonNull Call call, @NonNull IOException e) {
        App.post(() -> onError(e));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        DanmakuApi.cancel();
    }
}