package com.oflinetok.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oflinetok.app.data.VideoRepository

/** Opens the system page where the user can turn on "All files access" for OflineTok. */
fun openAllFilesAccessSettings(context: Context) {
    val pkg = Uri.fromParts("package", context.packageName, null)
    val intents = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        listOf(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg),
            // Some phones lack the per-app page and only have the list of all apps.
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
        )
    } else {
        listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
    }
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}

/** Only Android 11+ needs the extra permission; older versions can already read all files. */
val allFilesAccessNeeded: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !VideoRepository.canScanAllFiles()

/** Card on the Me screen offering the storage scan. */
@Composable
fun MissingVideosCard(onAllow: () -> Unit, onHelp: () -> Unit, onHide: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("Missing some videos?", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            "WhatsApp, Telegram and other apps hide their videos from the gallery. " +
                "Allow \"All files access\" and OflineTok will search all your storage for them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onAllow) { Text("Allow") }
            TextButton(onClick = onHelp) { Text("Telegram help") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onHide) { Text("Not now") }
        }
    }
}

@Composable
fun MissingVideosHelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Why is a video missing?") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                HelpSection(
                    "Telegram",
                    "A video you only watched in a chat stays in Telegram's private folder. Android lets no " +
                        "other app read that folder, so OflineTok can't see it.\n\n" +
                        "Fix: open the video in Telegram, tap ⋮ and choose \"Save to Gallery\". To do this " +
                        "automatically, turn on Telegram → Settings → Data and Storage → Save to Gallery.",
                )
                HelpSection(
                    "Hidden folders",
                    "Some apps mark their folders as hidden from the gallery. With \"All files access\" " +
                        "turned on, OflineTok searches all storage, including SD cards, and finds them too." +
                        if (allFilesAccessNeeded) "" else " (It's on.)",
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    HelpSection(
                        "Selected videos only",
                        "If you allowed access to selected videos only, the rest stay hidden. Use the " +
                            "banner on the Me screen to pick more, or allow all videos in app settings.",
                    )
                }
                HelpSection(
                    "Just downloaded",
                    "New videos show up when you come back to the app. Tap ⟳ on the Me screen to search again.",
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        dismissButton = if (allFilesAccessNeeded) {
            {
                TextButton(onClick = {
                    onDismiss()
                    openAllFilesAccessSettings(context)
                }) { Text("Allow all files access") }
            }
        } else {
            null
        },
    )
}

@Composable
private fun HelpSection(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(2.dp))
    Text(body, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))
}
