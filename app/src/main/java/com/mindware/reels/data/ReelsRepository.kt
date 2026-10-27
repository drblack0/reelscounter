package com.mindware.reels.data

import com.mindware.reels.data.db.RangeTotals
import com.mindware.reels.data.db.ReelDao
import com.mindware.reels.data.db.ReelSessionEntity
import com.mindware.reels.data.db.ReelViewEntity
import com.mindware.reels.session.SessionAction
import com.mindware.reels.session.SessionConfig
import kotlinx.coroutines.flow.Flow

/**
 * Persists [SessionAction]s. Not thread-safe: call from one coroutine at a time (the service's single
 * consumer loop does).
 */
class ReelsRepository(
    private val dao: ReelDao,
    private val heartbeat: HeartbeatStore,
    private val config: SessionConfig = SessionConfig(),
) {
    private var sessionId: Long? = null
    private var viewId: Long? = null
    private var viewTs = 0L
    private var lastHeartbeatWrite = 0L

    suspend fun apply(actions: List<SessionAction>) {
        for (action in actions) when (action) {
            is SessionAction.SessionStarted -> {
                sessionId = dao.insertSession(ReelSessionEntity(startTs = action.startTs, endTs = null, reelCount = 0))
                viewId = null
            }
            is SessionAction.ReelCounted -> sessionId?.let { sid ->
                viewId = dao.insertView(ReelViewEntity(sessionId = sid, ts = action.ts, durationMs = action.durationMs))
                viewTs = action.ts
                dao.incrementReelCount(sid)
            }
            is SessionAction.ReelDuration -> viewId?.takeIf { viewTs == action.ts }?.let {
                dao.updateViewDuration(it, action.durationMs)
            }
            is SessionAction.SessionEnded -> {
                sessionId?.let { dao.closeSession(it, action.endTs) }
                sessionId = null
                viewId = null
            }
        }
    }

    /** Throttled: SharedPreferences writes are cheap but we see events many times a second. */
    fun noteActivity(ts: Long) {
        if (ts - lastHeartbeatWrite < HEARTBEAT_INTERVAL_MS) return
        lastHeartbeatWrite = ts
        heartbeat.write(ts)
    }

    /** Closes sessions left open by a crash/kill. Call once when the service starts. */
    suspend fun recoverDangling(now: Long) {
        val beat = heartbeat.read()
        for (s in dao.openSessions()) {
            dao.closeSession(s.id, recoveredEnd(s.startTs, beat, dao.lastViewEnd(s.id), now, config.idleTimeoutMs))
        }
    }

    fun observeSessions(from: Long, to: Long): Flow<List<ReelSessionEntity>> = dao.observeSessions(from, to)

    fun observeViewTimestamps(from: Long, to: Long): Flow<List<Long>> = dao.observeViewTimestamps(from, to)

    fun observeTotals(from: Long, to: Long, now: Long): Flow<RangeTotals> = dao.observeTotals(from, to, now)

    companion object {
        const val HEARTBEAT_INTERVAL_MS = 5_000L
    }
}

/**
 * Best guess of when a dangling session really ended: the last heartbeat (ignored if it predates the
 * session, i.e. it belongs to an older one), else the end of the last recorded reel, else the start.
 * Never later than one idle timeout after the last recorded reel, and never in the future.
 */
fun recoveredEnd(startTs: Long, heartbeat: Long?, lastViewEnd: Long?, now: Long, idleTimeoutMs: Long): Long {
    var end = heartbeat?.takeIf { it >= startTs } ?: lastViewEnd ?: startTs
    if (lastViewEnd != null) end = minOf(end, lastViewEnd + idleTimeoutMs)
    return end.coerceIn(startTs, maxOf(startTs, now))
}
