package com.mindware.reels.data

import android.content.Context
import androidx.core.content.edit
import com.mindware.reels.session.SessionConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    val overlayEnabled: Boolean = true,
    /** A reel counts only after this long on screen. */
    val minReelMs: Long = 1_000,
    val onboarded: Boolean = false,
) {
    fun sessionConfig() = SessionConfig(minReelMs = minReelMs.coerceIn(MIN_REEL_RANGE_MS))

    companion object {
        val MIN_REEL_RANGE_MS = 1_000L..5_000L
    }
}

/** Plain SharedPreferences: reads are synchronous (the service needs them at start) and it adds no dependency. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_state.value)
        prefs.edit {
            putBoolean("overlay", next.overlayEnabled)
            putLong("min_reel_ms", next.minReelMs)
            putBoolean("onboarded", next.onboarded)
        }
        _state.value = next
    }

    private fun read() = Settings(
        overlayEnabled = prefs.getBoolean("overlay", true),
        minReelMs = prefs.getLong("min_reel_ms", 1_000),
        onboarded = prefs.getBoolean("onboarded", false),
    )
}
