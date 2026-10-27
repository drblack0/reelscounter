package com.mindware.reels.detection

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/** [enabledServices] is the raw ENABLED_ACCESSIBILITY_SERVICES value: "pkg/cls:pkg/cls". */
fun isServiceListed(enabledServices: String?, component: ComponentName): Boolean {
    if (enabledServices.isNullOrBlank()) return false
    val full = component.flattenToString()
    val short = component.flattenToShortString()
    return enabledServices.split(':').any { it.equals(full, true) || it.equals(short, true) }
}

fun isServiceEnabled(context: Context): Boolean = isServiceListed(
    Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
    ComponentName(context, ReelsAccessibilityService::class.java),
)
