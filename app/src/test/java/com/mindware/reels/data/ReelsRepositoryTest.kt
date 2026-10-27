package com.mindware.reels.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mindware.reels.data.db.AppDatabase
import com.mindware.reels.data.db.ReelSessionEntity
import com.mindware.reels.session.SessionAction.ReelCounted
import com.mindware.reels.session.SessionAction.ReelDuration
import com.mindware.reels.session.SessionAction.SessionEnded
import com.mindware.reels.session.SessionAction.SessionStarted
import com.mindware.reels.session.SessionTracker
import com.mindware.reels.session.Signals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeHeartbeat(var value: Long? = null) : HeartbeatStore {
    override fun read() = value
    override fun write(ts: Long) { value = ts }
}

@RunWith(RobolectricTestRunner::class)
class ReelsRepositoryTest {
    private lateinit var db: AppDatabase
    private val heartbeat = FakeHeartbeat()
    private lateinit var repo: ReelsRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = ReelsRepository(db.reelDao(), heartbeat)
    }

    @After fun tearDown() = db.close()

    private suspend fun sessions() = db.reelDao().observeSessions(0, Long.MAX_VALUE).first()
    private suspend fun views(sessionId: Long) = db.reelDao().observeViews(sessionId).first()

    @Test fun `actions are persisted as one session with its reels and final durations`() = runTest {
        repo.apply(listOf(SessionStarted(1_000), ReelCounted(1_000, 1_000)))
        repo.apply(listOf(ReelDuration(1_000, 4_000), ReelCounted(5_000, 1_000)))
        repo.apply(listOf(ReelDuration(5_000, 2_500), SessionEnded(7_500)))

        val s = sessions().single()
        assertEquals(2, s.reelCount)
        assertEquals(7_500L, s.endTs)
        assertEquals(listOf(4_000L, 2_500L), views(s.id).map { it.durationMs })
    }

    @Test fun `a second session after the first ended is stored separately`() = runTest {
        repo.apply(listOf(SessionStarted(0), ReelCounted(0, 1_000), SessionEnded(1_000)))
        repo.apply(listOf(SessionStarted(9_000), ReelCounted(9_000, 1_000)))
        val all = sessions()
        assertEquals(2, all.size)
        assertEquals(listOf(1, 1), all.map { it.reelCount })
        assertNull(all[1].endTs)
    }

    @Test fun `tracker output round-trips through the repository`() = runTest {
        val tracker = SessionTracker()
        fun sig(t: Long, id: String?) = Signals(t, true, true, id)
        repo.apply(tracker.onSignals(sig(0, "a")))
        repo.apply(tracker.tick(1_200))
        repo.apply(tracker.onSignals(sig(3_000, "b")))
        repo.apply(tracker.tick(4_100))
        repo.apply(tracker.onSignals(sig(5_000, "a"))) // scrolled back: counts again
        repo.apply(tracker.tick(6_100))
        repo.apply(tracker.onSignals(Signals(7_000, true, false, null)))

        val s = sessions().single()
        assertEquals(3, s.reelCount)
        assertEquals(7_000L, s.endTs)
        assertEquals(3, views(s.id).size)
    }

    @Test fun `dangling session is closed at the last heartbeat`() = runTest {
        val id = db.reelDao().insertSession(ReelSessionEntity(startTs = 1_000, endTs = null, reelCount = 0))
        heartbeat.value = 9_000
        repo.recoverDangling(now = 100_000)
        assertEquals(9_000L, sessions().single { it.id == id }.endTs)
    }

    @Test fun `noteActivity is throttled`() {
        repo.noteActivity(10_000)
        repo.noteActivity(12_000)
        assertEquals(10_000L, heartbeat.value)
        repo.noteActivity(15_000)
        assertEquals(15_000L, heartbeat.value)
    }
}

class RecoveredEndTest {
    private val idle = 30_000L

    @Test fun `uses heartbeat when it belongs to the session`() =
        assertEquals(9_000L, recoveredEnd(1_000, 9_000, 5_000, now = 99_000, idleTimeoutMs = idle))

    @Test fun `ignores a heartbeat that predates the session`() =
        assertEquals(10_000L, recoveredEnd(10_000, 2_000, null, now = 99_000, idleTimeoutMs = idle))

    @Test fun `heartbeat far after the last reel is capped by the idle timeout`() =
        assertEquals(35_000L, recoveredEnd(0, 500_000, 5_000, now = 900_000, idleTimeoutMs = idle))

    @Test fun `falls back to last reel end then to start`() {
        assertEquals(5_000L, recoveredEnd(1_000, null, 5_000, now = 99_000, idleTimeoutMs = idle))
        assertEquals(1_000L, recoveredEnd(1_000, null, null, now = 99_000, idleTimeoutMs = idle))
    }

    @Test fun `never in the future`() =
        assertEquals(8_000L, recoveredEnd(1_000, 50_000, 7_000, now = 8_000, idleTimeoutMs = idle))
}
