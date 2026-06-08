package com.fongmi.android.tv.bean.drive;

import android.text.TextUtils;

import com.google.gson.annotations.SerializedName;

import java.util.Locale;

public class DriveCheckItem {

    @SerializedName("type")
    private String diskType;
    @SerializedName("url")
    private String url;
    @SerializedName("password")
    private String password;

    public String getDiskType() {
        String value = TextUtils.isEmpty(diskType) ? "" : diskType.trim().toLowerCase(Locale.ROOT);
        if ("alipan".equals(value) || "ali".equals(value)) return "aliyun";
        if ("123pan".equals(value)) return "123";
        if ("caiyun".equals(value) || "139".equals(value)) return "mobile";
        return value;
    }

    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : url.trim();
    }

    public String getPassword() {
        return TextUtils.isEmpty(password) ? "" : password.trim();
    }
}
