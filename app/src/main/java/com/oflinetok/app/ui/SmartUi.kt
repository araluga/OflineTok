package com.oflinetok.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.oflinetok.app.ai.ModelFiles
import com.oflinetok.app.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val MODEL_MB = (ModelFiles.TOTAL_BYTES / 1_000_000).toInt()

/** Search → ✨ Smart: find videos by describing them. Sets up the model first if needed. */
@Composable
fun SmartSearch(vm: AppViewModel, ai: AiViewModel = viewModel()) {
    val state by ai.state.collectAsStateWithLifecycle()
    val videos by vm.visibleVideos.collectAsStateWithLifecycle()
    val meta by vm.meta.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var searchedFor by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VideoItem>?>(null) }
    var searching by remember { mutableStateOf(false) }

    val ready = state as? AiState.Ready
    if (ready == null) {
        SmartSetup(state, ai)
        return
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        focus.clearFocus()
        searching = true
        scope.launch {
            results = runCatching { ai.search(q, videos) }.getOrDefault(emptyList())
            searchedFor = q
            searching = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Describe a video, e.g. cat on a sofa") },
            leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, tint = TokCyan) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        query = ""
                        results = null
                    }) { Icon(Icons.Filled.Clear, "Clear") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        AiProgressLine(ready)

        val found = results
        when {
            searching -> Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Searching…")
            }
            found == null -> EmptyMessage(
                "Search by what's in the video",
                "Type a description in English, like \"dog on the beach\", \"night city\" or \"people dancing\", " +
                    "and press search. It works on untagged videos too. Everything runs on your phone.",
            )
            found.isEmpty() -> EmptyMessage(
                "No clear matches",
                "Try describing it differently, in English." +
                    if (ready.indexing) " Videos still being analyzed aren't searched yet." else "",
            )
            else -> {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${found.size} matches, best first",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { vm.openFeed("✨ $searchedFor", found, 0) },
                        contentPadding = PaddingValues(horizontal = 14.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play")
                    }
                }
                VideoGrid(
                    videos = found,
                    meta = meta,
                    onOpen = { index -> vm.openFeed("✨ $searchedFor", found, index) },
                )
            }
        }
    }
}

/** "Analyzing videos 120 / 800" while the background analysis runs. */
@Composable
fun AiProgressLine(ready: AiState.Ready) {
    if (!ready.indexing || ready.total == 0) return
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            "Analyzing your videos: ${ready.indexed} of ${ready.total}. About a second each; " +
                "only while the app is open.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ready.indexed.toFloat() / ready.total },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SmartSetup(state: AiState, ai: AiViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.AutoAwesome, null, tint = TokCyan, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("Smart search & tag suggestions", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "• Find videos by describing them: \"cat on a sofa\", \"snowy mountains\".\n" +
                "• Get tag suggestions: OflineTok learns from the videos you've tagged and suggests " +
                "the same tags for similar videos.\n\n" +
                "This needs a one-time download of an AI model ($MODEL_MB MB, Wi-Fi recommended). " +
                "After that everything runs on your phone. Your videos are never uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        when (state) {
            is AiState.Downloading -> {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    "Downloading… ${(state.progress * MODEL_MB).toInt()} of $MODEL_MB MB. Keep the app open; " +
                        "if it's interrupted, it continues where it stopped.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = ai::cancelInstall) { Text("Cancel") }
            }
            is AiState.Failed -> {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = ai::install) { Text("Try again") }
            }
            else -> Button(onClick = ai::install) { Text("Download ($MODEL_MB MB)") }
        }
    }
}

/**
 * Banner on the Search screen: "Tag suggestions for 12 untagged videos". Opens a feed of
 * those videos with tap-to-add suggestion chips.
 */
@Composable
fun SuggestionsBanner(vm: AppViewModel, ai: AiViewModel = viewModel()) {
    val state by ai.state.collectAsStateWithLifecycle()
    val version by ai.version.collectAsStateWithLifecycle()
    val videos by vm.visibleVideos.collectAsStateWithLifecycle()
    val meta by vm.meta.collectAsStateWithLifecycle()
    var withSuggestions by remember { mutableStateOf<List<VideoItem>>(emptyList()) }

    // Comparing every untagged video with every tagged one can take a moment: do it off the UI thread.
    LaunchedEffect(state is AiState.Ready, version, videos, meta) {
        withSuggestions = if (state !is AiState.Ready) emptyList() else withContext(Dispatchers.Default) {
            videos.filter { meta[it.id]?.tags.isNullOrEmpty() && ai.suggestTags(it.id, meta, videos).isNotEmpty() }
        }
    }
    if (withSuggestions.isEmpty()) return

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { vm.openFeed("✨ Suggested tags", withSuggestions, 0, suggestions = true) }
            .background(TokCyan.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.AutoAwesome, null, tint = TokCyan)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Tag suggestions for ${withSuggestions.size} untagged video${if (withSuggestions.size == 1) "" else "s"}",
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Watch them and tap a suggestion to add it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Row in Me → ⓘ: model status and a button to free the space. */
@Composable
fun SmartFeaturesRow(ai: AiViewModel = viewModel()) {
    val state by ai.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    Column {
        Text("Smart features (AI)", fontWeight = FontWeight.SemiBold)
        Text(
            when (val s = state) {
                is AiState.Ready -> "Installed ($MODEL_MB MB). Analyzed ${s.indexed} of ${s.total} videos."
                is AiState.Downloading -> "Downloading…"
                is AiState.Failed -> "Download failed. Set it up again in Search → ✨ Smart."
                AiState.Off -> "Not installed. Set it up in Search → ✨ Smart."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state is AiState.Ready) {
            OutlinedButton(onClick = { confirm = true }) { Text("Delete AI model") }
        }
    }
    if (confirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete the AI model?") },
            text = { Text("Frees $MODEL_MB MB. Smart search and tag suggestions stop until you download it again. Your tags are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    ai.remove()
                    confirm = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
