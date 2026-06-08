package com.fongmi.android.tv.event;

import com.fongmi.android.tv.BuildConfig;

public final class ActionEvent {

    public static final String STOP = BuildConfig.APPLICATION_ID.concat(".stop");
    public static final String PREV = BuildConfig.APPLICATION_ID.concat(".prev");
    public static final String NEXT = BuildConfig.APPLICATION_ID.concat(".next");
    public static final String PLAY = BuildConfig.APPLICATION_ID.concat(".play");
    public static final String PAUSE = BuildConfig.APPLICATION_ID.concat(".pause");
    public static final String AUDIO = BuildConfig.APPLICATION_ID.concat(".audio");
    public static final String REPEAT = BuildConfig.APPLICATION_ID.concat(".repeat");
    public static final String REPLAY = BuildConfig.APPLICATION_ID.concat(".replay");

}
