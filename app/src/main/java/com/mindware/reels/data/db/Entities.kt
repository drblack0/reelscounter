package com.mindware.reels.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** All timestamps are UTC epoch millis; local days are derived at query time. */
@Entity(tableName = "reel_session")
data class ReelSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_ts") val startTs: Long,
    /** Null while the session is open. A non-null value after a crash means it was recovered. */
    @ColumnInfo(name = "end_ts") val endTs: Long?,
    @ColumnInfo(name = "reel_count") val reelCount: Int,
)

@Entity(
    tableName = "reel_view",
    foreignKeys = [
        ForeignKey(
            entity = ReelSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_id")],
)
data class ReelViewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    val ts: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
)

data class RangeTotals(
    val sessionCount: Int,
    val reelCount: Int,
    val totalMs: Long,
    val longestMs: Long,
)
