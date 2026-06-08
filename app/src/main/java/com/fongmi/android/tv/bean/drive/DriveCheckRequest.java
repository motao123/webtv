package com.fongmi.android.tv.bean.drive;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public class DriveCheckRequest {

    @SerializedName("items")
    private List<DriveCheckItem> items;
    @SerializedName("view_token")
    private String viewToken;

    public List<DriveCheckItem> getItems() {
        return items == null ? Collections.emptyList() : items;
    }

    public String getViewToken() {
        return viewToken;
    }
}
