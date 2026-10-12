package com.oflinetok.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oflinetok.app.data.VideoItem

/** Me → Hidden: videos the user blocked, and duplicate copies hidden automatically. */
@Composable
fun HiddenSection(vm: AppViewModel) {
    val blocked by vm.blockedVideos.collectAsStateWithLifecycle()
    val duplicates by vm.duplicateCopies.collectAsStateWithLifecycle()
    val hideDuplicates by vm.hideDuplicates.collectAsStateWithLifecycle()
    val meta by vm.meta.collectAsStateWithLifecycle()
    var showDuplicates by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<VideoItem?>(null) }

    Column {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !showDuplicates,
                onClick = { showDuplicates = false },
                label = { Text("Blocked  ${blocked.size}") },
            )
            FilterChip(
                selected = showDuplicates,
                onClick = { showDuplicates = true },
                label = { Text("Duplicates  ${duplicates.size}") },
            )
        }

        if (!showDuplicates) {
            if (blocked.isEmpty()) {
                EmptyMessage(
                    "No blocked videos",
                    "Videos you hide with ⋮ → \"Don't show this video again\" are listed here.",
                )
            } else {
                Hint("Tap a video to show it again or watch it.")
                VideoGrid(videos = blocked, meta = meta, onOpen = { selected = blocked[it] })
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Hide duplicate copies", modifier = Modifier.weight(1f))
                Switch(checked = hideDuplicates, onCheckedChange = vm::setHideDuplicates)
            }
            Hint(
                "When the exact same video file is saved more than once (e.g. from Telegram and WhatsApp), " +
                    "only one copy plays and the extra copies are listed here. Edited or re-compressed " +
                    "versions are different files and stay visible.",
            )
            if (duplicates.isEmpty()) {
                EmptyMessage("No duplicates found", "Every video in your library is unique.")
            } else {
                val copies = duplicates.map { it.copy }
                VideoGrid(
                    videos = copies,
                    meta = meta,
                    onOpen = { index -> vm.openFeed("Duplicates", copies, index, showHidden = true) },
                )
            }
        }
    }

    selected?.let { video ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Hidden video") },
            text = {
                Text(
                    listOfNotNull(video.name, video.folder?.let { "in $it" }).joinToString("\n"),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setBlocked(video.id, false)
                    selected = null
                }) { Text("Show again") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        vm.openFeed("Hidden", blocked, blocked.indexOf(video).coerceAtLeast(0), showHidden = true)
                        selected = null
                    }) { Text("Watch") }
                    TextButton(onClick = { selected = null }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}
