package com.oflinetok.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.oflinetok.app.data.BackupManager
import com.oflinetok.app.data.MetaStore
import com.oflinetok.app.data.mergedWith
import com.oflinetok.app.data.VideoItem
import com.oflinetok.app.data.VideoMeta
import com.oflinetok.app.data.VideoRepository
import com.oflinetok.app.data.normalizeTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A feed opened from search or the library, e.g. "all videos tagged #cats". */
data class FeedRequest(val title: String, val items: List<VideoItem>, val startIndex: Int)

data class TagCount(val tag: String, val count: Int)

data class BackupStatus(val lastSavedAt: Long, val error: String? = null)

/** Shown in a dialog after the user restores a backup file. */
data class RestoreMessage(val title: String, val body: String)

data class FolderCount(val folder: String, val count: Int)

/**
 * What the Home feed shows when its filter tab is active. Within a group any match counts
 * (#cats or #dogs); when both groups are set a video must match each (#cats from Camera).
 */
data class FeedFilter(val tags: Set<String> = emptySet(), val folders: Set<String> = emptySet()) {
    val isEmpty: Boolean get() = tags.isEmpty() && folders.isEmpty()

    fun matches(video: VideoItem, meta: VideoMeta?): Boolean =
        (tags.isEmpty() || meta?.tags.orEmpty().any { it in tags }) &&
            (folders.isEmpty() || video.folder in folders)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = VideoRepository(app)
    private val metaStore = MetaStore(app)
    private val saveLock = Mutex()
    private val prefs = app.getSharedPreferences("feed", Context.MODE_PRIVATE)
    private val backup = BackupManager(app)
    private var exportJob: Job? = null

    private val _backupStatus = MutableStateFlow(BackupStatus(backup.lastBackupAt))
    val backupStatus: StateFlow<BackupStatus> = _backupStatus.asStateFlow()

    private val _restoreMessage = MutableStateFlow<RestoreMessage?>(null)
    val restoreMessage: StateFlow<RestoreMessage?> = _restoreMessage.asStateFlow()

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

    /** The saved Home filter; kept even while the "For You" tab is showing. */
    private val _feedFilter = MutableStateFlow(
        FeedFilter(
            tags = prefs.getStringSet("tags", null).orEmpty().toSet(),
            folders = prefs.getStringSet("folders", null).orEmpty().toSet(),
        ),
    )
    val feedFilter: StateFlow<FeedFilter> = _feedFilter.asStateFlow()

    /** True when Home shows the filtered feed instead of all videos. */
    private val _filterActive = MutableStateFlow(
        prefs.getBoolean("active", false) && !_feedFilter.value.isEmpty,
    )
    val filterActive: StateFlow<Boolean> = _filterActive.asStateFlow()

    /** Every gallery folder with its number of videos, largest first. */
    val allFolders: StateFlow<List<FolderCount>> = _videos.map { videos ->
        videos.mapNotNull { it.folder }
            .groupingBy { it }.eachCount()
            .map { FolderCount(it.key, it.value) }
            .sortedWith(compareByDescending<FolderCount> { it.count }.thenBy { it.folder.lowercase() })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Every tag used on a video that still exists, most used first. */
    val allTags: StateFlow<List<TagCount>> = combine(_videos, _meta) { videos, meta ->
        videos.flatMap { meta[it.id]?.tags.orEmpty() }
            .groupingBy { it }.eachCount()
            .map { TagCount(it.key, it.value) }
            .sortedWith(compareByDescending<TagCount> { it.count }.thenBy { it.tag })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val metaLoad = viewModelScope.launch {
        _meta.value = withContext(Dispatchers.IO) { metaStore.load() }
    }

    /** forceScan re-walks all storage instead of using the last few minutes' scan. */
    fun load(forceScan: Boolean = false) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            metaLoad.join() // A tag-filtered feed needs the tags before it can shuffle.
            val list = withContext(Dispatchers.IO) {
                runCatching { repository.loadVideos(forceScan) }.getOrDefault(emptyList())
            }
            val changed = list.map { it.id } != _videos.value.map { it.id }
            _videos.value = list
            if (changed || mainFeed.isEmpty()) reshuffle()
            _loading.value = false
            _loaded.value = true
            scheduleExport() // Also creates the first backup for tags made before backups existed.
        }
    }

    /** The videos the Home feed draws from right now. */
    private fun feedSource(): List<VideoItem> {
        if (!_filterActive.value) return _videos.value
        val filter = _feedFilter.value
        val meta = _meta.value
        return _videos.value.filter { filter.matches(it, meta[it.id]) }
    }

