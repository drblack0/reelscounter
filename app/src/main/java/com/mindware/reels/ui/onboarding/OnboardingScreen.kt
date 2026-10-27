package com.mindware.reels.ui.onboarding

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mindware.reels.detection.isServiceEnabled

/** Whether our accessibility service is on; re-checked every time the app returns to the foreground. */
@Composable
fun rememberServiceEnabled(): Boolean {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(isServiceEnabled(ctx)) }
    LifecycleResumeEffect(Unit) {
        enabled = isServiceEnabled(ctx)
        onPauseOrDispose { }
    }
    return enabled
}

@Composable
fun OnboardingScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val serviceEnabled = rememberServiceEnabled()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Welcome", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Reels Counter counts the Instagram reels you watch and how long you spend. " +
                "Everything stays on this phone. The app has no internet permission.",
        )

        Step("1. Turn on the service", done = serviceEnabled) {
            Text("Android needs you to enable the accessibility service so the app can tell when Reels is on screen.")
            Text(
                "Status: " + if (serviceEnabled) "enabled" else "not enabled yet",
                style = MaterialTheme.typography.labelLarge,
            )
            Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                Text("Open accessibility settings")
            }
            if (Build.VERSION.SDK_INT >= 33) {
                Text(
                    "If the switch is greyed out: open the app's info page, tap the ⋮ menu and choose " +
                        "\"Allow restricted settings\", then try again.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")),
                    )
                }) { Text("Open app info") }
            }
        }

        Step("2. Keep it running", done = null) { BatteryContent() }

        Button(onClick = onDone, Modifier.fillMaxWidth()) { Text(if (serviceEnabled) "Get started" else "Continue anyway") }
    }
}

@Composable
private fun Step(title: String, done: Boolean?, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title + if (done == true) "  ✓" else "", style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Shared by onboarding and the Settings "Keep it running" page. */
@Composable
fun BatteryContent() {
    val ctx = LocalContext.current
    val guidance = remember { batteryGuidanceFor(Build.MANUFACTURER) }
    Text(
        "Some phones stop background services to save battery, which would pause counting. " +
            "Suggested steps for ${guidance.brand}:",
    )
    guidance.steps.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
    Text("Menu names differ between OS versions.", style = MaterialTheme.typography.bodySmall)
    OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) {
        Text("Open battery optimisation settings")
    }
}
