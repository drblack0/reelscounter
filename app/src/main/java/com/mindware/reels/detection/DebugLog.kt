package com.mindware.reels.detection

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory ring of recent debug lines (also sent to logcat under tag ReelsDebug). */
object DebugLog {
    private const val MAX = 200
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines
    val enabled = MutableStateFlow(false)
    val dumpTree = MutableStateFlow(false)

    fun add(line: String) {
        Log.d("ReelsDebug", line)
        _lines.value = (_lines.value + line).takeLast(MAX)
    }

    fun clear() { _lines.value = emptyList() }
}
