package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogLinkBinding;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class LinkDialog extends BaseAlertDialog {

    private DialogLinkBinding binding;

    public static void show(Fragment fragment) {
        new LinkDialog().show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogLinkBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.play).setView(getBinding().getRoot()).setPositiveButton(R.string.dialog_positive, this::onPositive).setNegativeButton(R.string.dialog_negative, null);
    }

    @Override
    protected void initView() {
        CharSequence text = Util.getClipText();
        binding.text.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Integer.MAX_VALUE)});
        if (!TextUtils.isEmpty(text)) binding.text.setText(Sniffer.getUrl(text.toString()));
    }

    @Override
    protected void initEvent() {
        binding.input.setEndIconOnClickListener(this::onChoose);
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive(null, 0);
            return true;
        });
    }

    private void onChoose(View view) {
        FileChooser.from(launcher).show();
    }

    private void onPositive(DialogInterface dialog, int which) {
        String text = binding.text.getText().toString().trim();
        if (!text.isEmpty()) VideoActivity.start(requireActivity(), text);
        dismiss();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        VideoActivity.file(requireActivity(), FileChooser.getPathFromUri(result.getData().getData()));
        dismiss();
    });
}