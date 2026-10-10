package com.oflinetok.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oflinetok.app.data.BackupManager
import java.text.DateFormat
import java.util.Date

/** Returns a function that opens the file picker and restores the chosen backup. */
@Composable
fun rememberRestoreLauncher(vm: AppViewModel): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restore(uri)
    }
    // Some file managers label .json files as plain text or binary.
    return { launcher.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }
}

@Composable
fun BackupDialog(vm: AppViewModel, onRestore: () -> Unit, onDismiss: () -> Unit) {
    val status by vm.backupStatus.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tag backup") },
        text = {
            Column {
                Text(
                    "Your tags, likes and favorites are saved automatically after every change to:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(BackupManager.DISPLAY_PATH, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "This file stays on your phone even if you uninstall OflineTok. " +
                        "Copy it to a new phone to bring your tags along.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        status.lastSavedAt > 0 -> "Last saved: " +
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(status.lastSavedAt))
                        else -> "Not saved yet. A backup is made once you tag, like or favorite a video."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                status.error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Last backup failed: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "To restore after reinstalling, tap Restore and pick the file. " +
                        "If there are several (e.g. \"OflineTok-tags (1).json\"), pick the newest. " +
                        "Restoring only adds tags; it never removes any.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onRestore()
            }) { Text("Restore…") }
        },
        dismissButton = {
            TextButton(onClick = vm::exportNow) { Text("Back up now") }
        },
    )
}
