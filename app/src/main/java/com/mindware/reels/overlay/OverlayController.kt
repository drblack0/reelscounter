package com.mindware.reels.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import com.mindware.reels.R

/** Small non-interactive pill below the status bar. Main thread only. */
class OverlayController(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var pill: TextView? = null

    fun show(text: String) {
        val view = pill ?: createPill().also { pill = it }
        view.text = text
    }

    fun hide() {
        pill?.let { runCatching { windowManager.removeView(it) } }
        pill = null
    }

    private fun createPill(): TextView {
        val view = LayoutInflater.from(service).inflate(R.layout.overlay_pill, null) as TextView
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // LAYOUT_IN_SCREEN makes y count from the very top of the screen, not from below the status bar.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topInset() + (2 * service.resources.displayMetrics.density).toInt()
        }
        windowManager.addView(view, params)
        return view
    }

    /** Bottom edge of the status bar / camera cutout, so the pill sits just below the notch. */
    private fun topInset(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val types = WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
            return windowManager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(types).top
        }
        val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) service.resources.getDimensionPixelSize(id) else 0
    }
}

/** "12 reels · 03:41"; hours appear only when needed. */
fun formatPill(reelCount: Int, elapsedMs: Long): String {
    val total = (elapsedMs / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val time = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    return "$reelCount ${if (reelCount == 1) "reel" else "reels"} · $time"
}
