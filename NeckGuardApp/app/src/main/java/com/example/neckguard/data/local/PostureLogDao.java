package com.example.neckguard.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.lifecycle.LiveData;
import java.util.List;

@Dao
public interface PostureLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLog(PostureLog log);

    @Query("SELECT * FROM posture_logs ORDER BY timestampStartMs DESC")
    LiveData<List<PostureLog>> getAllLogs();

    @Query("SELECT * FROM posture_logs WHERE isSynced = 0")
    List<PostureLog> getUnsyncedLogs();

    @Query("UPDATE posture_logs SET isSynced = 1 WHERE timestampStartMs IN (:timestamps)")
    void markAsSynced(List<Long> timestamps);
    
    @Query("SELECT SUM(durationMs) FROM posture_logs WHERE timestampStartMs >= :sinceMs")
    LiveData<Long> getCumulativeUsageSince(long sinceMs);

    @Query("SELECT SUM(slouchedMs) FROM posture_logs WHERE timestampStartMs >= :sinceMs")
    LiveData<Long> getCumulativeSlouchedSince(long sinceMs);

    /**
     * Blocking range queries used to compute the previous day's score on
     * day-rollover. SUM() over zero rows returns NULL, hence the boxed
     * {@link Long} return type — callers should treat null as "no data".
     */
    @Query("SELECT SUM(durationMs) FROM posture_logs WHERE timestampStartMs BETWEEN :startMs AND :endMs")
    Long getCumulativeUsageBetween(long startMs, long endMs);

    @Query("SELECT SUM(slouchedMs) FROM posture_logs WHERE timestampStartMs BETWEEN :startMs AND :endMs")
    Long getCumulativeSlouchedBetween(long startMs, long endMs);
}
