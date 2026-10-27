package com.mindware.reels.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindware.reels.data.ReelsRepository
import com.mindware.reels.data.db.RangeTotals
import com.mindware.reels.session.LiveState
import com.mindware.reels.stats.dayRange
import com.mindware.reels.stats.hourlyCounts
import com.mindware.reels.stats.localDate
import com.mindware.reels.ui.components.BarChart
import com.mindware.reels.ui.format.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.time.ZoneId

@Composable
fun TodayScreen(repository: ReelsRepository, live: StateFlow<LiveState>, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val liveState by live.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(liveState.active) {
        while (true) {
            now = System.currentTimeMillis()
            delay(if (liveState.active) 1_000 else 30_000)
        }
    }

    // Keyed on the minute: an open session's duration is recomputed once a minute, not every second.
    val minute = now / 60_000
    val range = remember(minute) { dayRange(localDate(now, zone), zone) }
    val totals by remember(range, minute) { repository.observeTotals(range.first, range.last + 1, now) }
        .collectAsStateWithLifecycle(RangeTotals(0, 0, 0, 0))
    val stamps by remember(range) { repository.observeViewTimestamps(range.first, range.last + 1) }
        .collectAsStateWithLifecycle(emptyList())
    val hourly = remember(stamps, zone) { hourlyCounts(stamps, zone).toList() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Today", style = MaterialTheme.typography.headlineMedium)

        if (liveState.active) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Watching Reels now", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${liveState.reelCount} reels · ${formatDuration(now - liveState.startedAt)}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Reels", totals.reelCount.toString(), Modifier.weight(1f))
            Stat("Time", formatDuration(totals.totalMs), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Sessions", totals.sessionCount.toString(), Modifier.weight(1f))
            Stat("Longest", formatDuration(totals.longestMs), Modifier.weight(1f))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reels by hour", style = MaterialTheme.typography.titleMedium)
                if (stamps.isEmpty()) {
                    Text("Nothing yet today.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BarChart(
                    values = hourly,
                    labels = List(24) { if (it % 6 == 0) "%02d".format(it) else "" },
                    description = "Reels per hour of the day, ${stamps.size} in total",
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
