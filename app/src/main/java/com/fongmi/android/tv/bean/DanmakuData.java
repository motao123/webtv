package com.fongmi.android.tv.bean;

import android.graphics.Color;
import android.text.TextUtils;

import com.github.catvod.utils.Trans;

import java.util.regex.Matcher;

public class DanmakuData {

    private int type;
    private int color;
    private int shadow;
    private long time;
    private float size;
    private String text;

    public DanmakuData(Matcher matcher, float density) throws Exception {
        this.param(matcher.group(1), density);
        this.text = matcher.group(2);
        this.trans();
    }

    private void param(String param, float density) throws Exception {
        String[] params = param.split(",");
        if (params.length < 4) throw new Exception();
        this.type = Integer.parseInt(params[1]);
        this.time = (long) (Float.parseFloat(params[0]) * 1000);
        this.size = Float.parseFloat(params[2]) * (density - 0.6f);
        this.color = (int) ((0x00000000FF000000L | Long.parseLong(params[3])) & 0x00000000FFFFFFFFL);
        this.shadow = color <= Color.BLACK ? Color.WHITE : Color.BLACK;
    }

    public int getType() {
        return type;
    }

    public int getShadow() {
        return shadow;
    }

    public int getColor() {
        return color;
    }

    public long getTime() {
        return time;
    }

    public float getSize() {
        return size;
    }

    public String getText() {
        return TextUtils.isEmpty(text) ? "" : text.replace("&amp;", "&").replace("&quot;", "\"").replace("&gt;", ">").replace("&lt;", "<");
    }

    public void trans() {
        if (Trans.pass()) return;
        this.text = Trans.s2t(text);
    }
}
