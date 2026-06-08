package com.fongmi.android.tv.ui.custom;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.AttributeSet;
import android.view.KeyEvent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.bassaer.library.MDColor;

import java.util.List;

public class CustomMic extends AppCompatImageView {

    private ActivityResultLauncher<Intent> mLauncher;
    private CustomTextListener mListener;
    private SpeechRecognizer mRecognizer;
    private FragmentActivity mActivity;
    private boolean mAvailable;
    private boolean mListen;

    public CustomMic(@NonNull Context context) {
        super(context);
    }

    public CustomMic(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    private boolean isAvailable() {
        return mAvailable;
    }

    private boolean isListen() {
        return mListen;
    }

    private Intent getIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        return intent;
    }

    public void setListener(FragmentActivity activity, CustomTextListener listener) {
        mActivity = activity;
        mListener = listener;
        mListener.setDone(() -> updateUI(false));
        mAvailable = SpeechRecognizer.isRecognitionAvailable(activity);
        initSpeech();
    }

    private void initSpeech() {
        if (isAvailable()) initRecognizer();
        else if (hasResolveActivity()) initLauncher();
        else setVisibility(GONE);
    }

    private boolean hasResolveActivity() {
        return getIntent().resolveActivity(mActivity.getPackageManager()) != null;
    }

    private void initRecognizer() {
        if (mRecognizer == null) mRecognizer = SpeechRecognizer.createSpeechRecognizer(mActivity);
        mRecognizer.setRecognitionListener(mListener);
    }

    private void initLauncher() {
        mLauncher = mActivity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
            List<String> texts = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (texts != null && !texts.isEmpty()) mListener.onResults(texts.get(0));
        });
    }

    public void start() {
        if (mActivity == null) return;
        if (isAvailable()) startRecognizer();
        else launchIntent();
    }

    private void startRecognizer() {
        if (mRecognizer == null) return;
        PermissionUtil.requestAudio(mActivity, allGranted -> {
            if (allGranted) startListening();
        });
    }

    private void startListening() {
        try {
            mRecognizer.startListening(getIntent());
            requestFocus();
            updateUI(true);
        } catch (Exception ignored) {
        }
    }

    private void launchIntent() {
        try {
            if (mLauncher == null) return;
            mLauncher.launch(getIntent());
        } catch (Exception ignored) {
        }
    }

    public void stop() {
        if (mRecognizer == null) return;
        mRecognizer.stopListening();
        updateUI(false);
    }

    public void destroy() {
        if (mRecognizer != null) {
            mRecognizer.destroy();
            mRecognizer = null;
        }
        if (mLauncher != null) {
            mLauncher.unregister();
            mLauncher = null;
        }
    }

    private void updateUI(boolean listening) {
        mListen = listening;
        if (listening) {
            startAnimation(ResUtil.getAnim(R.anim.flicker));
            setColorFilter(MDColor.RED_500, PorterDuff.Mode.SRC_IN);
        } else {
            clearAnimation();
            setColorFilter(MDColor.WHITE, PorterDuff.Mode.SRC_IN);
        }
    }

    private boolean onBackKey(KeyEvent event) {
        if (!isListen() || !KeyUtil.isBackKey(event)) return false;
        stop();
        return true;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus && isAvailable()) start();
        else if (!gainFocus) stop();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (onBackKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }
}
