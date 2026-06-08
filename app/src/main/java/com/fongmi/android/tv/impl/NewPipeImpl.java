package com.fongmi.android.tv.impl;

import androidx.annotation.NonNull;

import com.github.catvod.net.OkHttp;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public final class NewPipeImpl extends Downloader {

    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

    private static class Loader {
        static volatile NewPipeImpl INSTANCE = new NewPipeImpl();
    }

    public static NewPipeImpl get() {
        return Loader.INSTANCE;
    }

    @Override
    public Response execute(@NonNull Request request) throws IOException, ReCaptchaException {
        okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(request.url()).method(request.httpMethod(), body(request)).header("User-Agent", USER_AGENT);
        for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
            builder.removeHeader(entry.getKey());
            for (String value : entry.getValue()) builder.addHeader(entry.getKey(), value);
        }
        try (okhttp3.Response response = OkHttp.client().newCall(builder.build()).execute(); ResponseBody body = response.body()) {
            if (response.code() == 429) throw new ReCaptchaException("reCaptcha Challenge requested", request.url());
            return new Response(response.code(), response.message(), response.headers().toMultimap(), body.string(), response.request().url().toString());
        }
    }

    private static RequestBody body(Request request) {
        byte[] data = request.dataToSend();
        return data == null ? null : RequestBody.create(data);
    }
}
