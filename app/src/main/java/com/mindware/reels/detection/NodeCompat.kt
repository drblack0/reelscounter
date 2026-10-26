package com.mindware.reels.detection

import android.view.accessibility.AccessibilityNodeInfo

/** recycle() is required below API 33 and a no-op after; call it everywhere in a try/finally. */
@Suppress("DEPRECATION")
internal fun AccessibilityNodeInfo.recycleCompat() {
    try {
        recycle()
    } catch (_: IllegalStateException) {
        // already recycled
    }
}

internal inline fun <T> AccessibilityNodeInfo.use(block: (AccessibilityNodeInfo) -> T): T =
    try { block(this) } finally { recycleCompat() }
