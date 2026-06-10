package com.fongmi.android.tv.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.fongmi.android.tv.utils.EpgReminder;
import com.fongmi.android.tv.utils.Notify;

public class EpgReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String channelName = intent.getStringExtra("channelName");
        String programTitle = intent.getStringExtra("programTitle");
        String programStart = intent.getStringExtra("programStart");
        if (channelName == null || programTitle == null) return;
        Notify.show(EpgReminder.buildNotification(channelName, programTitle, programStart != null ? programStart : ""));
    }
}
