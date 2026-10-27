package com.mindware.reels.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mindware.reels.data.AppContainer
import com.mindware.reels.ui.onboarding.rememberServiceEnabled
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.state.collectAsStateWithLifecycle()
    val serviceEnabled = rememberServiceEnabled()
    var confirmDelete by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Accessibility service")
                Text(
                    if (serviceEnabled) "Enabled" else "Off: reels are not being counted",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (serviceEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open") }
        }
        HorizontalDivider()

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Overlay counter")
                Text("Shows the running count under the camera while you watch Reels.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(settings.overlayEnabled, { on -> container.settings.update { it.copy(overlayEnabled = on) } })
        }
        HorizontalDivider()

        Text("A reel counts after ${settings.minReelMs / 1000} s on screen")
        Text("Higher values ignore quick flicks past reels.", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = (settings.minReelMs / 1000).toFloat(),
            onValueChange = { v -> container.settings.update { it.copy(minReelMs = v.toLong() * 1000) } },
            valueRange = 1f..5f,
            steps = 3,
        )
        HorizontalDivider()

        NavRow("Keep it running (battery)") { onOpen("battery") }
        NavRow("Privacy") { onOpen("privacy") }
        NavRow("Debug tools") { onOpen("debug") }
        HorizontalDivider()

        OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete all data") }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all data?") },
            text = { Text("This removes every session and reel record. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { container.repository.deleteAll() }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NavRow(title: String, onClick: () -> Unit) {
    Text(title, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
}
