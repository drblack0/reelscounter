package com.mindware.reels.ui

import android.content.ComponentName
import com.mindware.reels.detection.isServiceListed
import com.mindware.reels.ui.onboarding.batteryGuidanceFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class BatteryGuidanceTest {
    @Test fun `picks vendor specific steps`() {
        assertEquals("Xiaomi / Redmi / POCO", batteryGuidanceFor("Xiaomi").brand)
        assertEquals("Xiaomi / Redmi / POCO", batteryGuidanceFor("POCO").brand)
        assertEquals("Samsung", batteryGuidanceFor("samsung").brand)
        assertEquals("Oppo / Realme / OnePlus", batteryGuidanceFor("OnePlus").brand)
    }

    @Test fun `unknown vendor gets generic steps`() {
        val g = batteryGuidanceFor("Google")
        assertEquals("Your phone", g.brand)
        assertTrue(g.steps.isNotEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
class ServiceListedTest {
    private val component = ComponentName("com.mindware.reels", "com.mindware.reels.detection.ReelsAccessibilityService")

    @Test fun `finds the service in the colon separated list`() {
        val list = "com.other/com.other.Svc:" + component.flattenToString()
        assertTrue(isServiceListed(list, component))
    }

    @Test fun `accepts the short component form`() =
        assertTrue(isServiceListed(component.flattenToShortString(), component))

    @Test fun `false when missing or empty`() {
        assertFalse(isServiceListed(null, component))
        assertFalse(isServiceListed("", component))
        assertFalse(isServiceListed("com.other/com.other.Svc", component))
    }
}
