package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.web.CookieBridge;
import com.fongmi.android.tv.web.HeaderPolicy;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

public class WebResourceGateway implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/webResource");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        if (session.getMethod() == NanoHTTPD.Method.OPTIONS) return cors(NanoHTTPD.newFixedLengthResponse(Response.Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, ""), session);
        okhttp3.Response response = null;
        try {
            Map<String, String> params = session.getParms();
            String target = params.get("url");
            if (!isAllowed(target)) return cors(Nano.error(Response.Status.BAD_REQUEST, "Unsupported url"), session);
            Map<String, String> headers = HeaderPolicy.withDefaultUa(HeaderPolicy.parse(params.get("headers")));
            copyRange(session, headers);
            Request.Builder builder = new Request.Builder().url(target).headers(HeaderPolicy.of(headers));
            CookieBridge.apply(builder.build().url(), builder, "include".equals(params.get("credentials")), HeaderPolicy.hasCookie(headers));
            builder.method(getMethod(session), getBody(session, builder.build().headers(), files));
            Request request = builder.build();
            long start = System.currentTimeMillis();
            SpiderDebug.log("web-resource", "%s %s", request.method(), request.url());
            response = OkHttp.client().newCall(request).execute();
            SpiderDebug.log("web-resource", "%s %s -> %s in %sms", request.method(), request.url(), response.code(), System.currentTimeMillis() - start);
            CookieBridge.set(target, response.headers());
            return cors(toResponse(response), session);
        } catch (Throwable e) {
            if (response != null) response.close();
            SpiderDebug.log("web-resource", e);
            return cors(Nano.error(e.getMessage()), session);
        }
    }

    private boolean isAllowed(String url) {
        if (TextUtils.isEmpty(url) || (!url.startsWith("http://") && !url.startsWith("https://"))) return false;
        try {
            String host = URI.create(url).getHost();
            if (TextUtils.isEmpty(host)) return false;
            for (InetAddress address : InetAddress.getAllByName(host)) if (isPrivate(address)) return false;
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private boolean isPrivate(InetAddress address) {
        return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress();
    }

    private void copyRange(IHTTPSession session, Map<String, String> headers) {
        String range = session.getHeaders().get("range");
        if (!TextUtils.isEmpty(range)) headers.put("Range", range);
    }

    private String getMethod(IHTTPSession session) {
        String method = session.getMethod().name();
        return "OPTIONS".equals(method) ? "GET" : method;
    }

    private RequestBody getBody(IHTTPSession session, Headers headers, Map<String, String> files) {
        String method = getMethod(session);
        if ("GET".equals(method) || "HEAD".equals(method)) return null;
        String body = session.getParms().get("body");
        if (TextUtils.isEmpty(body)) body = files.get("postData");
        String contentType = headers.get("Content-Type");
        MediaType type = MediaType.parse(contentType == null ? "text/plain; charset=utf-8" : contentType);
        return RequestBody.create(body == null ? "" : body, type);
    }

    private Response toResponse(okhttp3.Response response) {
        InputStream stream = response.body().byteStream();
        String type = getContentType(response);
        Response.Status status = Response.Status.lookup(response.code());
        Response result = NanoHTTPD.newChunkedResponse(status == null ? Response.Status.OK : status, type, stream);
        for (String name : response.headers().names()) if (copyHeader(name)) for (String value : response.headers(name)) result.addHeader(name, value);
        return result;
    }

    private String getContentType(okhttp3.Response response) {
        MediaType type = response.body().contentType();
        return type == null ? NanoHTTPD.MIME_PLAINTEXT : type.toString();
    }

    private boolean copyHeader(String name) {
        return !"Connection".equalsIgnoreCase(name) && !"Transfer-Encoding".equalsIgnoreCase(name) && !"Keep-Alive".equalsIgnoreCase(name) && !"Content-Length".equalsIgnoreCase(name);
    }

    private Response cors(Response response, IHTTPSession session) {
        String origin = session.getHeaders().get("origin");
        response.addHeader("Access-Control-Allow-Origin", TextUtils.isEmpty(origin) ? "*" : origin);
        response.addHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,HEAD,OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "*");
        response.addHeader("Access-Control-Expose-Headers", "*");
        response.addHeader("Access-Control-Max-Age", "86400");
        return response;
    }
}
