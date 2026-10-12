package com.oflinetok.app.ai

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * CLIP image preprocessing in plain Kotlin: resize the shortest side to 224 with an
 * antialiased bilinear (triangle) filter like Pillow, center-crop 224×224, normalize with
 * CLIP's mean/std, and lay out as 1×3×224×224 (CHW).
 */
object ClipPreprocess {
    const val SIZE = 224
    private val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
    private val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

    /** [argb] holds width*height pixels in Android's ARGB_8888 int layout, row by row. */
    fun toTensor(argb: IntArray, width: Int, height: Int): FloatArray {
        val scale = SIZE.toDouble() / min(width, height)
        val outW = max(SIZE, (width * scale).roundToInt())
        val outH = max(SIZE, (height * scale).roundToInt())

        // Separate float planes so the two resize passes keep full precision.
        val planes = Array(3) { FloatArray(width * height) }
        for (i in argb.indices) {
            val p = argb[i]
            planes[0][i] = ((p shr 16) and 0xff).toFloat()
            planes[1][i] = ((p shr 8) and 0xff).toFloat()
            planes[2][i] = (p and 0xff).toFloat()
        }
        val left = (outW - SIZE) / 2
        val top = (outH - SIZE) / 2
        val out = FloatArray(3 * SIZE * SIZE)
        for (c in 0 until 3) {
            val horizontal = resample(planes[c], width, height, outW, horizontal = true)
            val resized = resample(horizontal, outW, height, outH, horizontal = false)
            val base = c * SIZE * SIZE
            for (y in 0 until SIZE) {
                val row = (y + top) * outW + left
                for (x in 0 until SIZE) {
                    val v = resized[row + x].coerceIn(0f, 255f).roundToInt() / 255f
                    out[base + y * SIZE + x] = (v - MEAN[c]) / STD[c]
                }
            }
        }
        return out
    }

    /** One pass of Pillow-style triangle-filter resampling along x or y. */
    private fun resample(src: FloatArray, w: Int, h: Int, outSize: Int, horizontal: Boolean): FloatArray {
        val inSize = if (horizontal) w else h
        val scale = inSize.toDouble() / outSize
        val filterScale = max(scale, 1.0)
        val support = 1.0 * filterScale
        val out = FloatArray(if (horizontal) outSize * h else w * outSize)

        for (o in 0 until outSize) {
            val center = (o + 0.5) * scale
            val from = max(0, floor(center - support).toInt())
            val to = min(inSize, ceil(center + support).toInt())
            val weights = DoubleArray(to - from)
            var total = 0.0
            for (i in from until to) {
                val wgt = max(0.0, 1.0 - abs((i - center + 0.5) / filterScale))
                weights[i - from] = wgt
                total += wgt
            }
            if (total == 0.0) continue
            for (k in weights.indices) weights[k] /= total

            if (horizontal) {
                for (y in 0 until h) {
                    var acc = 0.0
                    val row = y * w
                    for (i in from until to) acc += src[row + i] * weights[i - from]
                    out[y * outSize + o] = acc.toFloat()
                }
            } else {
                for (x in 0 until w) {
                    var acc = 0.0
                    for (i in from until to) acc += src[i * w + x] * weights[i - from]
                    out[o * w + x] = acc.toFloat()
                }
            }
        }
        return out
    }

    fun normalize(v: FloatArray): FloatArray {
        var sum = 0.0
        for (x in v) sum += x * x
        val n = sqrt(sum).toFloat()
        if (n == 0f) return v
        return FloatArray(v.size) { v[it] / n }
    }

    /** Cosine similarity of two already-normalized vectors. */
    fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    /** Mean of several normalized vectors, normalized again. */
    fun average(vectors: List<FloatArray>): FloatArray {
        val sum = FloatArray(vectors.first().size)
        for (v in vectors) for (i in v.indices) sum[i] += v[i]
        return normalize(sum)
    }
}
