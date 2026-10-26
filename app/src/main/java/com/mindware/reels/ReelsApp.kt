package com.mindware.reels

import android.app.Application
import com.mindware.reels.data.AppContainer

class ReelsApp : Application() {
    // Manual DI: a handful of singletons doesn't justify Hilt.
    val container: AppContainer by lazy { AppContainer(this) }
}
