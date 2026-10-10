package com.oflinetok.app.ui

import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.oflinetok.app.data.VideoItem
import com.oflinetok.app.data.VideoMeta
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val overlayText = TextStyle(
    color = Color.White,
    shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 2f), 6f),
)

/** The endless, shuffled Home feed: all videos ("For You") or the saved tag/folder filter. */
@Composable
fun HomeFeed(vm: AppViewModel, active: Boolean) {
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val videos by vm.videos.collectAsStateWithLifecycle()
    val meta by vm.meta.collectAsStateWithLifecycle()
    val filter by vm.feedFilter.collectAsStateWithLifecycle()
    val filterActive by vm.filterActive.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()
    val allFolders by vm.allFolders.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }

    val topBar: @Composable BoxScope.() -> Unit = {
        HomeTabs(
            filter = filter,
            filterActive = filterActive,
            onForYou = { if (filterActive) vm.setFilterActive(false) else vm.reshuffle() },
            onFilterTab = {
                // First tap switches to the saved filter; tapping it again (or with none saved) edits it.
                if (filterActive || filter.isEmpty) picking = true else vm.setFilterActive(true)
            },
            modifier = Modifier.align(Alignment.Center),
        )
        IconButton(onClick = vm::reshuffle, modifier = Modifier.align(Alignment.CenterEnd)) {
            Icon(Icons.Filled.Shuffle, "Shuffle", tint = Color.White)
        }
    }

    when {
        !loaded -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        videos.isEmpty() -> EmptyMessage(
            "No videos found",
            "Record or download some videos, then come back. They'll show up here automatically.",
        )
        vm.mainFeed.isEmpty() -> Box(Modifier.fillMaxSize()) {
            // Only reachable when the filter matches nothing, e.g. its tags were removed from every video.
            EmptyMessage("Nothing to show", "No videos match ${filter.label()}. Tap it above to change the filter.")
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
                content = topBar,
            )
        }
        else -> key(vm.feedVersion) {
            VideoFeed(
                vm = vm,
                items = vm.mainFeed,
                initialPage = vm.mainFeedPage.coerceAtMost(vm.mainFeed.lastIndex),
                active = active && !picking,
                onPageSettled = { page ->
                    vm.mainFeedPage = page
                    vm.extendMainFeed(page)
                },
                topBar = topBar,
            )
        }
    }

    if (picking) {
        FeedFilterSheet(
            current = filter,
            allTags = allTags,
            allFolders = allFolders,
            videos = videos,
            meta = meta,
            onApply = {
                vm.applyFeedFilter(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** TikTok-style "For You | #cats" switch at the top of Home. */
@Composable
private fun HomeTabs(
    filter: FeedFilter,
    filterActive: Boolean,
    onForYou: () -> Unit,
    onFilterTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.padding(horizontal = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HomeTab("For You", selected = !filterActive, onClick = onForYou)
        Text("|", style = overlayText.copy(color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp))
        HomeTab(filter.label(), selected = filterActive, onClick = onFilterTab, dropdown = true)
    }
}

@Composable
private fun HomeTab(label: String, selected: Boolean, onClick: () -> Unit, dropdown: Boolean = false) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val color = if (selected) Color.White else Color.White.copy(alpha = 0.6f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = overlayText.copy(
                    color = color,
                    fontSize = 17.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
            if (dropdown) {
                Icon(Icons.Filled.ArrowDropDown, "Choose filter", tint = color, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .size(width = 24.dp, height = 2.dp)
                .background(if (selected) Color.White else Color.Transparent),
        )
    }
}

/** A finite feed opened from search, a tag, or the library. Shown above everything else. */
@Composable
fun OpenedFeed(vm: AppViewModel, request: FeedRequest) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        key(request) {
            VideoFeed(
                vm = vm,
                items = request.items,
                initialPage = request.startIndex,
                active = true,
                onPageSettled = {},
            ) {
                IconButton(onClick = vm::closeFeed, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    request.title,
                    style = overlayText.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 56.dp),
                )
            }
        }
    }
}

@Composable
private fun VideoFeed(
    vm: AppViewModel,
    items: List<VideoItem>,
    initialPage: Int,
    active: Boolean,
    onPageSettled: (Int) -> Unit,
    topBar: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val meta by vm.meta.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()

    val player = remember {
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    // Pages are tracked so a newly settled page never inherits the previous page's state.
    var loadedPage by remember { mutableIntStateOf(-1) }
    var renderedPage by remember { mutableIntStateOf(-1) }
    var errorPage by remember { mutableIntStateOf(-1) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                renderedPage = loadedPage
            }

            override fun onPlayerError(error: PlaybackException) {
                errorPage = loadedPage
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val pagerState = rememberPagerState(initialPage.coerceAtLeast(0)) { items.size }
    val settledPage = pagerState.settledPage
    val current = items.getOrNull(settledPage)
    var userPaused by remember { mutableStateOf(false) }
    var tagEditorFor by remember { mutableStateOf<VideoItem?>(null) }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect(onPageSettled)
    }

    LaunchedEffect(settledPage, current?.id) {
        userPaused = false
        loadedPage = settledPage
        if (current != null) {
            player.setMediaItem(MediaItem.fromUri(current.uri))
            player.prepare()
        } else {
            player.stop()
        }
    }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(active, resumed, userPaused, tagEditorFor) {
        player.playWhenReady = active && resumed && !userPaused && tagEditorFor == null
    }

    Box(Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { page -> "$page-${items.getOrNull(page)?.id}" },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items.getOrNull(page) ?: return@VerticalPager
            val isCurrent = page == settledPage
            VideoPage(
                item = item,
                meta = meta[item.id] ?: VideoMeta(),
                player = if (isCurrent) player else null,
                firstFrameRendered = isCurrent && renderedPage == page,
                playError = isCurrent && errorPage == page,
                paused = isCurrent && userPaused,
                onTogglePause = { userPaused = !userPaused },
                onLike = { vm.toggleLike(item.id) },
                onDoubleTapLike = { vm.like(item.id) },
                onFavorite = { vm.toggleFavorite(item.id) },
                onEditTags = { tagEditorFor = item },
                onShare = { shareVideo(context, item) },
                onTagClick = { tag ->
                    val tagged = vm.videos.value.filter { tag in vm.metaFor(it.id).tags }.shuffled()
                    vm.openFeed("#$tag", tagged, 0)
                },
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)))
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            content = topBar,
        )
    }

    tagEditorFor?.let { item ->
        TagEditorDialog(
            meta = meta[item.id] ?: VideoMeta(),
            allTags = allTags,
            onAdd = { vm.addTag(item.id, it) },
            onRemove = { vm.removeTag(item.id, it) },
            onDismiss = { tagEditorFor = null },
        )
    }
}

