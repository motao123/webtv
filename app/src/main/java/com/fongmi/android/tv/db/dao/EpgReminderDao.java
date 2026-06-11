package com.fongmi.android.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.fongmi.android.tv.bean.EpgReminderRecord;

import java.util.List;

@Dao
public abstract class EpgReminderDao {

    @Query("SELECT * FROM EpgReminderRecord")
    public abstract List<EpgReminderRecord> findAll();

    @Query("SELECT * FROM EpgReminderRecord WHERE triggerAtMillis > :now")
    public abstract List<EpgReminderRecord> findPending(long now);

    @Insert
    public abstract void insert(EpgReminderRecord record);

    @Query("DELETE FROM EpgReminderRecord WHERE programTitle = :title")
    public abstract void delete(String title);

    @Query("DELETE FROM EpgReminderRecord WHERE triggerAtMillis <= :now")
    public abstract void deleteExpired(long now);

    @Query("DELETE FROM EpgReminderRecord")
    public abstract void clear();
}
