package com.mindware.reels.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelDao {
    @Insert
    suspend fun insertSession(session: ReelSessionEntity): Long

    @Insert
    suspend fun insertView(view: ReelViewEntity): Long

    @Query("UPDATE reel_session SET reel_count = reel_count + 1 WHERE id = :id")
    suspend fun incrementReelCount(id: Long)

    @Query("UPDATE reel_view SET duration_ms = :durationMs WHERE id = :id")
    suspend fun updateViewDuration(id: Long, durationMs: Long)

    @Query("UPDATE reel_session SET end_ts = :endTs WHERE id = :id")
    suspend fun closeSession(id: Long, endTs: Long)

    @Query("SELECT * FROM reel_session WHERE end_ts IS NULL")
    suspend fun openSessions(): List<ReelSessionEntity>

    /** Best-effort end of a dangling session: when its last recorded reel stopped. */
    @Query("SELECT MAX(ts + duration_ms) FROM reel_view WHERE session_id = :sessionId")
    suspend fun lastViewEnd(sessionId: Long): Long?

    @Query("SELECT * FROM reel_session WHERE start_ts >= :from AND start_ts < :to ORDER BY start_ts")
    fun observeSessions(from: Long, to: Long): Flow<List<ReelSessionEntity>>

    @Query("SELECT ts FROM reel_view WHERE ts >= :from AND ts < :to ORDER BY ts")
    fun observeViewTimestamps(from: Long, to: Long): Flow<List<Long>>

    @Query("SELECT * FROM reel_view WHERE session_id = :sessionId ORDER BY ts")
    fun observeViews(sessionId: Long): Flow<List<ReelViewEntity>>

    /**
     * Sessions are attributed to the day they started in. An open session counts up to [now].
     * [now] is a parameter (not SQL time) so results are deterministic in tests.
     */
    @Query(
        """
        SELECT COUNT(*) AS sessionCount,
               COALESCE(SUM(reel_count), 0) AS reelCount,
               COALESCE(SUM(COALESCE(end_ts, :now) - start_ts), 0) AS totalMs,
               COALESCE(MAX(COALESCE(end_ts, :now) - start_ts), 0) AS longestMs
        FROM reel_session WHERE start_ts >= :from AND start_ts < :to
        """,
    )
    fun observeTotals(from: Long, to: Long, now: Long): Flow<RangeTotals>

    @Query("DELETE FROM reel_session")
    suspend fun deleteAll()
}
