package com.oflinetok.app.ai

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Checks the Kotlin CLIP port against the Python/Hugging Face reference.
 * Run with: ./gradlew testDebugUnitTest -PclipDir=<folder with tokenizer.json and reference.json>
 * (optionally -PcocoDir=<folder with the reference images>). Skipped when no folder is given.
 */
class ClipPortTest {
    private val clipDir = System.getProperty("clipDir").orEmpty()
    private val cocoDir = System.getProperty("cocoDir").orEmpty()
    private val outDir = File(System.getProperty("clipOut") ?: "build/clip-test")

    @Test
    fun tokenizerMatchesHuggingFace() {
        assumeTrue("set -PclipDir to run", clipDir.isNotEmpty())
        val tokenizer = ClipTokenizer.fromJson(File(clipDir, "tokenizer.json").readText())
        val tokens = JSONObject(File(clipDir, "reference.json").readText()).getJSONObject("tokens")
        for (text in tokens.keys()) {
            val expected = tokens.getJSONArray(text).let { a -> IntArray(a.length()) { a.getInt(it) } }
            assertArrayEquals("tokens for \"$text\"", expected, tokenizer.encode(text))
        }
    }

    @Test
    fun longTextIsTruncatedTo77() {
        assumeTrue("set -PclipDir to run", clipDir.isNotEmpty())
        val tokenizer = ClipTokenizer.fromJson(File(clipDir, "tokenizer.json").readText())
        val ids = tokenizer.encode("cat ".repeat(200))
        assert(ids.size == 77) { "got ${ids.size}" }
        assert(ids.first() == ClipTokenizer.START && ids.last() == ClipTokenizer.END)
    }

    /** Writes Kotlin-preprocessed tensors; clip_check.py runs them through the model and compares. */
    @Test
    fun writePreprocessedImages() {
        assumeTrue("set -PcocoDir to run", cocoDir.isNotEmpty())
        outDir.mkdirs()
        // Images pre-decoded by Python as: int32 width, int32 height, then RGB bytes.
        File(cocoDir).listFiles { f -> f.extension == "rgb" }!!.forEach { f ->
            val bytes = ByteBuffer.wrap(f.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            val width = bytes.int
            val height = bytes.int
            val argb = IntArray(width * height) {
                val r = bytes.get().toInt() and 0xff
                val g = bytes.get().toInt() and 0xff
                val b = bytes.get().toInt() and 0xff
                (0xff shl 24) or (r shl 16) or (g shl 8) or b
            }
            val tensor = ClipPreprocess.toTensor(argb, width, height)
            val buf = ByteBuffer.allocate(tensor.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            tensor.forEach { buf.putFloat(it) }
            File(outDir, f.nameWithoutExtension + ".f32").writeBytes(buf.array())
        }
    }
}
