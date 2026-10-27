package com.mindware.reels.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyScreen(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Privacy", style = MaterialTheme.typography.headlineMedium)
        Section(
            "What the app looks at",
            "While Instagram is open, the accessibility service reads the screen's structure: element IDs, " +
                "class names, positions and whether an element is selected. It also looks at element descriptions, " +
                "only to recognise a reel and tell one reel from the next.",
        )
        Section(
            "What it keeps",
            "Descriptions are turned into a short one-way hash and the text is discarded. No usernames, captions, " +
                "comments or messages are stored or logged. The database holds only start times, reel timestamps and durations.",
        )
        Section(
            "Where data goes",
            "Nowhere. The app does not request internet permission, so it cannot send anything off the phone. " +
                "Android backup is turned off, so the database is not copied to your Google account either.",
        )
        Section(
            "Apps other than Instagram",
            "The service is told when another app comes to the front, only to end a Reels session. Nothing is read from those apps.",
        )
        Section(
            "Debug mode",
            "When on, a banner is shown. Logs contain only structure (IDs, classes, positions) and never on-screen text. " +
                "They stay in the phone's log and are cleared when the app closes.",
        )
        Section("Deleting data", "Settings → Delete all data removes every session. Uninstalling the app removes everything.")
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
