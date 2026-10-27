package com.mindware.reels.data

import android.content.Context
import com.mindware.reels.data.db.AppDatabase
import com.mindware.reels.session.LiveState
import kotlinx.coroutines.flow.MutableStateFlow

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    /** Published by the service, read by the overlay. */
    val live = MutableStateFlow(LiveState())
    val repository: ReelsRepository by lazy { ReelsRepository(database.reelDao(), PrefsHeartbeatStore(appContext)) }
}
