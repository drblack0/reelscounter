package com.mindware.reels.detection

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.mindware.reels.ReelsApp
import com.mindware.reels.overlay.OverlayController
import com.mindware.reels.overlay.formatPill
import com.mindware.reels.session.LiveState
import com.mindware.reels.session.SessionTracker
import com.mindware.reels.session.Signals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
    private var holdSince = 0L
    private val trailing = Runnable { process(pendingType) }

    private val signals = Channel<Signals>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlay: OverlayController? = null

    override fun onServiceConnected() {
        val container = (application as ReelsApp).container
        val repository = container.repository
        val live = container.live
        val settings = container.settings
        val pill = OverlayController(this).also { overlay = it }
        mainScope.launch {
            combine(live, settings.state) { l, s -> if (s.overlayEnabled) l else LiveState() }.collectLatest { state ->
                if (!state.active) {
                    pill.hide()
                } else {
                    // Re-render every second so the timer advances between reel changes.
                    while (true) {
                        pill.show(formatPill(state.reelCount, System.currentTimeMillis() - state.startedAt))
                        delay(1_000)
                    }
                }
            }
        }
        scope.launch {
            repository.recoverDangling(System.currentTimeMillis())
            val tracker = SessionTracker(settings.state.value.sessionConfig())
            launch { settings.state.collect { tracker.config = it.sessionConfig() } }
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
                live.value = LiveState(tracker.needsTick, tracker.reelCount, tracker.startedAt)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg != config.packageName) {
            // Instagram gets no events once we leave it, so a foreground switch is our only end signal.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !isTransientWindow(pkg)) {
                signals.trySend(Signals(System.currentTimeMillis(), false, false, null))
            }
            return
        }
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
            signals.trySend(withSheetHold(res))
            if (!DebugLog.enabled.value) return
            val d = res.diagnostics
            val s = res.signals
            val summary = "reels=${s.reelsVisible} id=${s.reelId} viewer=${d.viewerIdMatch} item=${d.itemIdMatch} " +
                "desc=${d.descMatched} pager=${d.pagerFound} tabs=${d.tabBarVisible} nodes=${d.nodesVisited}"
            // Only log when something changed, to keep the log readable.
            if (summary != lastSummary) {
                lastSummary = summary
                DebugLog.add("${AccessibilityEvent.eventTypeToString(eventType)} $summary")
                if (DebugLog.dumpTree.value) DebugLog.add("TREE\n" + NodeDump.dump(r))
            }
        }
    }

    /**
     * Opening comments (or any sheet) makes Instagram's active window lack both the Reels viewer and the
     * bottom bar. That is "unknown", not "left Reels": keep the running session alive for a while. A real
     * exit to Home/another tab shows the bar and still ends the session immediately.
     */
    private fun withSheetHold(res: InstagramReelsDetector.Result): Signals {
        val s = res.signals
        val unknown = s.instagramForeground && !s.reelsVisible && !res.diagnostics.tabBarVisible
        if (!unknown || !(application as ReelsApp).container.live.value.active) {
            holdSince = 0
            return s
        }
        if (holdSince == 0L) holdSince = s.ts
        return if (s.ts - holdSince < SHEET_HOLD_MS) Signals(s.ts, true, true, null) else s
    }

    /** System UI, our own overlay and the keyboard open windows without leaving Instagram. */
    private fun isTransientWindow(pkg: String): Boolean =
        pkg == packageName || pkg == "com.android.systemui" ||
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.any { it.packageName == pkg }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(trailing)
        scope.cancel()
        mainScope.cancel()
        overlay?.hide()
        super.onDestroy()
    }

    private companion object {
        const val THROTTLE_MS = 120L
        const val TICK_MS = 1_000L
        const val SHEET_HOLD_MS = 120_000L
    }
}
