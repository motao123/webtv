package com.fongmi.android.tv.dlna;

import org.jupnp.model.message.StreamRequestMessage;
import org.jupnp.model.message.StreamResponseMessage;
import org.jupnp.model.message.UpnpHeaders;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.transport.spi.AbstractStreamClient;
import org.jupnp.transport.spi.AbstractStreamClientConfiguration;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttpStreamClient extends AbstractStreamClient<OkHttpStreamClient.Configuration, Call> {

    private final Configuration configuration;
    private final OkHttpClient httpClient;

    public OkHttpStreamClient(Configuration configuration) {
        this.configuration = configuration;
        int timeout = configuration.getTimeoutSeconds() + 5;
        this.httpClient = new OkHttpClient.Builder().connectTimeout(timeout, TimeUnit.SECONDS).readTimeout(timeout, TimeUnit.SECONDS).build();
    }

    private static boolean requiresBody(String method) {
        return "POST".equals(method) || "NOTIFY".equals(method) || "PUT".equals(method);
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    protected Call createRequest(StreamRequestMessage requestMessage) {
        String method = requestMessage.getOperation().getHttpMethodName();
        Request.Builder builder = new Request.Builder().url(requestMessage.getOperation().getURI().toString()).method(method, buildRequestBody(requestMessage, method));
        requestMessage.getHeaders().forEach((key, value) -> value.stream().filter(Objects::nonNull).forEach(v -> builder.addHeader(key, v)));
        if (requestMessage.getHeaders().get("user-agent") == null) builder.header("User-Agent", configuration.getUserAgentValue(requestMessage.getUdaMajorVersion(), requestMessage.getUdaMinorVersion()));
        return httpClient.newCall(builder.build());
    }

    private RequestBody buildRequestBody(StreamRequestMessage requestMessage, String method) {
        if (requestMessage.hasBody()) {
            byte[] bytes = requestMessage.getBodyBytes();
            if (bytes != null && bytes.length > 0) {
                List<String> ct = requestMessage.getHeaders().get("content-type");
                MediaType mediaType = ct != null && !ct.isEmpty() ? MediaType.parse(ct.get(0)) : null;
                return RequestBody.create(bytes, mediaType);
            }
        }
        return requiresBody(method) ? RequestBody.create(new byte[0]) : null;
    }

    @Override
    protected Callable<StreamResponseMessage> createCallable(StreamRequestMessage requestMessage, Call call) {
        return () -> {
            try (Response response = call.execute()) {
                StreamResponseMessage responseMessage = new StreamResponseMessage(new UpnpResponse(response.code(), response.message()));
                UpnpHeaders upnpHeaders = new UpnpHeaders();
                response.headers().names().forEach(name -> response.headers(name).forEach(value -> upnpHeaders.add(name, value)));
                responseMessage.setHeaders(upnpHeaders);
                byte[] bytes = response.body().bytes();
                if (bytes.length > 0) responseMessage.setBodyCharacters(bytes);
                return responseMessage;
            }
        };
    }

    @Override
    protected void abort(Call call) {
        call.cancel();
    }

    @Override
    protected boolean logExecutionException(Throwable t) {
        return false;
    }

    @Override
    public void stop() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    public static class Configuration extends AbstractStreamClientConfiguration {

        public Configuration(ExecutorService executorService) {
            super(executorService);
        }
    }
}
