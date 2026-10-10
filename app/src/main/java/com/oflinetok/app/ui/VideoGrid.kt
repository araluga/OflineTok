package com.oflinetok.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import coil.compose.AsyncImage
import com.oflinetok.app.data.VideoItem
import com.oflinetok.app.data.VideoMeta

/** Three-column grid of 9:16 thumbnails, like a TikTok profile. */
@Composable
fun VideoGrid(
    videos: List<VideoItem>,
    meta: Map<Long, VideoMeta>,
    onOpen: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize(),
    ) {
        items(videos.size, key = { videos[it].id }) { index ->
            val video = videos[index]
            val m = meta[video.id]
            Box(
                Modifier
                    .aspectRatio(9f / 16f)
                    .background(Color(0xFF1A1A1A))
                    .clickable { onOpen(index) },
            ) {
                AsyncImage(
                    model = video.uri,
                    contentDescription = video.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(formatDuration(video.durationMs), color = Color.White, fontSize = 11.sp)
                        if (m?.liked == true) {
                            Icon(Icons.Filled.Favorite, null, tint = TokPink, modifier = Modifier.size(12.dp))
                        }
                        if (m?.favorite == true) {
                            Icon(Icons.Filled.Star, null, tint = TokGold, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
