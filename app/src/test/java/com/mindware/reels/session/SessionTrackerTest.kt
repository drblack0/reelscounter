package com.mindware.reels.session

import com.mindware.reels.session.SessionAction.ReelCounted
import com.mindware.reels.session.SessionAction.ReelDuration
import com.mindware.reels.session.SessionAction.SessionEnded
import com.mindware.reels.session.SessionAction.SessionStarted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTrackerTest {
    private val tracker = SessionTracker(SessionConfig(minReelMs = 1_000, idleTimeoutMs = 30_000))

    private fun reel(ts: Long, id: String?) = Signals(ts, instagramForeground = true, reelsVisible = true, reelId = id)
    private fun leave(ts: Long) = Signals(ts, instagramForeground = true, reelsVisible = false, reelId = null)
    private fun background(ts: Long) = Signals(ts, instagramForeground = false, reelsVisible = false, reelId = null)

    @Test fun `quick flick is not counted and creates no session`() {
        assertEquals(emptyList<SessionAction>(), tracker.onSignals(reel(0, "A")))
        assertEquals(emptyList<SessionAction>(), tracker.onSignals(reel(400, "B")))
        assertEquals(emptyList<SessionAction>(), tracker.onSignals(leave(800)))
        assertEquals(0, tracker.reelCount)
    }

    @Test fun `reel counts once it has been on screen long enough`() {
        tracker.onSignals(reel(0, "A"))
        val actions = tracker.onSignals(reel(1_000, "A"))
        assertEquals(listOf(SessionStarted(0), ReelCounted(0, 1_000)), actions)
    }

    @Test fun `tick counts a reel when no further events arrive`() {
        tracker.onSignals(reel(0, "A"))
        assertEquals(emptyList<SessionAction>(), tracker.tick(999))
        assertEquals(listOf(SessionStarted(0), ReelCounted(0, 1_000)), tracker.tick(1_000))
    }

    @Test fun `repeated events for the same reel do not double count`() {
        tracker.onSignals(reel(0, "A"))
        tracker.onSignals(reel(1_000, "A"))
        assertEquals(emptyList<SessionAction>(), tracker.onSignals(reel(2_000, "A")))
        assertEquals(emptyList<SessionAction>(), tracker.tick(2_500))
        assertEquals(1, tracker.reelCount)
    }

    @Test fun `scrolling to the next reel finalizes the previous duration`() {
        tracker.onSignals(reel(0, "A"))
        tracker.tick(1_000)
        val actions = tracker.onSignals(reel(5_000, "B"))
        assertEquals(listOf(ReelDuration(0, 5_000)), actions)
    }

    @Test fun `scrolling back to an earlier reel counts it again`() {
        tracker.onSignals(reel(0, "A"))
        tracker.onSignals(reel(2_000, "B"))
        tracker.onSignals(reel(4_000, "A"))
        tracker.onSignals(reel(6_000, "A"))
        assertEquals(3, tracker.reelCount)
    }

    @Test fun `unknown reel id keeps the current reel`() {
        tracker.onSignals(reel(0, "A"))
        tracker.onSignals(reel(500, null))
        tracker.onSignals(reel(1_200, "A"))
        assertEquals(1, tracker.reelCount)
    }

    @Test fun `leaving reels ends session at that moment`() {
        tracker.onSignals(reel(0, "A"))
        tracker.tick(1_000)
        val actions = tracker.onSignals(leave(8_000))
        assertEquals(listOf(ReelDuration(0, 8_000), SessionEnded(8_000)), actions)
        assertFalse(tracker.isActive)
    }

    @Test fun `backgrounding instagram ends session`() {
        tracker.onSignals(reel(0, "A"))
        tracker.tick(1_500)
        val actions = tracker.onSignals(background(3_000))
        assertEquals(listOf(ReelDuration(0, 3_000), SessionEnded(3_000)), actions)
    }

    @Test fun `idle timeout via tick ends session at last activity`() {
        tracker.onSignals(reel(0, "A"))
        tracker.onSignals(reel(2_000, "A"))
        val actions = tracker.tick(2_000 + 30_000)
        assertEquals(listOf(ReelDuration(0, 2_000), SessionEnded(2_000)), actions)
        assertFalse(tracker.isActive)
    }

    @Test fun `late event after idle closes old session then starts a new one`() {
        tracker.onSignals(reel(0, "A"))
        tracker.tick(1_000)
        // 60s of silence, then the user scrolls to B.
        tracker.onSignals(reel(61_000, "B"))
        val actions = tracker.tick(62_000)
        assertEquals(listOf(SessionStarted(61_000), ReelCounted(61_000, 1_000)), actions)
        assertEquals(1, tracker.reelCount) // counter restarts with the new session
    }

    @Test fun `same reel still on screen after idle is not counted again`() {
        tracker.onSignals(reel(0, "A"))
        tracker.tick(1_000)
        tracker.tick(31_000) // idle end
        assertFalse(tracker.isActive)
        tracker.onSignals(reel(40_000, "A"))
        assertEquals(emptyList<SessionAction>(), tracker.tick(45_000))
        assertEquals(0, tracker.reelCount)
    }

    @Test fun `returning to reels after leaving counts the visible reel again`() {
        tracker.onSignals(reel(0, "A"))
        tracker.tick(1_000)
        tracker.onSignals(leave(2_000))
        tracker.onSignals(reel(10_000, "A"))
        val actions = tracker.tick(11_000)
        assertTrue(actions.any { it is ReelCounted })
    }

    @Test fun `out of order timestamps are clamped`() {
        tracker.onSignals(reel(5_000, "A"))
        tracker.onSignals(reel(4_000, "A")) // clock went backwards
        val actions = tracker.tick(6_000)
        assertEquals(listOf(SessionStarted(5_000), ReelCounted(5_000, 1_000)), actions)
    }

    @Test fun `thresholds come from config`() {
        val t = SessionTracker(SessionConfig(minReelMs = 3_000, idleTimeoutMs = 10_000))
        t.onSignals(reel(0, "A"))
        assertEquals(emptyList<SessionAction>(), t.tick(2_999))
        assertTrue(t.tick(3_000).any { it is ReelCounted })
        assertTrue(t.tick(10_000).any { it is SessionEnded })
    }
}
