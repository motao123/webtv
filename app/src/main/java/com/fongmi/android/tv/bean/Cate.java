package com.fongmi.android.tv.bean;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

public class Cate implements Parcelable {

    @SerializedName("land")
    private Integer land;
    @SerializedName("circle")
    private Integer circle;
    @SerializedName("ratio")
    private Float ratio;

    protected Cate(Parcel in) {
        this.land = (Integer) in.readValue(Integer.class.getClassLoader());
        this.circle = (Integer) in.readValue(Integer.class.getClassLoader());
        this.ratio = (Float) in.readValue(Float.class.getClassLoader());
    }

    public int getLand() {
        return land == null ? 0 : land;
    }

    public int getCircle() {
        return circle == null ? 0 : circle;
    }

    public float getRatio() {
        return ratio == null ? 0 : ratio;
    }

    public Style getStyle() {
        return Style.get(getLand(), getCircle(), getRatio());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(this.land);
        dest.writeValue(this.circle);
        dest.writeValue(this.ratio);
    }

    public static final Creator<Cate> CREATOR = new Creator<>() {
        @Override
        public Cate createFromParcel(Parcel source) {
            return new Cate(source);
        }

        @Override
        public Cate[] newArray(int size) {
            return new Cate[size];
        }
    };
}
