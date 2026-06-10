package com.fongmi.android.tv.server.process;

import static fi.iki.elonen.NanoHTTPD.MIME_HTML;
import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.github.catvod.utils.Asset;
import com.google.gson.JsonPrimitive;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class Parse implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/parse");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        try {
            Map<String, String> params = session.getParms();
            String jxs = escapeJs(params.get("jxs"));
            String targetUrl = escapeJs(params.get("url"));
            String html = String.format(Asset.read("parse.html"), jxs, targetUrl);
            return newFixedLengthResponse(Status.OK, MIME_HTML, html);
        } catch (Exception e) {
            return Nano.error(e.getMessage());
        }
    }

    private static String escapeJs(String value) {
        if (value == null) return "";
        String json = new JsonPrimitive(value).toString();
        return json.substring(1, json.length() - 1);
    }
}
