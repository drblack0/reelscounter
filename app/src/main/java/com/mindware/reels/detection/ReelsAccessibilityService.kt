package com.mindware.reels.detection

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.mindware.reels.ReelsApp
import com.mindware.reels.session.SessionTracker
import com.mindware.reels.session.Signals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select

/**
 * Detects Reels and feeds [Signals] to a single consumer loop, which runs the pure [SessionTracker] and
 * persists its actions. The event thread only does the cheap tree walk.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReelsAccessibilityService : AccessibilityService() {
    private val detector = InstagramReelsDetector()
    private val config = InstagramConfig()
    private var lastRun = 0L
    private var lastSummary: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingType = 0
    private val trailing = Runnable { process(pendingType) }

    private val signals = Channel<Signals>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    override fun onServiceConnected() {
        val repository = (application as ReelsApp).container.repository
        scope.launch {
            repository.recoverDangling(System.currentTimeMillis())
            val tracker = SessionTracker()
            while (true) {
                // Wake for a tick only while the 1s threshold / idle timeout can still fire.
                val next: Signals? = if (tracker.needsTick) {
                    select {
                        signals.onReceive { it }
                        onTimeout(TICK_MS) { null }
                    }
                } else {
                    signals.receive()
                }
                val now = System.currentTimeMillis()
                val actions = if (next != null) tracker.onSignals(next) else tracker.tick(now)
                repository.apply(actions)
                if (tracker.needsTick) repository.noteActivity(now)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() != config.packageName) return
        val now = SystemClock.elapsedRealtime()
        // Throttle, but always run once more after the burst settles: a dropped last event would
        // otherwise leave us looking at a mid-transition tree (e.g. a pager still sliding).
        handler.removeCallbacks(trailing)
        pendingType = event.eventType
        if (now - lastRun < THROTTLE_MS) {
            handler.postDelayed(trailing, THROTTLE_MS)
            return
        }
        process(event.eventType)
    }

    private fun process(eventType: Int) {
        lastRun = SystemClock.elapsedRealtime()
        val root = rootInActiveWindow ?: return
        root.use { r ->
            val res = detector.detect(r, System.currentTimeMillis())
            signals.trySend(res.signals)
            if (!DebugLog.enabled.value) return
            val d = res.diagnostics
            val s = res.signals
            val summary = "reels=${s.reelsVisible} id=${s.reelId} viewer=${d.viewerIdMatch} item=${d.itemIdMatch} " +
                "desc=${d.descMatched} pager=${d.pagerFound} nodes=${d.nodesVisited}"
            // Only log when something changed, to keep the log readable.
            if (summary != lastSummary) {
                lastSummary = summary
                DebugLog.add("${AccessibilityEvent.eventTypeToString(eventType)} $summary")
                if (DebugLog.dumpTree.value) DebugLog.add("TREE\n" + NodeDump.dump(r))
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(trailing)
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val THROTTLE_MS = 120L
        const val TICK_MS = 1_000L
    }
}
