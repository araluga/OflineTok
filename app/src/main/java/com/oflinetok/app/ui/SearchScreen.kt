package com.oflinetok.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oflinetok.app.data.normalizeTag

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(vm: AppViewModel) {
    val videos by vm.videos.collectAsStateWithLifecycle()
    val meta by vm.meta.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var untagged by rememberSaveable { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }

    val q = normalizeTag(query)
    val visibleTags = allTags.filter { q == null || it.tag.contains(q) || it.tag in selected }

    // Selected tags must all match (AND). Typed text matches any tag containing it.
    val results = when {
        untagged -> videos.filter { meta[it.id]?.tags.isNullOrEmpty() }
        selected.isNotEmpty() -> videos.filter { v ->
            val tags = meta[v.id]?.tags.orEmpty()
            selected.all { it in tags } && (q == null || tags.any { it.contains(q) })
        }
        q != null -> videos.filter { v -> meta[v.id]?.tags.orEmpty().any { it.contains(q) } }
        else -> null
    }

    val resultTitle = when {
        untagged -> "Untagged"
        selected.isNotEmpty() -> selected.joinToString(" ") { "#$it" }
        else -> "#$q"
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search by tag") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Clear") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        if (allTags.isEmpty()) {
            EmptyMessage(
                "No tags yet",
                "Tap the # button on any video in your feed to tag it. Then you can find it here.",
            )
            return@Column
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Tags",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (selected.isNotEmpty() || untagged) {
                TextButton(onClick = {
                    selected = emptyList()
                    untagged = false
                }) { Text("Clear") }
            }
            TextButton(onClick = { managing = true }) { Text("Manage") }
        }

        // When results are shown the tag cloud collapses to a scrollable row to make room.
        val chips: @Composable () -> Unit = {
            FilterChip(
                selected = untagged,
                onClick = {
                    untagged = !untagged
                    if (untagged) selected = emptyList()
                },
                label = { Text("Untagged") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TokCyan.copy(alpha = 0.3f)),
            )
            visibleTags.forEach { t ->
                FilterChip(
                    selected = t.tag in selected,
                    onClick = {
                        untagged = false
                        selected = if (t.tag in selected) selected - t.tag else selected + t.tag
                    },
                    label = { Text("#${t.tag}  ${t.count}") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TokPink.copy(alpha = 0.35f)),
                )
            }
        }
        if (results == null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .verticalScroll(rememberScrollState()),
            ) { chips() }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
            ) { chips() }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${results.size} video${if (results.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { vm.openFeed(resultTitle, results.shuffled(), 0) },
                    enabled = results.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play shuffled")
                }
            }

            if (results.isEmpty()) {
                EmptyMessage("Nothing found", "No videos match these tags.")
            } else {
                VideoGrid(
                    videos = results,
                    meta = meta,
                    onOpen = { index -> vm.openFeed(resultTitle, results, index) },
                )
            }
        }
    }

    if (managing) {
        ManageTagsDialog(
            tags = allTags,
            onRename = { from, to ->
                vm.renameTag(from, to)
                selected = selected.map { if (it == from) normalizeTag(to) ?: it else it }.distinct()
            },
            onDelete = { tag ->
                vm.deleteTag(tag)
                selected = selected - tag
            },
            onDismiss = { managing = false },
        )
    }
}

@Composable
private fun ManageTagsDialog(
    tags: List<TagCount>,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Manage tags") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(tags, key = { it.tag }) { t ->
                    ListItem(
                        headlineContent = { Text("#${t.tag}") },
                        supportingContent = { Text("${t.count} video${if (t.count == 1) "" else "s"}") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { renaming = t.tag }) { Icon(Icons.Filled.Edit, "Rename") }
                                IconButton(onClick = { deleting = t.tag }) { Icon(Icons.Filled.Delete, "Delete") }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        },
    )

    renaming?.let { tag ->
        var text by remember(tag) { mutableStateOf(tag) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename #$tag") },
            text = {
                Column {
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, prefix = { Text("#") })
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "If the new name already exists, the tags are merged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(tag, text)
                        renaming = null
                    },
                    enabled = normalizeTag(text) != null,
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    deleting?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete #$tag?") },
            text = { Text("The tag is removed from every video. The videos themselves are not touched.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(tag)
                    deleting = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
