package com.oflinetok.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oflinetok.app.data.UpdateChecker
import com.oflinetok.app.data.UpdateInfo

@Composable
fun UpdateDialog(updates: UpdateViewModel) {
    val context = LocalContext.current
    val state by updates.state.collectAsStateWithLifecycle()
    val current = updates.currentVersion

    // Open the installer as soon as the download finishes; the button below stays as a fallback.
    (state as? UpdateState.ReadyToInstall)?.let { ready ->
        LaunchedEffect(ready.apk) { updates.install(context, ready.apk) }
    }

    AlertDialog(
        onDismissRequest = updates::later,
        title = {
            Text(
                when (val s = state) {
                    is UpdateState.Available -> "Update available"
                    is UpdateState.Downloading -> "Downloading ${s.info.version}"
                    is UpdateState.ReadyToInstall -> "Ready to install"
                    is UpdateState.UpToDate -> "You're up to date"
                    is UpdateState.Failed -> "Update problem"
                    else -> "Checking for updates"
                },
            )
        },
        text = {
            when (val s = state) {
                is UpdateState.Available -> AvailableBody(s.info, current)
                is UpdateState.Downloading -> Column {
                    if (s.progress == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You can keep using the app. The download continues in the background.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is UpdateState.ReadyToInstall -> Text(
                    "Tap Install and confirm in Android's installer. Your tags and likes are kept.\n\n" +
                        "If Android asks, allow OflineTok to install apps. This is only needed once.",
                )
                is UpdateState.UpToDate -> Text("OflineTok $current is the latest version.")
                is UpdateState.Failed -> Text(s.message)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Asking GitHub…")
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                is UpdateState.Available -> TextButton(onClick = { updates.download(s.info) }) { Text("Update") }
                is UpdateState.ReadyToInstall -> TextButton(onClick = { updates.install(context, s.apk) }) {
                    Text("Install")
                }
                is UpdateState.Failed -> TextButton(onClick = {
                    s.info?.let(updates::download) ?: updates.checkNow()
                }) { Text("Retry") }
                is UpdateState.Downloading -> TextButton(onClick = updates::later) { Text("Hide") }
                else -> TextButton(onClick = updates::later) { Text("OK") }
            }
        },
        dismissButton = {
            when (val s = state) {
                is UpdateState.Available -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { updates.skip(s.info) }) { Text("Skip this version") }
                    TextButton(onClick = updates::later) { Text("Later") }
                }
                is UpdateState.ReadyToInstall, is UpdateState.Failed ->
                    TextButton(onClick = updates::later) { Text("Later") }
                else -> {}
            }
        },
    )
}

@Composable
private fun AvailableBody(info: UpdateInfo, current: String) {
    Column(
        Modifier
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Version ${info.version} is out. You have $current.")
        if (info.apkSize > 0) {
            Text(
                "Download size: %.1f MB".format(info.apkSize / 1_000_000.0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (info.notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("What's new", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(info.notes, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun AboutDialog(updates: UpdateViewModel, onMissingVideos: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val autoCheck by updates.autoCheck.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("OflineTok ${updates.currentVersion}") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Check for updates automatically",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = autoCheck, onCheckedChange = updates::setAutoCheck)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Once a day OflineTok asks GitHub whether a newer version exists, and offers it if so. " +
                        "Updates are never installed without you. This check, and downloading an update you " +
                        "chose, are the only things the app does online. Your videos and tags never leave the phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.REPO_URL))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("View on GitHub") }
                TextButton(onClick = onMissingVideos) { Text("Missing videos?") }
                Spacer(Modifier.height(8.dp))
                SmartFeaturesRow()
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                updates.checkNow()
            }) { Text("Check now") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
