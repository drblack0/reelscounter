package com.mindware.reels.data

import android.content.Context
import com.mindware.reels.data.db.AppDatabase

class AppContainer(context: Context) {
    val database: AppDatabase by lazy { AppDatabase.create(context.applicationContext) }
}
