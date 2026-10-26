package com.mindware.reels.session

/** Tunable rules. Keep every number that defines "what counts" here. */
data class SessionConfig(
    /** A reel counts only if it stays on screen this long (filters quick flicks). */
    val minReelMs: Long = 1_000,
    /** A session ends after this long with no detection signal while Reels is visible. */
    val idleTimeoutMs: Long = 30_000,
)

/** What the detector observed at [ts] (UTC millis). [reelId] is opaque; null means "unknown right now". */
data class Signals(
    val ts: Long,
    val instagramForeground: Boolean,
    val reelsVisible: Boolean,
    val reelId: String?,
)

sealed interface SessionAction {
    /** Emitted lazily, right before the first counted reel, so sessions with no reels never exist. */
    data class SessionStarted(val startTs: Long) : SessionAction

    /** A reel passed the minimum on-screen time. [ts] is when it appeared. */
    data class ReelCounted(val ts: Long, val durationMs: Long) : SessionAction

    /** Final on-screen time for the reel that appeared at [ts]. */
    data class ReelDuration(val ts: Long, val durationMs: Long) : SessionAction

    data class SessionEnded(val endTs: Long) : SessionAction
}

/**
 * Pure session rules: no Android, no clock. Feed it [onSignals] for detector events and [tick] on a
 * timer (needed because idle timeout and the 1s threshold fire when no events arrive).
 */
class SessionTracker(private val config: SessionConfig = SessionConfig()) {
    // PENDING: on screen, not yet long enough. COUNTED: counted. IGNORED: already accounted for in an
    // earlier (idle-ended) session, so the same reel still on screen is not counted twice.
    private enum class ReelState { PENDING, COUNTED, IGNORED }

    private var active = false
    private var opened = false
    private var sessionStart = 0L
    private var lastActivity = 0L
    private var reelId: String? = null
    private var reelSince = 0L
    private var reelState = ReelState.PENDING

    var reelCount = 0
        private set
    val isActive: Boolean get() = active && opened

    fun onSignals(signals: Signals): List<SessionAction> {
        val out = mutableListOf<SessionAction>()
        val ts = if (active) maxOf(signals.ts, lastActivity) else signals.ts

        // A late event (e.g. service was throttled) can reveal that the idle timeout already passed.
        if (active && ts - lastActivity >= config.idleTimeoutMs) endSession(lastActivity, keepReel = true, out)

        if (!signals.instagramForeground || !signals.reelsVisible) {
            if (active) endSession(ts, keepReel = false, out)
            return out
        }

        if (!active) {
            active = true
            opened = false
            sessionStart = ts
            reelCount = 0
        }
        lastActivity = ts

        val id = signals.reelId
        if (id != null && id != reelId) {
            finishReel(ts, out)
            reelId = id
            reelSince = ts
            reelState = ReelState.PENDING
        }
        promote(ts, out)
        return out
    }

    fun tick(now: Long): List<SessionAction> {
        val out = mutableListOf<SessionAction>()
        if (!active) return out
        if (now - lastActivity >= config.idleTimeoutMs) endSession(lastActivity, keepReel = true, out) else promote(now, out)
        return out
    }

    private fun promote(now: Long, out: MutableList<SessionAction>) {
        if (reelId == null || reelState != ReelState.PENDING) return
        val elapsed = now - reelSince
        if (elapsed < config.minReelMs) return
        if (!opened) {
            opened = true
            out += SessionAction.SessionStarted(sessionStart)
        }
        reelState = ReelState.COUNTED
        reelCount++
        out += SessionAction.ReelCounted(reelSince, elapsed)
    }

    private fun finishReel(endTs: Long, out: MutableList<SessionAction>) {
        if (reelId == null) return
        promote(endTs, out) // counts a reel that qualified but was never ticked
        if (reelState == ReelState.COUNTED) out += SessionAction.ReelDuration(reelSince, endTs - reelSince)
    }

    private fun endSession(endTs: Long, keepReel: Boolean, out: MutableList<SessionAction>) {
        finishReel(endTs, out)
        if (opened) out += SessionAction.SessionEnded(endTs)
        active = false
        opened = false
        if (keepReel && reelId != null) reelState = ReelState.IGNORED else reelId = null
    }
}
