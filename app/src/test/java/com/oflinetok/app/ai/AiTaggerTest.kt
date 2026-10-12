package com.oflinetok.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class AiTaggerTest {
    /** Unit vector mostly along [axis], with a little of [other] mixed in to vary similarity. */
    private fun vec(axis: Int, other: Int = axis, mix: Float = 0f): FloatArray {
        val v = FloatArray(8)
        v[axis] += 1f - mix
        v[other] += mix
        val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(8) { v[it] / n }
    }

    private fun input(
        embeddings: Map<Long, FloatArray>,
        userTags: Map<Long, Set<String>> = emptyMap(),
        rejected: Map<Long, Set<String>> = emptyMap(),
        never: Set<String> = emptySet(),
        ideas: List<Pair<String, FloatArray>> = emptyList(),
    ) = AiTagger.Input(embeddings.keys.toList(), embeddings, userTags, rejected, never, emptyMap(), ideas)

    @Test
    fun similarVideosGetTheUsersTag() {
        val result = AiTagger.tagAll(
            input(
                embeddings = mapOf(1L to vec(0), 2L to vec(0, 1, 0.1f), 3L to vec(5)),
                userTags = mapOf(1L to setOf("cats")),
            ),
        )
        assertEquals(listOf("cats"), result[2L]) // Looks like the tagged video.
        assertEquals(null, result[3L]) // Looks like nothing tagged.
        assertEquals(null, result[1L]) // Already has the tag as a user tag.
    }

    @Test
    fun rejectedTagIsDroppedHereAndOnNearIdenticalVideos() {
        // Video 2 (rejected) is somewhat like video 1; video 3 is almost exactly video 1.
        val embeddings = mapOf(1L to vec(0), 2L to vec(0, 1, 0.3f), 3L to vec(0, 1, 0.05f))
        val result = AiTagger.tagAll(
            input(embeddings, userTags = mapOf(1L to setOf("cats")), rejected = mapOf(2L to setOf("cats"))),
        )
        assertEquals(null, result[2L]) // Rejected here.
        // Video 3 is closer to the tagged video 1 than to the rejected video 2, so it keeps "cats".
        assertEquals(listOf("cats"), result[3L])

        val nearRejected = AiTagger.tagAll(
            input(
                mapOf(1L to vec(0), 2L to vec(0, 1, 0.3f), 3L to vec(0, 1, 0.31f)),
                userTags = mapOf(1L to setOf("cats")),
                rejected = mapOf(2L to setOf("cats")),
            ),
        )
        // Video 3 is nearly identical to the rejected video 2 and further from the tagged one.
        assertEquals(null, nearRejected[3L])
    }

    @Test
    fun ideasOnlyWhenNoOwnTagFitsAndNeverListIsRespected() {
        val ideas = listOf("beach" to vec(3), "dog" to vec(4))
        // Make the video clearly "beach": similarity 1.0 with the beach idea.
        val result = AiTagger.tagAll(input(mapOf(7L to vec(3)), ideas = ideas))
        assertEquals(listOf("beach"), result[7L])

        val never = AiTagger.tagAll(input(mapOf(7L to vec(3)), never = setOf("beach"), ideas = ideas))
        assertEquals(null, never[7L])

        val withOwn = AiTagger.tagAll(
            input(
                mapOf(1L to vec(3), 7L to vec(3, 2, 0.05f)),
                userTags = mapOf(1L to setOf("seaside")),
                ideas = ideas,
            ),
        )
        assertEquals(listOf("seaside"), withOwn[7L]) // The user's own tag wins over an idea.
    }

    @Test
    fun atMostThreeTags() {
        val tags = (0 until 6).associate { it.toLong() to setOf("t$it") }
        val embeddings = (0L until 6L).associateWith { vec(0, 1, it * 0.001f) } + (99L to vec(0))
        val result = AiTagger.tagAll(input(embeddings, userTags = tags))
        assertTrue((result[99L]?.size ?: 0) <= AiTagger.MAX_TAGS)
    }
}
