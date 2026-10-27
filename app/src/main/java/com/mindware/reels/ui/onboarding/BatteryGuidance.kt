package com.mindware.reels.ui.onboarding

/** Steps that stop a phone's battery manager from killing the service. Menu names vary by OS version. */
data class BatteryGuidance(val brand: String, val steps: List<String>)

fun batteryGuidanceFor(manufacturer: String): BatteryGuidance {
    val m = manufacturer.lowercase()
    return when {
        "xiaomi" in m || "redmi" in m || "poco" in m -> BatteryGuidance(
            "Xiaomi / Redmi / POCO",
            listOf(
                "Settings → Apps → Manage apps → Reels Counter → Autostart: turn on.",
                "On the same page, Battery saver: choose No restrictions.",
                "Open Recents, long-press the Reels Counter card and tap the lock icon.",
            ),
        )
        "oppo" in m || "realme" in m || "oneplus" in m -> BatteryGuidance(
            "Oppo / Realme / OnePlus",
            listOf(
                "Settings → Apps → App management → Reels Counter → Battery usage: allow background activity.",
                "Turn on Auto launch (or Allow auto-start) for the app.",
                "Open Recents and lock the Reels Counter card.",
            ),
        )
        "vivo" in m || "iqoo" in m -> BatteryGuidance(
            "Vivo / iQOO",
            listOf(
                "Settings → Battery → Background power consumption → Reels Counter: allow.",
                "Settings → Apps → Autostart: turn on Reels Counter.",
                "Open Recents and lock the Reels Counter card.",
            ),
        )
        "samsung" in m -> BatteryGuidance(
            "Samsung",
            listOf(
                "Settings → Battery → Background usage limits: remove Reels Counter from Sleeping and Deep sleeping apps.",
                "Add it to Never sleeping apps.",
                "Settings → Apps → Reels Counter → Battery: choose Unrestricted.",
            ),
        )
        "huawei" in m || "honor" in m -> BatteryGuidance(
            "Huawei / Honor",
            listOf(
                "Settings → Apps → App launch → Reels Counter: switch to Manage manually.",
                "Turn on Auto-launch, Secondary launch and Run in background.",
            ),
        )
        else -> BatteryGuidance(
            "Your phone",
            listOf(
                "Settings → Apps → Reels Counter → Battery: choose Unrestricted (or Don't optimise).",
                "If your phone has an Autostart or Background activity list, allow Reels Counter there.",
            ),
        )
    }
}
