package com.fongmi.android.tv.impl;

import androidx.annotation.NonNull;

import com.permissionx.guolindev.callback.RequestCallback;
import com.permissionx.guolindev.request.ChainTask;
import com.permissionx.guolindev.request.ExplainScope;
import com.permissionx.guolindev.request.ForwardScope;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class PermissionCallback implements RequestCallback, ChainTask {

    private Consumer<Boolean> result;

    public PermissionCallback() {
    }

    public PermissionCallback(Consumer<Boolean> result) {
        this.result = Objects.requireNonNull(result);
    }

    @Override
    public void onResult(boolean allGranted, @NonNull List<String> grantedList, @NonNull List<String> deniedList) {
        if (result != null) result.accept(allGranted);
    }

    @Override
    public void finish() {
        if (result != null) result.accept(true);
    }

    @Override
    public ExplainScope getExplainScope() {
        return null;
    }

    @Override
    public ForwardScope getForwardScope() {
        return null;
    }

    @Override
    public void request() {
    }

    @Override
    public void requestAgain(List<String> permissions) {
    }
}
