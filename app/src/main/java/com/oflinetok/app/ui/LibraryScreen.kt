package com.oflinetok.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private data class LibraryTab(val title: String, val icon: ImageVector, val tint: Color)

private val libraryTabs = listOf(
    LibraryTab("Favorites", Icons.Filled.Star, TokGold),
    LibraryTab("Liked", Icons.Filled.Favorite, TokPink),
    LibraryTab("All", Icons.Filled.Apps, Color.White),
)

@Composable
fun LibraryScreen(vm: AppViewModel, partialOnly: Boolean, onManageAccess: () -> Unit) {
    val videos by vm.videos.collectAsStateWithLifecycle()
    val meta by vm.meta.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val favorites = videos.filter { meta[it.id]?.favorite == true }
    val liked = videos.filter { meta[it.id]?.liked == true }
    val shown = when (tab) {
        0 -> favorites
        1 -> liked
        else -> videos
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(
                "My videos",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.Center),
            )
            Box(Modifier.align(Alignment.CenterEnd)) {
                if (loading) {
                    CircularProgressIndicator(Modifier.padding(12.dp).padding(2.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = vm::load) { Icon(Icons.Filled.Refresh, "Rescan gallery") }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Stat(videos.size, "Videos")
            Stat(liked.size, "Likes")
            Stat(favorites.size, "Favorites")
            Stat(allTags.size, "Tags")
        }

        if (partialOnly) {
            Text(
                "You gave access to selected videos only. Tap to choose more.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageAccess)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
            )
        }

        TabRow(
            selectedTabIndex = tab,
            containerColor = Color.Black,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(positions[tab]),
                    color = Color.White,
                )
            },
        ) {
            libraryTabs.forEachIndexed { i, t ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = { Icon(t.icon, t.title, tint = if (tab == i) t.tint else Color.Gray) },
                )
            }
        }

        if (shown.isEmpty()) {
            when (tab) {
                0 -> EmptyMessage("No favorites yet", "Tap the star on a video to save it here.")
                1 -> EmptyMessage("No liked videos yet", "Tap the heart, or double-tap a video, to like it.")
                else -> EmptyMessage("No videos found", "Videos in your gallery will show up here.")
            }
        } else {
            VideoGrid(
                videos = shown,
                meta = meta,
                onOpen = { index -> vm.openFeed(libraryTabs[tab].title, shown, index) },
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun Stat(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
