package com.mindware.reels

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.mindware.reels.ui.debug.DebugScreen
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.mindware.reels.data.db.RangeTotals
import com.mindware.reels.stats.dayRange
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val nav = rememberNavController()
                val repository = (application as ReelsApp).container.repository
                val totals by produceState<RangeTotals?>(null) {
                    // Re-subscribe every minute so the open session's elapsed time stays fresh.
                    while (true) {
                        val now = System.currentTimeMillis()
                        val range = dayRange(LocalDate.now(), ZoneId.systemDefault())
                        withTimeoutOrNull(60_000) {
                            repository.observeTotals(range.first, range.last + 1, now).collect { value = it }
                        }
                    }
                }
                NavHost(nav, startDestination = "today") {
                    composable("today") { DebugScreen(totals) } // replaced in M5
                }
            }
        }
    }
}
