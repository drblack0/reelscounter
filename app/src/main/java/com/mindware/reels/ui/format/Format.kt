package com.mindware.reels.ui.format

/** "45s", "4m 05s", "1h 23m". Drops the smaller unit once the larger one dominates. */
fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m %02ds".format(s)
        else -> "${s}s"
    }
}
