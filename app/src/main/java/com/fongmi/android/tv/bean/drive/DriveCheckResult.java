package com.fongmi.android.tv.bean.drive;

import com.google.gson.annotations.SerializedName;

public class DriveCheckResult {

    @SerializedName("type")
    private String diskType;
    @SerializedName("url")
    private String url;
    @SerializedName("normalized_url")
    private String normalizedUrl;
    @SerializedName("state")
    private String state;
    @SerializedName("cache_hit")
    private boolean cacheHit;
    @SerializedName("checked_at")
    private long checkedAt;
    @SerializedName("expires_at")
    private long expiresAt;
    @SerializedName("summary")
    private String summary;

    public DriveCheckResult(String diskType, String url, String normalizedUrl, String state, boolean cacheHit, long checkedAt, long expiresAt, String summary) {
        this.diskType = diskType;
        this.url = url;
        this.normalizedUrl = normalizedUrl;
        this.state = state;
        this.cacheHit = cacheHit;
        this.checkedAt = checkedAt;
        this.expiresAt = expiresAt;
        this.summary = summary;
    }

    public String getDiskType() {
        return diskType;
    }

    public String getUrl() {
        return url;
    }

    public String getNormalizedUrl() {
        return normalizedUrl;
    }

    public String getState() {
        return state;
    }

    public boolean isCacheHit() {
        return cacheHit;
    }

    public long getCheckedAt() {
        return checkedAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public String getSummary() {
        return summary;
    }

    public DriveCheckResult cacheHit() {
        return new DriveCheckResult(diskType, url, normalizedUrl, state, true, checkedAt, expiresAt, summary);
    }
}
