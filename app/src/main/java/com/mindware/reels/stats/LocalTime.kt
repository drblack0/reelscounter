package com.mindware.reels.stats

import com.mindware.reels.data.db.ReelSessionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// SQLite has no IANA time zones, so "which local day / hour" is computed here from UTC millis using
// the zone passed in (callers pass ZoneId.systemDefault() at query time). A fixed-offset SQL GROUP BY
// would be wrong across DST changes and when the user travels.

/** Half-open [start, end) millis of a local calendar day. A DST day is 23 or 25 hours long. */
fun dayRange(date: LocalDate, zone: ZoneId): LongRange =
    date.atStartOfDay(zone).toInstant().toEpochMilli() until
        date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

fun localDate(ts: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

/** Reels per local hour-of-day (0..23). On a 25-hour day the repeated hour lands in one bucket. */
fun hourlyCounts(viewTimestamps: List<Long>, zone: ZoneId): IntArray {
    val buckets = IntArray(24)
    for (ts in viewTimestamps) buckets[Instant.ofEpochMilli(ts).atZone(zone).hour]++
    return buckets
}

data class DayStats(val date: LocalDate, val reels: Int, val sessions: Int, val timeMs: Long)

/** One entry per day in [from]..[to] inclusive (zero-filled), sessions attributed to their start day. */
fun dailyStats(
    sessions: List<ReelSessionEntity>,
    from: LocalDate,
    to: LocalDate,
    zone: ZoneId,
    now: Long,
): List<DayStats> {
    val byDay = sessions.groupBy { localDate(it.startTs, zone) }
    return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { day ->
        val list = byDay[day].orEmpty()
        DayStats(
            date = day,
            reels = list.sumOf { it.reelCount },
            sessions = list.size,
            timeMs = list.sumOf { (it.endTs ?: now) - it.startTs },
        )
    }.toList()
}
