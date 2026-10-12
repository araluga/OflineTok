package com.oflinetok.app.ai

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * The CLIP model files (OpenAI CLIP ViT-B/32, MIT license, int8 ONNX export by Xenova).
 * Downloaded once on request, checked against fixed SHA-256 hashes, then used fully offline.
 */
object ModelFiles {
    /** Bump when switching models; old embeddings are then recomputed. */
    const val VERSION = 1
    const val VISION = "vision_model_quantized.onnx"
    const val TEXT = "text_model_quantized.onnx"
    const val TOKENIZER = "tokenizer.json"

    class Spec(val name: String, val size: Long, val sha256: String)

    val FILES = listOf(
        Spec(VISION, 89_117_001, "583fd1110a514667812fee7d684952aaf82a99b959760c8d7dca7e0ab9839299"),
        Spec(TEXT, 64_504_507, "73baab855d406190da9faa498cfedf65f15cf309f4cc7385b7b032e6d08e5c3a"),
        Spec(TOKENIZER, 2_224_119, "f7f3b7af117d467b58374797691a6438d3e6b9e9cef800dfd5dced7f697a90cd"),
    )
    val TOTAL_BYTES = FILES.sumOf { it.size }

    /** Our GitHub release first (same place app updates come from), Hugging Face as a fallback. */
    private fun urls(name: String) = listOf(
        "https://github.com/araluga/OflineTok/releases/download/ai-model-$VERSION/$name",
        "https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/" +
            if (name.endsWith(".onnx")) "onnx/$name" else name,
    )

    fun dir(context: Context) = File(context.getExternalFilesDir(null) ?: context.filesDir, "ai-model-$VERSION")

    private fun marker(context: Context) = File(dir(context), ".verified")

    fun isInstalled(context: Context): Boolean =
        marker(context).exists() && FILES.all { File(dir(context), it.name).length() == it.size }

    fun delete(context: Context) {
        dir(context).deleteRecursively()
    }

    /**
     * Downloads every missing file, resuming partial downloads. [onProgress] gets 0..1.
     * Throws with a readable message on failure. Cancellable between chunks.
     */
    suspend fun install(context: Context, onProgress: (Float) -> Unit) {
        val dir = dir(context).apply { mkdirs() }
        marker(context).delete()
        var doneBefore = 0L
        for (spec in FILES) {
            val target = File(dir, spec.name)
            if (target.length() != spec.size || sha256(target) != spec.sha256) {
                target.delete()
                download(spec, target) { got -> onProgress((doneBefore + got).toFloat() / TOTAL_BYTES) }
            }
            doneBefore += spec.size
            onProgress(doneBefore.toFloat() / TOTAL_BYTES)
        }
        marker(context).createNewFile()
    }

    private suspend fun download(spec: Spec, target: File, onBytes: (Long) -> Unit) {
        val part = File(target.path + ".part")
        var lastError: Exception? = null
        for (url in urls(spec.name)) {
            try {
                fetch(url, part, spec.size, onBytes)
                if (part.length() != spec.size || sha256(part) != spec.sha256) {
                    part.delete()
                    error("The downloaded file was damaged. Please try again.")
                }
                if (!part.renameTo(target)) error("Couldn't save the model file.")
                return
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Download failed")
    }

    private suspend fun fetch(url: String, part: File, expected: Long, onBytes: (Long) -> Unit) {
        var have = part.length().takeIf { it < expected } ?: 0L.also { part.delete() }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "OflineTok")
            if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                have = 0 // Server ignored the range; start over.
            } else if (code != HttpURLConnection.HTTP_PARTIAL) {
                error("Server answered $code")
            }
            conn.inputStream.use { input ->
                FileOutputStream(part, have > 0).use { out ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        have += n
                        onBytes(have)
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun sha256(file: File): String? {
        if (!file.exists()) return null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(1 shl 20).use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
