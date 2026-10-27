package com.mindware.reels.data

import android.content.Context
import com.mindware.reels.data.db.AppDatabase

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val repository: ReelsRepository by lazy { ReelsRepository(database.reelDao(), PrefsHeartbeatStore(appContext)) }
}
