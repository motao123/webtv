package com.fongmi.android.tv.bean;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class EpgReminderRecord {

    @PrimaryKey(autoGenerate = true)
    private Long id;
    private String channelName;
    private String programTitle;
    private String programStart;
    private long triggerAtMillis;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getChannelName() { return channelName; }
    public void setChannelName(String v) { this.channelName = v; }
    public String getProgramTitle() { return programTitle; }
    public void setProgramTitle(String v) { this.programTitle = v; }
    public String getProgramStart() { return programStart; }
    public void setProgramStart(String v) { this.programStart = v; }
    public long getTriggerAtMillis() { return triggerAtMillis; }
    public void setTriggerAtMillis(long v) { this.triggerAtMillis = v; }
}
