package com.mindware.reels.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatPillTest {
    @Test fun `singular and plural`() {
        assertEquals("1 reel · 00:00", formatPill(1, 0))
        assertEquals("12 reels · 03:41", formatPill(12, 221_000))
    }

    @Test fun `hours only when needed`() = assertEquals("3 reels · 1:02:03", formatPill(3, 3_723_000))

    @Test fun `negative elapsed clamps to zero`() = assertEquals("0 reels · 00:00", formatPill(0, -5_000))
}
