package com.mindware.reels.detection

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Structure-only tree dump for debugging. Never includes text or content descriptions, only whether a
 * description is present, so shared logs cannot leak captions or usernames.
 */
object NodeDump {
    fun dump(root: AccessibilityNodeInfo, maxNodes: Int = 400, maxDepth: Int = 30): String {
        val sb = StringBuilder()
        var count = 0
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > maxDepth || count >= maxNodes) return
            count++
            val b = Rect().also { n.getBoundsInScreen(it) }
            sb.append("  ".repeat(depth))
                .append(n.className?.toString()?.substringAfterLast('.') ?: "?")
                .append(" id=").append(n.viewIdResourceName?.substringAfter(":id/") ?: "-")
                .append(" [").append(b.left).append(',').append(b.top).append("][").append(b.right).append(',').append(b.bottom).append(']')
                .append(if (n.isScrollable) " scroll" else "")
                .append(if (n.isSelected) " sel" else "")
                .append(if (n.isVisibleToUser) "" else " hidden")
                .append(if (n.contentDescription != null) " desc" else "")
                .append('\n')
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                c.use { walk(it, depth + 1) }
            }
        }
        walk(root, 0)
        return sb.toString()
    }
}
