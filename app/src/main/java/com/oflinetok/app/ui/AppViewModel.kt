package com.oflinetok.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oflinetok.app.data.MetaStore
import com.oflinetok.app.data.VideoItem
import com.oflinetok.app.data.VideoMeta
import com.oflinetok.app.data.VideoRepository
import com.oflinetok.app.data.normalizeTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A feed opened from search or the library, e.g. "all videos tagged #cats". */
data class FeedRequest(val title: String, val items: List<VideoItem>, val startIndex: Int)

data class TagCount(val tag: String, val count: Int)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = VideoRepository(app)
    private val metaStore = MetaStore(app)
    private val saveLock = Mutex()

    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _meta = MutableStateFlow<Map<Long, VideoMeta>>(emptyMap())
    val meta: StateFlow<Map<Long, VideoMeta>> = _meta.asStateFlow()

    private val _openedFeed = MutableStateFlow<FeedRequest?>(null)
    val openedFeed: StateFlow<FeedRequest?> = _openedFeed.asStateFlow()

    /** The endless, randomly ordered "For You" feed. Grows as the user scrolls. */
    val mainFeed = mutableStateListOf<VideoItem>()
    var mainFeedPage = 0

    /** Bumped on every reshuffle so the feed UI restarts from the top. */
    var feedVersion by mutableIntStateOf(0)
        private set

    /** Every tag used on a video that still exists, most used first. */
    val allTags: StateFlow<List<TagCount>> = combine(_videos, _meta) { videos, meta ->
        videos.flatMap { meta[it.id]?.tags.orEmpty() }
            .groupingBy { it }.eachCount()
            .map { TagCount(it.key, it.value) }
            .sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.tag })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            _meta.value = withContext(Dispatchers.IO) { metaStore.load() }
        }
    }

    fun load() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            val list = withContext(Dispatchers.IO) {
                runCatching { repository.loadVideos() }.getOrDefault(emptyList())
            }
            val changed = list.map { it.id } != _videos.value.map { it.id }
            _videos.value = list
            if (changed || mainFeed.isEmpty()) reshuffle()
            _loading.value = false
            _loaded.value = true
        }
    }

    fun reshuffle() {
        mainFeed.clear()
        mainFeed.addAll(_videos.value.shuffled())
        mainFeedPage = 0
        feedVersion++
    }

    /** Appends another random round of videos when the user nears the end of the feed. */
    fun extendMainFeed(page: Int) {
        val all = _videos.value
        if (all.isEmpty() || page < mainFeed.size - 3) return
        var next = all.shuffled()
        // Avoid showing the same video twice in a row across rounds.
        if (all.size > 1 && next.first().id == mainFeed.lastOrNull()?.id) {
            next = next.drop(1) + next.first()
        }
        mainFeed.addAll(next)
    }

    fun metaFor(id: Long): VideoMeta = _meta.value[id] ?: VideoMeta()

    fun toggleLike(id: Long) = edit(id) { it.copy(liked = !it.liked) }

    fun like(id: Long) = edit(id) { it.copy(liked = true) }

    fun toggleFavorite(id: Long) = edit(id) { it.copy(favorite = !it.favorite) }

    fun addTag(id: Long, raw: String) {
        val tag = normalizeTag(raw) ?: return
        edit(id) { it.copy(tags = it.tags + tag) }
    }

    fun removeTag(id: Long, tag: String) = edit(id) { it.copy(tags = it.tags - tag) }

    /** Renames a tag on every video, merging it into an existing tag if needed. */
    fun renameTag(from: String, toRaw: String) {
        val to = normalizeTag(toRaw) ?: return
        if (from == to) return
        _meta.update { all ->
            all.mapValues { (_, m) ->
                if (from in m.tags) m.copy(tags = m.tags - from + to) else m
            }
        }
        persist()
    }

    fun deleteTag(tag: String) {
        _meta.update { all -> all.mapValues { (_, m) -> m.copy(tags = m.tags - tag) } }
        persist()
    }

    fun openFeed(title: String, items: List<VideoItem>, startIndex: Int) {
        if (items.isEmpty()) return
        _openedFeed.value = FeedRequest(title, items, startIndex.coerceIn(items.indices))
    }

    fun closeFeed() {
        _openedFeed.value = null
    }

    private fun edit(id: Long, change: (VideoMeta) -> VideoMeta) {
        _meta.update { all -> all + (id to change(all[id] ?: VideoMeta())) }
        persist()
    }

    private fun persist() {
        viewModelScope.launch(Dispatchers.IO) {
            // Reading the latest value inside the lock means the final write always wins.
            saveLock.withLock { metaStore.save(_meta.value) }
        }
    }
}
