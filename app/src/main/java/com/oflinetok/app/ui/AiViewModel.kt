package com.oflinetok.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oflinetok.app.ai.ClipModel
import com.oflinetok.app.ai.ClipPreprocess
import com.oflinetok.app.ai.EmbeddingStore
import com.oflinetok.app.ai.FrameGrabber
import com.oflinetok.app.ai.ModelFiles
import com.oflinetok.app.ai.TagVocabulary
import com.oflinetok.app.data.VideoItem
import com.oflinetok.app.data.VideoMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.util.concurrent.ConcurrentHashMap

sealed interface AiState {
    /** Model not downloaded. */
    data object Off : AiState
    data class Downloading(val progress: Float) : AiState
    data class Failed(val message: String) : AiState
    /** Model ready; [indexed] of [total] videos analyzed so far. */
    data class Ready(val indexed: Int, val total: Int, val indexing: Boolean) : AiState
}

/**
 * On-device "smart features" built on CLIP:
 *  - Tag suggestions: a video gets the tags of the tagged videos that look most like it
 *    (plus tags whose name matches what CLIP sees, for English tag names).
 *  - Smart search: find videos by describing them in words.
 * Each video is analyzed once (three frames) and its embedding saved.
 */
class AiViewModel(app: Application) : AndroidViewModel(app) {

    private val storeDir = File(app.filesDir, "ai")
    private val store = EmbeddingStore(storeDir)
    private val embeddings = ConcurrentHashMap<Long, EmbeddingStore.Entry>()
    private var model: ClipModel? = null
    private var installJob: Job? = null
    private var indexJob: Job? = null
    private var indexedFor: List<VideoItem>? = null
    private var loadJob: Job? = null

    /** One analysis at a time, on a single background thread. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val worker = Dispatchers.Default.limitedParallelism(1)

    private val _state = MutableStateFlow<AiState>(AiState.Off)
    val state: StateFlow<AiState> = _state.asStateFlow()

    /** Bumped as embeddings arrive, so suggestions on screen refresh. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    /** Text embeddings of tag names ("a photo of {tag}") for name-based suggestions. */
    private val tagTexts = ConcurrentHashMap<String, FloatArray>()

    /** Text embeddings of [TagVocabulary] concepts, for ideas of brand-new tags. */
    @Volatile
    private var vocabulary: List<FloatArray>? = null
    private var vocabularyJob: Job? = null
    private val vocabularyFile = File(storeDir, "vocabulary-v${ModelFiles.VERSION}.bin")

    init {
        if (ModelFiles.isInstalled(app)) {
            _state.value = AiState.Ready(0, 0, indexing = false)
            loadJob = viewModelScope.launch(Dispatchers.IO) {
                embeddings.putAll(store.load())
                _version.update { it + 1 }
            }
        }
    }

    @Synchronized
    private fun model(): ClipModel =
        model ?: ClipModel(ModelFiles.dir(getApplication())).also { model = it }

