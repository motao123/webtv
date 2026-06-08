package com.fongmi.android.tv.server.process;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.bean.drive.DriveCheckRequest;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.impl.Process;
import com.fongmi.android.tv.service.DriveCheckService;
import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.JsonObject;

import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Response;

public class DriveCheck implements Process {

    @Override
    public boolean isRequest(IHTTPSession session, String url) {
        return url.startsWith("/pan/check");
    }

    @Override
    public Response doResponse(IHTTPSession session, String url, Map<String, String> files) {
        if (session.getMethod() == NanoHTTPD.Method.OPTIONS) return cors(json(Response.Status.NO_CONTENT, ""), session);
        if (session.getMethod() != NanoHTTPD.Method.POST) return cors(error(Response.Status.METHOD_NOT_ALLOWED, 405, "只支持 POST"), session);
        if (!Setting.isDriveCheck()) return cors(error(Response.Status.FORBIDDEN, 403, "网盘检测未开启"), session);
        try {
            String body = files.get("postData");
            if (TextUtils.isEmpty(body)) body = session.getParms().get("body");
            DriveCheckRequest request = App.gson().fromJson(body, DriveCheckRequest.class);
            if (request == null || request.getItems().isEmpty()) return cors(error(Response.Status.BAD_REQUEST, 400, "items不能为空"), session);
            SpiderDebug.log("pan-check", "http /pan/check count=%s", request.getItems().size());
            return cors(json(Response.Status.OK, App.gson().toJson(DriveCheckService.get().check(request.getItems()))), session);
        } catch (Throwable e) {
            SpiderDebug.log("pan-check", e);
            return cors(error(Response.Status.INTERNAL_ERROR, 500, e.getMessage()), session);
        }
    }

    private Response error(Response.Status status, int code, String message) {
        JsonObject object = new JsonObject();
        object.addProperty("code", code);
        object.addProperty("message", TextUtils.isEmpty(message) ? "检测失败" : message);
        return json(status, object.toString());
    }

    private Response json(Response.Status status, String text) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json; charset=utf-8", text);
    }

    private Response cors(Response response, IHTTPSession session) {
        String origin = session.getHeaders().get("origin");
        response.addHeader("Access-Control-Allow-Origin", TextUtils.isEmpty(origin) ? "*" : origin);
        response.addHeader("Access-Control-Allow-Methods", "POST,OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "*");
        response.addHeader("Access-Control-Expose-Headers", "*");
        response.addHeader("Access-Control-Max-Age", "86400");
        return response;
    }
}
