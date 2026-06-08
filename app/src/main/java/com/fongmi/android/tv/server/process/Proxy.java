package com.fongmi.android.tv.server.process;

import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.github.catvod.crawler.SpiderDebug;

import java.io.InputStream;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class Proxy implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/proxy");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        try {
            Map<String, String> params = session.getParms();
            params.putAll(session.getHeaders());
            params.putAll(files);
            SpiderDebug.log("proxy", "request uri=%s method=%s do=%s keys=%s", url, session.getMethod(), params.get("do"), params.keySet());
            Object[] rs = BaseLoader.get().proxy(params);
            if (rs == null) {
                SpiderDebug.log("proxy", "response null do=%s uri=%s", params.get("do"), url);
                return Nano.error("Proxy response is null");
            }
            if (rs[0] instanceof Response) {
                SpiderDebug.log("proxy", "response object do=%s type=%s", params.get("do"), rs[0].getClass().getName());
                return (Response) rs[0];
            }
            SpiderDebug.log("proxy", "response do=%s status=%s mime=%s body=%s headerKeys=%s", params.get("do"), rs.length > 0 ? rs[0] : null, rs.length > 1 ? rs[1] : null, rs.length > 2 && rs[2] != null ? rs[2].getClass().getName() : null, rs.length > 3 && rs[3] instanceof Map ? ((Map<String, String>) rs[3]).keySet() : null);
            Response response = NanoHTTPD.newChunkedResponse(Status.lookup((Integer) rs[0]), (String) rs[1], (InputStream) rs[2]);
            if (rs.length > 3 && rs[3] != null) for (Map.Entry<String, String> entry : ((Map<String, String>) rs[3]).entrySet()) response.addHeader(entry.getKey(), entry.getValue());
            return response;
        } catch (Throwable e) {
            e.printStackTrace();
            SpiderDebug.log("proxy", e);
            return Nano.error(e.getMessage());
        }
    }
}
