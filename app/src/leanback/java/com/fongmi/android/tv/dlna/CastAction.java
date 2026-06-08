package com.fongmi.android.tv.dlna;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import com.fongmi.android.tv.bean.Result;

import java.util.HashMap;
import java.util.Map;

public class CastAction implements Parcelable {

    public static final String KEY_EXTRA = "cast_action";

    private final String currentURI;
    private final String currentURIMetaData;
    private final Map<String, String> headers;

    public CastAction(String currentURI, String currentURIMetaData, Map<String, String> headers) {
        this.currentURI = currentURI != null ? currentURI : "";
        this.currentURIMetaData = currentURIMetaData != null ? currentURIMetaData : "";
        this.headers = headers != null ? headers : new HashMap<>();
    }

    protected CastAction(Parcel in) {
        currentURI = in.readString();
        currentURIMetaData = in.readString();
        Bundle bundle = in.readBundle(getClass().getClassLoader());
        headers = new HashMap<>();
        if (bundle != null) {
            for (String key : bundle.keySet()) {
                headers.put(key, bundle.getString(key));
            }
        }
    }

    public String getCurrentURI() {
        return currentURI;
    }

    public String getCurrentURIMetaData() {
        return currentURIMetaData;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Result result() {
        Result result = new Result();
        result.setUrl(getCurrentURI());
        result.setHeader(getHeaders());
        return result;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(currentURI);
        dest.writeString(currentURIMetaData);
        Bundle bundle = new Bundle();
        headers.forEach(bundle::putString);
        dest.writeBundle(bundle);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<CastAction> CREATOR = new Creator<>() {
        @Override
        public CastAction createFromParcel(Parcel in) {
            return new CastAction(in);
        }

        @Override
        public CastAction[] newArray(int size) {
            return new CastAction[size];
        }
    };
}
