package com.example.neckguard.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "posture_logs")
data class PostureLog(
    @PrimaryKey val timestampStartMs: Long,
    val durationMs: Long,
    val healthyMs: Long,
    val slouchedMs: Long,
    val isSynced: Boolean = false
)

@Dao
interface PostureLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: PostureLog)

    @Query("SELECT * FROM posture_logs ORDER BY timestampStartMs DESC")
    fun getAllLogs(): Flow<List<PostureLog>>

    @Query("SELECT * FROM posture_logs WHERE isSynced = 0")
    suspend fun getUnsyncedLogs(): List<PostureLog>

    @Query("UPDATE posture_logs SET isSynced = 1 WHERE timestampStartMs IN (:timestamps)")
    suspend fun markAsSynced(timestamps: List<Long>)
    
    @Query("SELECT SUM(durationMs) FROM posture_logs WHERE timestampStartMs >= :sinceMs")
    fun getCumulativeUsageSince(sinceMs: Long): Flow<Long?>

    @Query("SELECT SUM(slouchedMs) FROM posture_logs WHERE timestampStartMs >= :sinceMs")
    fun getCumulativeSlouchedSince(sinceMs: Long): Flow<Long?>
}
