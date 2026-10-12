package com.oflinetok.app.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Runs CLIP with ONNX Runtime. Both encoders output 512-number embeddings in a shared space:
 * a picture and a sentence describing it end up close together. Results are L2-normalized,
 * so a dot product is their cosine similarity.
 */
class ClipModel(private val dir: File) : Closeable {
    private val env = OrtEnvironment.getEnvironment()
    private val options = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(2) // Leaves cores free so video playback stays smooth.
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
    }
    private val visionLock = Any()
    private val textLock = Any()
    private var vision: OrtSession? = null
    private var text: OrtSession? = null
    private val tokenizer by lazy { ClipTokenizer.fromJson(File(dir, ModelFiles.TOKENIZER).readText()) }

    /** [tensor] comes from [ClipPreprocess.toTensor]. */
    fun embedImage(tensor: FloatArray): FloatArray = synchronized(visionLock) {
        val session = vision ?: env.createSession(File(dir, ModelFiles.VISION).path, options).also { vision = it }
        val shape = longArrayOf(1, 3, ClipPreprocess.SIZE.toLong(), ClipPreprocess.SIZE.toLong())
        OnnxTensor.createTensor(env, FloatBuffer.wrap(tensor), shape).use { input ->
            session.run(mapOf("pixel_values" to input)).use { result ->
                @Suppress("UNCHECKED_CAST")
                ClipPreprocess.normalize((result[0].value as Array<FloatArray>)[0])
            }
        }
    }

    fun embedText(query: String): FloatArray = synchronized(textLock) {
        val session = text ?: env.createSession(File(dir, ModelFiles.TEXT).path, options).also { text = it }
        val ids = tokenizer.encode(query).map { it.toLong() }.toLongArray()
        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())).use { input ->
            session.run(mapOf("input_ids" to input)).use { result ->
                @Suppress("UNCHECKED_CAST")
                ClipPreprocess.normalize((result[0].value as Array<FloatArray>)[0])
            }
        }
    }

    override fun close() {
        synchronized(visionLock) { vision?.close(); vision = null }
        synchronized(textLock) { text?.close(); text = null }
        options.close()
    }
}
