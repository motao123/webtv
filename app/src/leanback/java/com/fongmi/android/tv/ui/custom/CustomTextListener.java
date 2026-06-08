package com.fongmi.android.tv.ui.custom;

import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;

import java.util.Collections;
import java.util.List;

public abstract class CustomTextListener implements TextWatcher, RecognitionListener {

    private Runnable done;

    public void setDone(Runnable done) {
        this.done = done;
    }

    private void done() {
        if (done != null) done.run();
    }

    private String parse(Bundle bundle) {
        List<String> texts = bundle == null ? Collections.emptyList() : bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (texts == null || texts.isEmpty()) return "";
        return texts.get(0).trim();
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onRmsChanged(float rmsdB) {
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
    }

    @Override
    public void onError(int error) {
        done();
        onResults("");
    }

    @Override
    public void onResults(Bundle results) {
        done();
        onResults(parse(results));
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    public void onResults(String result) {
    }
}
