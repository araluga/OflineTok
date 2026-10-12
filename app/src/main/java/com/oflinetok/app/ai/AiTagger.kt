package com.oflinetok.app.ai

import kotlin.math.exp

/**
 * Decides the AI's own tags for every video. Pure Kotlin, so it is unit-tested on a PC.
 *
 * For each video, in order of preference:
 *  1. The user's tags that fit: the video looks like videos carrying the tag (average of the 3
 *     closest), or CLIP thinks the tag's name describes the video.
 *  2. If none fit: ideas from [TagVocabulary] that CLIP is clearly sure about.
 * Never kept: tags the video already has, tags the user rejected on this video, tags the user
 * never wants, and tags rejected on a near-identical video (the AI learns from "wrong").
 */
object AiTagger {
    class Input(
        val ids: List<Long>,
        val embeddings: Map<Long, FloatArray>,
        val userTags: Map<Long, Set<String>>,
        val rejected: Map<Long, Set<String>>,
        val never: Set<String>,
        /** Text embeddings of user tag names ("a photo of …"). */
        val tagTexts: Map<String, FloatArray>,
        /** Idea labels with their text embeddings, or empty while not ready. */
        val ideas: List<Pair<String, FloatArray>>,
    )

    const val MAX_TAGS = 3

    // Calibrated on sample photos: related pictures score ~0.70-0.95 against each other,
    // unrelated ones ~0.30-0.55; matching captions ~0.26-0.33, unrelated text ~0.13-0.22.
    private const val KNN_FLOOR = 0.60f
    private const val KNN_RANGE = 0.25f // >= 0.725 average similarity gives the tag
    private const val TEXT_FLOOR = 0.20f
    private const val TEXT_RANGE = 0.10f // >= 0.25 caption similarity gives the tag
    private const val IDEA_MIN_SIMILARITY = 0.24f
    private const val IDEA_MIN_SHARE = 0.10
    /** A "wrong" on a video at least this similar blocks the tag (unless a positive example is closer). */
    const val REJECT_SIMILARITY = 0.85f

    fun tagAll(input: Input): Map<Long, List<String>> {
        // Examples per tag: videos the user tagged with it, and videos where the user rejected it.
        val positives = HashMap<String, MutableList<Pair<Long, FloatArray>>>()
        val negatives = HashMap<String, MutableList<Pair<Long, FloatArray>>>()
        for ((id, tags) in input.userTags) {
            val e = input.embeddings[id] ?: continue
            for (t in tags) positives.getOrPut(t) { mutableListOf() } += id to e
        }
        for ((id, tags) in input.rejected) {
            val e = input.embeddings[id] ?: continue
            for (t in tags) negatives.getOrPut(t) { mutableListOf() } += id to e
        }

        val result = HashMap<Long, List<String>>()
        for (id in input.ids) {
            val e = input.embeddings[id] ?: continue
            val own = input.userTags[id].orEmpty()
            val rejectedHere = input.rejected[id].orEmpty()

            fun allowed(tag: String, bestPositive: Float): Boolean {
                if (tag in own || tag in rejectedHere || tag in input.never) return false
                val worst = negatives[tag]?.filter { it.first != id }?.maxOfOrNull { ClipPreprocess.dot(e, it.second) }
                return worst == null || worst < REJECT_SIMILARITY || worst < bestPositive
            }

            // 1. The user's own tags.
            val scores = HashMap<String, Float>()
            val closest = HashMap<String, Float>()
            for ((tag, examples) in positives) {
                val sims = examples.filter { it.first != id }.map { ClipPreprocess.dot(e, it.second) }
                if (sims.isEmpty()) continue
                val top = sims.sortedDescending().take(3)
                scores[tag] = (top.average().toFloat() - KNN_FLOOR) / KNN_RANGE
                closest[tag] = top.first()
            }
            for ((tag, text) in input.tagTexts) {
                val zeroShot = (ClipPreprocess.dot(e, text) - TEXT_FLOOR) / TEXT_RANGE
                scores[tag] = maxOf(scores[tag] ?: Float.NEGATIVE_INFINITY, zeroShot)
            }
            val fromOwn = scores.entries
                .filter { it.value >= 0.5f && allowed(it.key, closest[it.key] ?: 0f) }
                .sortedByDescending { it.value }
                .map { it.key }

            // 2. New ideas when nothing of the user's fits.
            val tags = fromOwn.ifEmpty { ideas(e, input.ideas).filter { allowed(it, 0f) } }
            if (tags.isNotEmpty()) result[id] = tags.distinct().take(MAX_TAGS)
        }
        return result
    }

    /** Idea labels CLIP is clearly sure about (softmax over all ideas, like CLIP's own classifier). */
    private fun ideas(e: FloatArray, ideas: List<Pair<String, FloatArray>>): List<String> {
        if (ideas.isEmpty()) return emptyList()
        val sims = FloatArray(ideas.size) { ClipPreprocess.dot(e, ideas[it].second) }
        val best = sims.max()
        var total = 0.0
        val weights = DoubleArray(sims.size) { exp(100.0 * (sims[it] - best)).also { w -> total += w } }
        return sims.indices
            .filter { sims[it] >= IDEA_MIN_SIMILARITY && weights[it] / total >= IDEA_MIN_SHARE }
            .sortedByDescending { sims[it] }
            .map { ideas[it].first }
    }
}
