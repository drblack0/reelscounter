package com.mindware.reels.stats

import com.mindware.reels.data.db.ReelSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class LocalTimeTest {
    private val ny = ZoneId.of("America/New_York")
    private fun ms(z: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, z).toInstant().toEpochMilli()

    private fun hours(r: LongRange) = (r.last + 1 - r.first) / 3_600_000

    @Test fun `normal day is 24 hours, DST days are 23 and 25`() {
        assertEquals(24, hours(dayRange(LocalDate.of(2026, 6, 1), ny)))
        assertEquals(23, hours(dayRange(LocalDate.of(2026, 3, 8), ny)))  // spring forward
        assertEquals(25, hours(dayRange(LocalDate.of(2026, 11, 1), ny))) // fall back
    }

    @Test fun `same instant lands on different days in different zones`() {
        val ts = ms(ZoneOffset.UTC, 2026, 6, 1, 2) // 02:00 UTC
        assertEquals(LocalDate.of(2026, 6, 1), localDate(ts, ZoneOffset.UTC))
        assertEquals(LocalDate.of(2026, 5, 31), localDate(ts, ny))
        assertEquals(LocalDate.of(2026, 6, 1), localDate(ts, ZoneId.of("Asia/Kolkata")))
    }

    @Test fun `hourly buckets use local hour`() {
        val c = hourlyCounts(listOf(ms(ny, 2026, 6, 1, 9, 5), ms(ny, 2026, 6, 1, 9, 50), ms(ny, 2026, 6, 1, 23, 59)), ny)
        assertEquals(2, c[9])
        assertEquals(1, c[23])
        assertEquals(3, c.sum())
    }

    @Test fun `repeated hour on fall-back day merges into one bucket`() {
        val first = ZonedDateTime.of(2026, 11, 1, 1, 30, 0, 0, ny).withEarlierOffsetAtOverlap().toInstant().toEpochMilli()
        val second = ZonedDateTime.of(2026, 11, 1, 1, 30, 0, 0, ny).withLaterOffsetAtOverlap().toInstant().toEpochMilli()
        assertEquals(2, hourlyCounts(listOf(first, second), ny)[1])
    }

    @Test fun `daily stats zero-fill and attribute sessions to start day`() {
        val sessions = listOf(
            ReelSessionEntity(1, ms(ny, 2026, 6, 1, 10), ms(ny, 2026, 6, 1, 10, 5), 20),
            ReelSessionEntity(2, ms(ny, 2026, 6, 1, 23, 50), ms(ny, 2026, 6, 2, 0, 10), 8), // crosses midnight
            ReelSessionEntity(3, ms(ny, 2026, 6, 3, 8), null, 2), // open
        )
        val now = ms(ny, 2026, 6, 3, 8, 1)
        val days = dailyStats(sessions, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 4), ny, now)
        assertEquals(listOf(28, 0, 2, 0), days.map { it.reels })
        assertEquals(listOf(2, 0, 1, 0), days.map { it.sessions })
        assertEquals(5 * 60_000L + 20 * 60_000L, days[0].timeMs)
        assertEquals(60_000L, days[2].timeMs)
    }
}
