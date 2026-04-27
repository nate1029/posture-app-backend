package com.example.neckguard.data.local;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "posture_logs")
public class PostureLog {
    @PrimaryKey
    public long timestampStartMs;
    public long durationMs;
    public long healthyMs;
    public long slouchedMs;
    public boolean isSynced;

    public PostureLog(long timestampStartMs, long durationMs, long healthyMs, long slouchedMs, boolean isSynced) {
        this.timestampStartMs = timestampStartMs;
        this.durationMs = durationMs;
        this.healthyMs = healthyMs;
        this.slouchedMs = slouchedMs;
        this.isSynced = isSynced;
    }
}
