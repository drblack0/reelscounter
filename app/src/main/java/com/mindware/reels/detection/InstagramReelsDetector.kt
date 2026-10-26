package com.mindware.reels.detection

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.mindware.reels.session.Signals

/**
 * Turns an accessibility tree into [Signals]. Reads only view ids, class names, bounds, selected state
 * and content descriptions (matched against patterns, then hashed; the raw text is never kept).
 */
class InstagramReelsDetector(private val config: InstagramConfig = InstagramConfig()) {

    /** Why the detector decided what it did; contains no text from the screen. */
    data class Diagnostics(
        val viewerIdMatch: String?,
        val itemIdMatch: String?,
        val descMatched: Boolean,
        val pagerFound: Boolean,
        val nodesVisited: Int,
    )

    data class Result(val signals: Signals, val diagnostics: Diagnostics)

    fun detect(root: AccessibilityNodeInfo, now: Long): Result {
        val fg = root.packageName?.toString() == config.packageName
        if (!fg) return Result(Signals(now, false, false, null), Diagnostics(null, null, false, false, 0))

        val screen = Rect().also { root.getBoundsInScreen(it) }
        var viewerId: String? = null
        var itemId: String? = null
        var descHash: String? = null
        var pagerFound = false
        var pagerSelectedKey: String? = null
        var visited = 0

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > config.maxDepth || visited >= config.maxNodes) return
            visited++
            val id = node.viewIdResourceName?.substringAfter(":id/")
            if (id != null) {
                if (viewerId == null && config.reelsViewerIdHints.any { id.contains(it) }) viewerId = id
                if (itemId == null && node.isVisibleToUser && config.reelItemIdHints.any { id.contains(it) }) {
                    itemId = id
                    val b = Rect().also { node.getBoundsInScreen(it) }
                    pagerSelectedKey = "$id@${b.top}"
                }
            }
            if (descHash == null && node.isVisibleToUser) {
                val d = node.contentDescription?.toString()
                if (d != null && config.reelContentDescPatterns.any { it.containsMatchIn(d) }) {
                    descHash = d.hashCode().toUInt().toString(16)
                }
            }
            if (!pagerFound && node.className?.toString() in config.pagerClassNames && node.isScrollable) {
                val b = Rect().also { node.getBoundsInScreen(it) }
                // Structural fallback: a tall, near-full-screen scrollable container.
                if (b.height() >= screen.height() * 0.8) pagerFound = true
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                child.use { walk(it, depth + 1) }
            }
        }
        walk(root, 0)

        val descMatched = descHash != null
        val reelsVisible = viewerId != null || (pagerFound && descMatched)
        val reelId = if (reelsVisible) descHash ?: pagerSelectedKey else null
        return Result(
            Signals(now, true, reelsVisible, reelId),
            Diagnostics(viewerId, itemId, descMatched, pagerFound, visited),
        )
    }
}
