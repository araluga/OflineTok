package com.oflinetok.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.oflinetok.app.data.VideoMeta
import com.oflinetok.app.data.normalizeTag

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditorDialog(
    meta: VideoMeta,
    allTags: List<TagCount>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
    suggested: List<String> = emptyList(),
    ideas: List<String> = emptyList(),
) {
    var input by remember { mutableStateOf("") }
    val typed = normalizeTag(input)

    fun submit() {
        // Commas let the user add several tags at once: "cat, funny, 2024".
        input.split(',').forEach { onAdd(it) }
        input = ""
    }

    val suggestions = allTags
        .map { it.tag }
        .filter { it !in meta.tags && it !in suggested && it !in ideas && (typed == null || it.contains(typed)) }
        .take(30)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Tags") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Add a tag, e.g. cats") },
                    prefix = { Text("#") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = ::submit, enabled = typed != null) {
                            Icon(Icons.Filled.Add, "Add tag")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))
                if (meta.tags.isEmpty()) {
                    Text(
                        "This video has no tags yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text("On this video", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        meta.tags.sorted().forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { onRemove(tag) },
                                label = { Text("#$tag") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove $tag",
                                        modifier = Modifier.height(InputChipDefaults.IconSize),
                                    )
                                },
                            )
                        }
                    }
                }

                if (suggested.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("✨ Suggested for this video", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggested.forEach { tag ->
                            SuggestionChip(
                                onClick = { onAdd(tag) },
                                label = { Text("+ #$tag") },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = TokCyan.copy(alpha = 0.18f),
                                ),
                            )
                        }
                    }
                }

                if (ideas.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("\u2728 New tag ideas", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ideas.forEach { tag ->
                            SuggestionChip(
                                onClick = { onAdd(tag) },
                                label = { Text("+ #$tag") },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = TokPink.copy(alpha = 0.18f),
                                ),
                            )
                        }
                    }
                }

                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Your tags", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { tag ->
                            SuggestionChip(
                                onClick = {
                                    onAdd(tag)
                                    input = ""
                                },
                                label = { Text("#$tag") },
                            )
                        }
                    }
                }
            }
        },
    )
}
