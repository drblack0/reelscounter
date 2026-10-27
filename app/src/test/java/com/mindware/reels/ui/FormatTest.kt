package com.mindware.reels.ui

import com.mindware.reels.ui.format.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun `formats seconds, minutes and hours`() {
        assertEquals("0s", formatDuration(0))
        assertEquals("45s", formatDuration(45_999))
        assertEquals("4m 05s", formatDuration(245_000))
        assertEquals("1h 23m", formatDuration(4_980_000))
    }

    @Test fun `negative clamps to zero`() = assertEquals("0s", formatDuration(-1))
}
