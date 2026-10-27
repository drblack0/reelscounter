package com.mindware.reels.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindware.reels.data.ReelsRepository
import com.mindware.reels.data.db.ReelSessionEntity
import com.mindware.reels.stats.DayStats
import com.mindware.reels.stats.dailyStats
import com.mindware.reels.stats.dayRange
import com.mindware.reels.stats.localDate
import com.mindware.reels.ui.components.BarChart
import com.mindware.reels.ui.format.formatDuration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryScreen(repository: ReelsRepository, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val now = remember { System.currentTimeMillis() }
    val today = remember { localDate(now, zone) }
    var span by rememberSaveable { mutableIntStateOf(7) }
    var selectedEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpochDay)

    val from = today.minusDays(span - 1L)
    val sessions by remember(span) {
        repository.observeSessions(dayRange(from, zone).first, dayRange(today, zone).last + 1)
    }.collectAsStateWithLifecycle(emptyList())
    val days = remember(sessions, span) { dailyStats(sessions, from, today, zone, now) }
    val selectedStats = days.firstOrNull { it.date == selected }
    val selectedSessions = remember(sessions, selected) { sessions.filter { localDate(it.startTs, zone) == selected }.sortedByDescending { it.startTs } }

    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("History", style = MaterialTheme.typography.headlineMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(span == 7, { span = 7 }, { Text("Week") })
                FilterChip(span == 30, { span = 30 }, { Text("Month") })
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val total = days.sumOf { it.reels }
                    Text("$total reels, ${"%.1f".format(total / span.toDouble())} per day", style = MaterialTheme.typography.titleMedium)
                    BarChart(
                        values = days.map { it.reels },
                        labels = days.mapIndexed { i, d -> axisLabel(d, i, span) },
                        description = "Reels per day over the last $span days, $total in total",
                        selected = days.indexOfFirst { it.date == selected }.takeIf { it >= 0 },
                        onBarClick = { selectedEpochDay = days[it].date.toEpochDay() },
                    )
                }
            }
        }
        item {
            Text(
                dayTitle(selected, today) + selectedStats?.let { " · ${it.reels} reels · ${formatDuration(it.timeMs)}" }.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (selectedSessions.isEmpty()) {
            item { Text("No sessions this day.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(selectedSessions, key = { it.id }) { SessionRow(it, zone, now) }
    }
}

private fun axisLabel(d: DayStats, index: Int, span: Int): String = when {
    span == 7 -> d.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
    index % 5 == 0 -> d.date.dayOfMonth.toString()
    else -> ""
}

private fun dayTitle(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}

@Composable
private fun SessionRow(s: ReelSessionEntity, zone: ZoneId, now: Long) {
    val start = Instant.ofEpochMilli(s.startTs).atZone(zone).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (s.endTs == null) "$start · in progress" else start)
            Text("${s.reelCount} reels · ${formatDuration((s.endTs ?: now) - s.startTs)}")
        }
    }
}