    /** Switches Home between "For You" (all videos) and the saved filter. */
    fun setFilterActive(active: Boolean) {
        val value = active && !_feedFilter.value.isEmpty
        if (value == _filterActive.value) return
        _filterActive.value = value
        prefs.edit().putBoolean("active", value).apply()
        reshuffle()
    }

    /** Saves a new filter and shows it; an empty filter falls back to "For You". */
    fun applyFeedFilter(filter: FeedFilter) {
        _feedFilter.value = filter
        _filterActive.value = !filter.isEmpty
        prefs.edit()
            .putStringSet("tags", filter.tags)
            .putStringSet("folders", filter.folders)
            .putBoolean("active", !filter.isEmpty)
            .apply()
        reshuffle()
    }

    fun reshuffle() {
        mainFeed.clear()
        mainFeed.addAll(feedSource().shuffled())
        mainFeedPage = 0
        feedVersion++
    }

    /** Appends another random round of videos when the user nears the end of the feed. */
    fun extendMainFeed(page: Int) {
        val all = feedSource()
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
        _feedFilter.value.let { f ->
            if (from in f.tags) applyFeedFilterKeepingTab(f.copy(tags = f.tags - from + to))
        }
    }

    fun deleteTag(tag: String) {
        _meta.update { all -> all.mapValues { (_, m) -> m.copy(tags = m.tags - tag) } }
        persist()
        _feedFilter.value.let { f ->
            if (tag in f.tags) applyFeedFilterKeepingTab(f.copy(tags = f.tags - tag))
        }
    }

    /** Updates the saved filter after a tag rename/delete without switching tabs. */
    private fun applyFeedFilterKeepingTab(filter: FeedFilter) {
        if (_filterActive.value) {
            applyFeedFilter(filter)
            return
        }
        _feedFilter.value = filter
        prefs.edit()
            .putStringSet("tags", filter.tags)
            .putStringSet("folders", filter.folders)
            .apply()
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

    /** Debounced, so a burst of edits writes the backup file once. */
    private fun scheduleExport() {
        if (!_loaded.value || _meta.value.values.all { it.isEmpty }) return
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            delay(2000)
            runExport()
        }
    }

    /** Writes a pending backup right away, e.g. when the app goes to the background. */
    fun flushExport() {
        if (exportJob?.isActive != true) return
        exportJob?.cancel()
        exportJob = viewModelScope.launch { runExport() }
    }

    fun exportNow() {
        exportJob?.cancel()
        exportJob = viewModelScope.launch { runExport() }
    }

    private suspend fun runExport() {
        val videos = _videos.value
        val meta = _meta.value
        val result = withContext(Dispatchers.IO) { runCatching { backup.export(videos, meta) } }
        _backupStatus.value = result.fold(
            onSuccess = { BackupStatus(backup.lastBackupAt) },
            onFailure = { BackupStatus(backup.lastBackupAt, it.message ?: "Couldn't write the backup") },
        )
    }

    /** Merges a backup file into the current tags; nothing existing is removed. */
    fun restore(uri: Uri) {
        viewModelScope.launch {
            val videos = _videos.value
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val entries = backup.read(uri)
                    entries.size to BackupManager.match(entries, videos)
                }
            }
            result.onSuccess { (total, matched) ->
                if (matched.isNotEmpty()) {
                    _meta.update { all ->
                        all + matched.mapValues { (id, m) -> all[id]?.mergedWith(m) ?: m }
                    }
                    persist()
                }
                val missing = total - matched.size
                _restoreMessage.value = RestoreMessage(
                    title = if (matched.isEmpty()) "Nothing restored" else "Tags restored",
                    body = buildString {
                        append("Restored ${matched.size} of $total videos from the backup.")
                        if (missing > 0) {
                            append(" $missing weren't found on this phone. They may have been deleted, renamed or moved.")
                        }
                    },
                )
            }.onFailure {
                _restoreMessage.value = RestoreMessage(
                    "Couldn't restore",
                    it.message?.takeIf { m -> "OflineTok" in m } ?: "That file couldn't be read as an OflineTok backup.",
                )
            }
        }
    }

    fun dismissRestoreMessage() {
        _restoreMessage.value = null
    }

    private fun persist() {
        scheduleExport()
        viewModelScope.launch(Dispatchers.IO) {
            // Reading the latest value inside the lock means the final write always wins.
            saveLock.withLock { metaStore.save(_meta.value) }
        }
    }
}
