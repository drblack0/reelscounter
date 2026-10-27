package com.mindware.reels.ui.debug

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindware.reels.detection.DebugLog

@Composable
fun DebugScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val enabled by DebugLog.enabled.collectAsState()
    val dumpTree by DebugLog.dumpTree.collectAsState()
    val lines by DebugLog.lines.collectAsState()

    Column(modifier.fillMaxSize().padding(12.dp)) {
        if (enabled) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text("Debug mode ON: structure-only logging", Modifier.padding(8.dp))
            }
        }
        Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
            Text("Open accessibility settings")
        }
        Row { Switch(enabled, { DebugLog.enabled.value = it }); Spacer(Modifier.width(8.dp)); Text("Debug logging") }
        Row { Switch(dumpTree, { DebugLog.dumpTree.value = it }); Spacer(Modifier.width(8.dp)); Text("Dump node tree on change") }
        OutlinedButton(onClick = { DebugLog.clear() }) { Text("Clear") }
        LazyColumn(Modifier.weight(1f)) {
            items(lines.asReversed()) { Text(it, fontSize = 10.sp, lineHeight = 12.sp) }
        }
    }
}
