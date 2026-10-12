package com.oflinetok.app.data

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Finds identical copies of the same video, e.g. one saved from Telegram and the same file
 * forwarded through WhatsApp, or a copy on the SD card.
 *
 * Two files count as the same when their byte sizes are equal and the first and last 64 KB match.
 * Only files that share a size with another file are opened, so this is cheap even for big
 * libraries. Re-encoded copies (different size) are different videos and stay visible.
 */
class DuplicateFinder(private val resolver: ContentResolver) {

    /** Fingerprints survive between loads; a file's uri + size identifies its content well enough. */
    private val cache = HashMap<String, String?>()

    /**
     * Returns hidden copy id → id of the copy that stays visible. The visible copy is the one
     * the user has tagged/liked, else one from the gallery index, else the oldest.
     */
    fun find(videos: List<VideoItem>, meta: Map<Long, VideoMeta>): Map<Long, Long> {
        val result = HashMap<Long, Long>()
        val keep = compareByDescending<VideoItem> { meta[it.id]?.let { m -> !m.isEmpty } == true }
            .thenByDescending { it.id > 0 } // MediaStore items (positive ids) over storage-scan finds.
            .thenBy { it.dateAdded }
            .thenBy { it.id }

        for (sameSize in videos.filter { it.size > 0 }.groupBy { it.size }.values) {
            if (sameSize.size < 2) continue
            val byPrint = sameSize.groupBy { fingerprint(it) }
            for ((print, copies) in byPrint) {
                if (print == null || copies.size < 2) continue
                val sorted = copies.sortedWith(keep)
                val visible = sorted.first()
                for (copy in sorted.drop(1)) result[copy.id] = visible.id
            }
        }
        return result
    }

    private fun fingerprint(video: VideoItem): String? =
        cache.getOrPut("${video.uri}|${video.size}") { runCatching { readFingerprint(video.uri) }.getOrNull() }

    private fun readFingerprint(uri: Uri): String? {
        val pfd = resolver.openFileDescriptor(uri, "r") ?: return null
        pfd.use {
            FileInputStream(it.fileDescriptor).channel.use { channel ->
                val size = channel.size()
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteBuffer.allocate(CHUNK)
                readAt(channel, 0, buffer)
                digest.update(buffer)
                if (size > CHUNK) {
                    buffer.clear()
                    readAt(channel, maxOf(CHUNK.toLong(), size - CHUNK), buffer)
                    digest.update(buffer)
                }
                return digest.digest().joinToString("") { b -> "%02x".format(b) }
            }
        }
    }

    private fun readAt(channel: java.nio.channels.FileChannel, position: Long, buffer: ByteBuffer) {
        var pos = position
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, pos)
            if (n <= 0) break
            pos += n
        }
        buffer.flip()
    }

    companion object {
        private const val CHUNK = 64 * 1024

        /** Saved so the next launch hides known duplicates immediately, before checking again. */
        fun load(file: File): Map<Long, Long> = runCatching {
            file.readLines().mapNotNull { line ->
                val (copy, keep) = line.split('\t').takeIf { it.size == 2 } ?: return@mapNotNull null
                copy.toLong() to keep.toLong()
            }.toMap()
        }.getOrDefault(emptyMap())

        fun save(file: File, duplicates: Map<Long, Long>) {
            runCatching { file.writeText(duplicates.entries.joinToString("\n") { "${it.key}\t${it.value}" }) }
        }
    }
}
