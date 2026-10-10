package com.oflinetok.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oflinetok.app.data.VideoItem
import com.oflinetok.app.data.VideoMeta

/** Short name for the filter tab, e.g. "#cats", "Camera" or "#cats +2". */
fun FeedFilter.label(): String {
    val parts = tags.sorted().map { "#$it" } + folders.sorted()
    return when (parts.size) {
        0 -> "Filter"
        1 -> parts.first()
        else -> "${parts.first()} +${parts.size - 1}"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedFilterSheet(
    current: FeedFilter,
    allTags: List<TagCount>,
    allFolders: List<FolderCount>,
    videos: List<VideoItem>,
    meta: Map<Long, VideoMeta>,
    onApply: (FeedFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Edits stay a draft until "Show" so the feed doesn't reshuffle on every tap.
    var draft by remember { mutableStateOf(current) }
    var query by remember { mutableStateOf("") }
    val q = query.trim().trimStart('#').lowercase()

    val tags = allTags.filter { q.isEmpty() || it.tag.contains(q) || it.tag in draft.tags }
    val folders = allFolders.filter { q.isEmpty() || it.folder.lowercase().contains(q) || it.folder in draft.folders }
    val matching = if (draft.isEmpty) videos.size else videos.count { draft.matches(it, meta[it.id]) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text("Choose what to watch", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "Any selected tag counts. Pick folders too and a video must also be in one of them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (allTags.size + allFolders.size > 12) {
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Find a tag or folder") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Clear") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(Modifier.height(16.dp))
                Text("Tags", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                if (allTags.isEmpty()) {
                    Text(
                        "No tags yet. Tag videos with the # button in the feed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { t ->
                            val on = t.tag in draft.tags
                            FilterChip(
                                selected = on,
                                onClick = {
                                    draft = draft.copy(tags = if (on) draft.tags - t.tag else draft.tags + t.tag)
                                },
                                label = { Text("#${t.tag}  ${t.count}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TokPink.copy(alpha = 0.35f),
                                ),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Folders", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    folders.forEach { f ->
                        val on = f.folder in draft.folders
                        FilterChip(
                            selected = on,
                            onClick = {
                                draft = draft.copy(
                                    folders = if (on) draft.folders - f.folder else draft.folders + f.folder,
                                )
                            },
                            label = { Text("${f.folder}  ${f.count}") },
                            leadingIcon = { Icon(Icons.Filled.Folder, null, Modifier.size(18.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TokCyan.copy(alpha = 0.3f),
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { draft = FeedFilter() }, enabled = !draft.isEmpty) { Text("Clear") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onApply(draft) },
                    enabled = draft.isEmpty || matching > 0,
                ) {
                    Text(
                        when {
                            draft.isEmpty -> "Show all videos"
                            matching == 0 -> "No matching videos"
                            else -> "Show $matching video${if (matching == 1) "" else "s"}"
                        },
                    )
                }
            }
        }
    }
}