@Composable
private fun VideoPage(
    item: VideoItem,
    meta: VideoMeta,
    player: ExoPlayer?,
    firstFrameRendered: Boolean,
    playError: Boolean,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onLike: () -> Unit,
    onDoubleTapLike: () -> Unit,
    onFavorite: () -> Unit,
    onEditTags: () -> Unit,
    onShare: () -> Unit,
    onTagClick: (String) -> Unit,
) {
    var heartAt by remember { mutableStateOf(Offset.Zero) }
    var heartKey by remember { mutableIntStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onTogglePause() },
                    onDoubleTap = { pos ->
                        heartAt = pos
                        heartKey++
                        onDoubleTapLike()
                    },
                )
            },
    ) {
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        this.player = player
                    }
                },
                update = { it.player = player },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Thumbnail covers the player until the first frame is drawn, so swipes never flash black.
        if (!firstFrameRendered) {
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (playError) {
            Text(
                "Can't play this video",
                style = overlayText,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (paused) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Paused",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp),
            )
        }

        if (heartKey > 0) {
            key(heartKey) { BurstHeart(heartAt) }
        }

        // Bottom shade for legibility of the caption.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(220.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 88.dp, bottom = 22.dp),
        ) {
            item.folder?.let {
                Text("@$it", style = overlayText.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp))
                Spacer(Modifier.height(4.dp))
            }
            Text(
                item.name,
                style = overlayText.copy(fontSize = 14.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    meta.tags.sorted().take(6).forEach { tag ->
                        Text(
                            "#$tag",
                            style = overlayText.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                            maxLines = 1,
                            modifier = Modifier.clickable { onTagClick(tag) },
                        )
                    }
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SideAction(
                icon = if (meta.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                tint = if (meta.liked) TokPink else Color.White,
                label = if (meta.liked) "Liked" else "Like",
                onClick = onLike,
            )
            SideAction(
                icon = if (meta.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                tint = if (meta.favorite) TokGold else Color.White,
                label = "Favorite",
                onClick = onFavorite,
            )
            SideAction(
                icon = Icons.Filled.Tag,
                tint = if (meta.tags.isNotEmpty()) TokCyan else Color.White,
                label = if (meta.tags.isEmpty()) "Tag" else "${meta.tags.size} tags",
                onClick = onEditTags,
            )
            SideAction(
                icon = Icons.Filled.Share,
                tint = Color.White,
                label = "Share",
                onClick = onShare,
            )
        }

        if (player != null) {
            SeekBar(
                player = player,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun SideAction(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(38.dp))
        }
        Text(label, style = overlayText.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** The heart that pops where the user double-tapped. */
@Composable
private fun BurstHeart(at: Offset) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(750)) }
    val p = progress.value
    if (p >= 1f) return
    val sizePx = with(LocalDensity.current) { 110.dp.toPx() }
    Icon(
        Icons.Filled.Favorite,
        contentDescription = null,
        tint = TokPink,
        modifier = Modifier
            .offset { IntOffset((at.x - sizePx / 2).roundToInt(), (at.y - sizePx / 2 - p * 120).roundToInt()) }
            .size(110.dp)
            .scale(if (p < 0.2f) 0.6f + p * 3f else 1.2f)
            .alpha(if (p < 0.6f) 1f else (1f - p) / 0.4f),
    )
}

/** Thin progress bar at the bottom; drag or tap on it to seek. */
@Composable
private fun SeekBar(player: Player, modifier: Modifier = Modifier) {
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(100)
        }
    }

    val fraction = when {
        dragging -> dragFraction
        duration > 0 -> (position.toFloat() / duration).coerceIn(0f, 1f)
        else -> 0f
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(20.dp)
            .pointerInput(player) {
                detectTapGestures { pos ->
                    if (duration > 0) player.seekTo((pos.x / size.width * duration).toLong())
                }
            }
            .pointerInput(player) {
                detectHorizontalDragGestures(
                    onDragStart = { pos ->
                        dragging = true
                        dragFraction = (pos.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        if (duration > 0) player.seekTo((dragFraction * duration).toLong())
                        scope.launch {
                            delay(150)
                            dragging = false
                        }
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            },
        contentAlignment = Alignment.BottomStart,
    ) {
        val barHeight = if (dragging) 5.dp else 2.dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(Color.White.copy(alpha = 0.25f)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(barHeight)
                .background(Color.White.copy(alpha = if (dragging) 1f else 0.8f)),
        )
    }
}

@Composable
fun EmptyMessage(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (action != null) {
            Spacer(Modifier.height(12.dp))
            action()
        }
    }
}
