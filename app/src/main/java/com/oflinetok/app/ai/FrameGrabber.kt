package com.oflinetok.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlin.math.min
import kotlin.math.roundToInt

/** Still frames taken from a video, as ARGB pixels ready for [ClipPreprocess]. */
class VideoFrames(val frames: List<Frame>, val durationMs: Long) {
    class Frame(val argb: IntArray, val width: Int, val height: Int)
}

object FrameGrabber {
    /** Box the decoder scales frames into; keeps the short side at or above CLIP's 224 for normal videos. */
    private const val BOX = 512

    /** Frames at 20%, 50% and 80% of the video (one frame for clips under 3 seconds). */
    fun grab(context: Context, uri: Uri): VideoFrames {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val points = if (duration < 3000) listOf(0.5) else listOf(0.2, 0.5, 0.8)
            val frames = points.mapNotNull { p ->
                val timeUs = (duration * p * 1000).toLong()
                val bitmap = runCatching { frameAt(retriever, timeUs) }.getOrNull() ?: return@mapNotNull null
                toFrame(bitmap)
            }
            return VideoFrames(frames, duration)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun frameAt(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, BOX, BOX)
        } else {
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }

    private fun toFrame(source: Bitmap): VideoFrames.Frame {
        // Older Android returns full-size frames; shrink them first so preprocessing stays fast.
        val shortSide = min(source.width, source.height)
        val bitmap = if (shortSide > 2 * ClipPreprocess.SIZE) {
            val s = 2.0 * ClipPreprocess.SIZE / shortSide
            Bitmap.createScaledBitmap(source, (source.width * s).roundToInt(), (source.height * s).roundToInt(), true)
                .also { if (it != source) source.recycle() }
        } else {
            source
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val frame = VideoFrames.Frame(pixels, bitmap.width, bitmap.height)
        bitmap.recycle()
        return frame
    }
}
