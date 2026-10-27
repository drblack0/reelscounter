package com.mindware.reels

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mindware.reels.ui.nav.AppNav
import com.mindware.reels.ui.theme.ReelsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ReelsApp).container
        setContent { ReelsTheme { AppNav(container) } }
    }
}
