package com.mindware.reels.detection

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/** Signals only: M2 detects and logs; wiring into the tracker/DB happens in M3. */
class ReelsAccessibilityService : AccessibilityService() {
    private val detector = InstagramReelsDetector()
    private val config = InstagramConfig()
    private var lastRun = 0L
    private var lastSummary: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!DebugLog.enabled.value) return
        if (event.packageName?.toString() != config.packageName) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastRun < THROTTLE_MS) return
        lastRun = now

        val root = rootInActiveWindow ?: return
        root.use { r ->
            val res = detector.detect(r, System.currentTimeMillis())
            val d = res.diagnostics
            val s = res.signals
            val summary = "reels=${s.reelsVisible} id=${s.reelId} viewer=${d.viewerIdMatch} item=${d.itemIdMatch} " +
                "desc=${d.descMatched} pager=${d.pagerFound} nodes=${d.nodesVisited}"
            // Only log when something changed, to keep the log readable.
            if (summary != lastSummary) {
                lastSummary = summary
                DebugLog.add("${AccessibilityEvent.eventTypeToString(event.eventType)} $summary")
                if (DebugLog.dumpTree.value) DebugLog.add("TREE\n" + NodeDump.dump(r))
            }
        }
    }

    override fun onInterrupt() = Unit

    private companion object { const val THROTTLE_MS = 120L }
}