    fun install() {
        if (installJob?.isActive == true) return
        _state.value = AiState.Downloading(0f)
        installJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                ModelFiles.install(getApplication()) { p -> _state.value = AiState.Downloading(p) }
                _state.value = AiState.Ready(0, 0, indexing = false)
                indexedFor?.let { index(it) }
            } catch (e: Exception) {
                ensureActive()
                _state.value = AiState.Failed(
                    e.message?.takeIf { it.length < 120 }
                        ?: "Download failed. Check your connection and try again.",
                )
            }
        }
    }

    fun cancelInstall() {
        installJob?.cancel()
        _state.value = AiState.Off
    }

    /** Deletes the model and all analysis results. */
    fun remove() {
        installJob?.cancel()
        indexJob?.cancel()
        viewModelScope.launch(worker) {
            synchronized(this@AiViewModel) {
                model?.close()
                model = null
            }
            embeddings.clear()
            tagTexts.clear()
            vocabulary = null
            vocabularyFile.delete()
            store.delete()
            ModelFiles.delete(getApplication())
            _state.value = AiState.Off
            _version.update { it + 1 }
        }
    }

    /** Analyzes every video that has no embedding yet. Safe to call whenever the list changes. */
    fun index(videos: List<VideoItem>) {
        // The list object changes on every like or tag edit; only restart when the videos change.
        val sameVideos = indexedFor?.map { it.id } == videos.map { it.id }
        indexedFor = videos
        if (_state.value !is AiState.Ready) return
        if (sameVideos && indexJob?.isActive == true) return
        indexJob?.cancel()
        indexJob = viewModelScope.launch(worker) {
            loadJob?.join() // Saved results must be in memory first, or every video is analyzed again.
            val todo = videos.filter { embeddings[it.id]?.key != keyOf(it) }
            var done = videos.size - todo.size
            _state.value = AiState.Ready(done, videos.size, indexing = todo.isNotEmpty())
            var unsaved = 0
            for (video in todo) {
                yield() // Lets other work queued on the worker (e.g. model removal) run between videos.
                runCatching { embedVideo(video) }.getOrNull()?.let { vector ->
                    embeddings[video.id] = EmbeddingStore.Entry(keyOf(video), vector)
                    unsaved++
                }
                done++
                _state.value = AiState.Ready(done, videos.size, indexing = true)
                if (unsaved >= 10) {
                    store.save(HashMap(embeddings))
                    unsaved = 0
                    _version.update { it + 1 }
                }
            }
            if (unsaved > 0) store.save(HashMap(embeddings))
            _version.update { it + 1 }
            _state.value = AiState.Ready(done, videos.size, indexing = false)
        }
    }

    private fun embedVideo(video: VideoItem): FloatArray? {
        val frames = FrameGrabber.grab(getApplication(), video.uri).frames
        if (frames.isEmpty()) return null
        val vectors = frames.map { f -> model().embedImage(ClipPreprocess.toTensor(f.argb, f.width, f.height)) }
        return ClipPreprocess.average(vectors)
    }

    /** Changes when the file behind an id changes. */
    private fun keyOf(video: VideoItem): Long = (video.uri.toString().hashCode().toLong() shl 32) xor video.size

    fun isAnalyzed(id: Long) = embeddings.containsKey(id)

    /** Prepares name-based suggestions for these tags (runs the text model once per tag). */
    fun prepareTags(tags: List<String>) {
        if (_state.value !is AiState.Ready) return
        prepareVocabulary()
        val missing = tags.filter { !tagTexts.containsKey(it) }
        if (missing.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            for (tag in missing) {
                // Tags that came from the idea list use its English wording, which CLIP understands.
                val prompt = TagVocabulary.forTag(tag)?.prompt ?: "a photo of ${tag.replace('_', ' ')}"
                runCatching { model().embedText(prompt) }
                    .onSuccess { tagTexts[tag] = it }
            }
            _version.update { it + 1 }
        }
    }

    /**
     * Up to [limit] tags for this video, best first. A tag qualifies when the video looks like
     * the videos already carrying it, or when CLIP thinks the tag's name describes the video.
     */
    fun suggestTags(id: Long, meta: Map<Long, VideoMeta>, videos: List<VideoItem>, limit: Int = 5): List<String> {
        val target = embeddings[id]?.vector ?: return emptyList()
        val own = meta[id]?.tags.orEmpty()

        // Similarity to every tagged video, grouped by tag.
        val byTag = HashMap<String, MutableList<Float>>()
        for (v in videos) {
            if (v.id == id) continue
            val tags = meta[v.id]?.tags ?: continue
            if (tags.isEmpty()) continue
            val e = embeddings[v.id]?.vector ?: continue
            val sim = ClipPreprocess.dot(target, e)
            for (t in tags) if (t !in own) byTag.getOrPut(t) { mutableListOf() } += sim
        }
        val scores = HashMap<String, Float>()
        for ((tag, sims) in byTag) {
            val top = sims.sortedDescending().take(3)
            scores[tag] = (top.average().toFloat() - KNN_FLOOR) / KNN_RANGE
        }
        for ((tag, text) in tagTexts) {
            if (tag in own) continue
            val zeroShot = (ClipPreprocess.dot(target, text) - TEXT_FLOOR) / TEXT_RANGE
            scores[tag] = maxOf(scores[tag] ?: Float.NEGATIVE_INFINITY, zeroShot)
        }
        return scores.filterValues { it >= 0.5f }.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    /** Embeds the idea vocabulary once (a few seconds) and caches it on disk. */
    private fun prepareVocabulary() {
        if (vocabulary != null || vocabularyJob?.isActive == true) return
        vocabularyJob = viewModelScope.launch(Dispatchers.Default) {
            val concepts = TagVocabulary.CONCEPTS
            val signature = concepts.joinToString("|") { it.english }.hashCode()
            val cached = runCatching {
                java.io.DataInputStream(vocabularyFile.inputStream().buffered()).use { input ->
                    if (input.readInt() != signature) return@use null
                    val dim = input.readInt()
                    List(concepts.size) { FloatArray(dim) { input.readFloat() } }
                }
            }.getOrNull()
            val vectors = cached ?: runCatching { concepts.map { model().embedText(it.prompt) } }.getOrNull()
                ?: return@launch
            if (cached == null) {
                runCatching {
                    java.io.DataOutputStream(vocabularyFile.outputStream().buffered()).use { out ->
                        out.writeInt(signature)
                        out.writeInt(vectors.first().size)
                        vectors.forEach { v -> v.forEach(out::writeFloat) }
                    }
                }
            }
            vocabulary = vectors
            _version.update { it + 1 }
        }
    }

    /**
     * New tag ideas from [TagVocabulary] that CLIP is fairly sure about, in the user's language.
     * A concept qualifies when its caption similarity is high and it clearly beats the rest
     * (softmax over all concepts, like CLIP's own zero-shot classification).
     */
    fun tagIdeas(id: Long, own: Set<String>, limit: Int = 3): List<String> {
        val target = embeddings[id]?.vector ?: return emptyList()
        val vectors = vocabulary ?: return emptyList()
        val sims = FloatArray(vectors.size) { ClipPreprocess.dot(target, vectors[it]) }
        val best = sims.maxOrNull() ?: return emptyList()
        var total = 0.0
        val weights = DoubleArray(sims.size) { kotlin.math.exp(100.0 * (sims[it] - best)).also { w -> total += w } }
        return sims.indices
            .filter { sims[it] >= IDEA_MIN_SIMILARITY && weights[it] / total >= IDEA_MIN_SHARE }
            .sortedByDescending { sims[it] }
            .map { TagVocabulary.CONCEPTS[it].label() }
            .filter { it !in own }
            .distinct()
            .take(limit)
    }

    /**
     * What to offer for a video: suggestions from the user's own tags, or, when none fit,
     * ideas for new tags.
     */
    fun suggestOrInvent(id: Long, meta: Map<Long, VideoMeta>, videos: List<VideoItem>, limit: Int): List<String> =
        suggestTags(id, meta, videos, limit).ifEmpty { tagIdeas(id, meta[id]?.tags.orEmpty(), minOf(limit, 3)) }

    /** Videos ranked by how well they match [query]; only clear matches are returned. */
    suspend fun search(query: String, videos: List<VideoItem>): List<VideoItem> = withContext(Dispatchers.Default) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        // Averaging with "a photo of …" helps short queries like "cat" or "beach".
        val text = ClipPreprocess.average(listOf(model().embedText(q), model().embedText("a photo of $q")))
        val scored = videos.mapNotNull { v -> embeddings[v.id]?.let { v to ClipPreprocess.dot(text, it.vector) } }
            .sortedByDescending { it.second }
        val best = scored.firstOrNull()?.second ?: return@withContext emptyList()
        if (best < MIN_SEARCH_SCORE) return@withContext emptyList()
        scored.takeWhile { it.second >= best - SEARCH_WINDOW }.take(MAX_RESULTS).map { it.first }
    }

    override fun onCleared() {
        model?.close()
    }

    private companion object {
        // Calibrated on sample photos: related pictures score ~0.70-0.95 against each other,
        // unrelated ones ~0.30-0.55; matching captions ~0.26-0.33, unrelated text ~0.13-0.22.
        const val KNN_FLOOR = 0.60f
        const val KNN_RANGE = 0.25f // >= 0.725 average similarity suggests the tag
        const val TEXT_FLOOR = 0.20f
        const val TEXT_RANGE = 0.10f // >= 0.25 caption similarity suggests the tag
        // Tag ideas: calibrated on sample photos (room 0.29, bear 0.30, tennis 0.26, cat 0.26
        // with clear softmax winners; vague pictures stay below).
        const val IDEA_MIN_SIMILARITY = 0.24f
        const val IDEA_MIN_SHARE = 0.10
        const val MIN_SEARCH_SCORE = 0.20f
        const val SEARCH_WINDOW = 0.06f
        const val MAX_RESULTS = 150
    }
}
