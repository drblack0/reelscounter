package com.mindware.reels.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.mindware.reels.data.db.AppDatabase
import com.mindware.reels.data.db.ReelDao
import com.mindware.reels.data.db.ReelSessionEntity
import com.mindware.reels.data.db.ReelViewEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReelDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ReelDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.reelDao()
    }

    @After fun tearDown() = db.close()

    private suspend fun session(start: Long, end: Long?, reels: Int) =
        dao.insertSession(ReelSessionEntity(startTs = start, endTs = end, reelCount = reels))

    @Test fun `totals aggregate sessions inside the range only`() = runTest {
        session(1_000, 61_000, 10)   // 60s
        session(100_000, 400_000, 5) // 300s, longest
        session(5_000_000, 5_060_000, 99) // outside range
        val totals = dao.observeTotals(0, 1_000_000, now = 0).first()
        assertEquals(2, totals.sessionCount)
        assertEquals(15, totals.reelCount)
        assertEquals(360_000, totals.totalMs)
        assertEquals(300_000, totals.longestMs)
    }

    @Test fun `range end is exclusive and start is inclusive`() = runTest {
        session(1_000, 2_000, 1)
        session(2_000, 3_000, 1)
        assertEquals(1, dao.observeTotals(1_000, 2_000, 0).first().sessionCount)
    }

    @Test fun `empty range yields zero totals`() = runTest {
        val totals = dao.observeTotals(0, 1_000, 0).first()
        assertEquals(0, totals.sessionCount)
        assertEquals(0, totals.totalMs)
    }

    @Test fun `open session is measured up to now`() = runTest {
        session(1_000, null, 3)
        assertEquals(9_000, dao.observeTotals(0, 100_000, now = 10_000).first().totalMs)
    }

    @Test fun `totals flow re-emits when data changes`() = runTest {
        dao.observeTotals(0, 100_000, 0).test {
            assertEquals(0, awaitItem().reelCount)
            val id = session(1_000, null, 0)
            assertEquals(0, awaitItem().reelCount)
            dao.incrementReelCount(id)
            assertEquals(1, awaitItem().reelCount)
        }
    }

    @Test fun `views are stored per session and cascade on delete`() = runTest {
        val id = session(0, null, 0)
        dao.insertView(ReelViewEntity(sessionId = id, ts = 10, durationMs = 1_000))
        dao.insertView(ReelViewEntity(sessionId = id, ts = 20, durationMs = 2_000))
        assertEquals(2, dao.observeViews(id).first().size)
        assertEquals(listOf(10L, 20L), dao.observeViewTimestamps(0, 100).first())
        dao.deleteAll()
        assertEquals(0, dao.observeViews(id).first().size)
        assertEquals(0, dao.observeViewTimestamps(0, 100).first().size)
    }

    @Test fun `view duration can be updated after insert`() = runTest {
        val id = session(0, null, 0)
        val viewId = dao.insertView(ReelViewEntity(sessionId = id, ts = 10, durationMs = 1_000))
        dao.updateViewDuration(viewId, 7_000)
        assertEquals(7_000, dao.observeViews(id).first().single().durationMs)
    }

    @Test fun `dangling sessions are found and can be closed at last view end`() = runTest {
        val open = session(0, null, 1)
        session(500, 900, 1) // already closed
        dao.insertView(ReelViewEntity(sessionId = open, ts = 100, durationMs = 4_000))
        assertEquals(listOf(open), dao.openSessions().map { it.id })
        assertEquals(4_100L, dao.lastViewEnd(open))
        dao.closeSession(open, dao.lastViewEnd(open)!!)
        assertEquals(0, dao.openSessions().size)
    }

    @Test fun `lastViewEnd is null when a session has no views`() = runTest {
        assertNull(dao.lastViewEnd(session(0, null, 0)))
    }

    @Test fun `observeSessions orders by start and respects range`() = runTest {
        session(300, 400, 1)
        session(100, 200, 1)
        session(900, 950, 1)
        assertEquals(listOf(100L, 300L), dao.observeSessions(0, 500).first().map { it.startTs })
    }
}
