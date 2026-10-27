package com.mindware.reels.data

import android.content.Context
import androidx.core.content.edit

/** Last time the service saw Reels activity; used to close sessions left open by a crash or kill. */
interface HeartbeatStore {
    fun read(): Long?
    fun write(ts: Long)
}

class PrefsHeartbeatStore(context: Context) : HeartbeatStore {
    private val prefs = context.applicationContext.getSharedPreferences("heartbeat", Context.MODE_PRIVATE)

    override fun read(): Long? = prefs.getLong(KEY, -1L).takeIf { it >= 0 }

    override fun write(ts: Long) = prefs.edit { putLong(KEY, ts) }

    private companion object { const val KEY = "last_activity_ts" }
}
