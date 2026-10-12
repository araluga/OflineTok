package com.oflinetok.app.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Finds videos in two ways:
 *  1. MediaStore (the gallery index): fast, always.
 *  2. A walk over all storage when the app has file access. This finds videos the gallery
 *     skips, e.g. in folders marked with `.nomedia` by WhatsApp, Telegram and other apps.
 *     See [StorageScanner]: later walks only re-read folders that changed, and the last result
 *     is shown on the next launch right away while a fresh walk runs in the background.
 *
 * Android/data (where Telegram keeps unsaved videos) is off-limits to every app on Android 11+,
 * so nothing there can be found.
 */
class VideoRepository(private val context: Context) {

    /** A gallery video with its absolute path, used to avoid listing a file twice. */
    class Indexed(val item: VideoItem, val absolutePath: String?)

    private val scanner = StorageScanner(File(context.filesDir, "storage_index.bin"))

    /** Gallery + scanned files, without duplicates of the same path, newest first. */
    fun merge(indexed: List<Indexed>, scanned: List<VideoItem>): List<VideoItem> {
        val known = indexed.mapNotNullTo(HashSet()) { it.absolutePath }
        return (indexed.map { it.item } + scanned.filter { it.uri.path !in known })
            .sortedByDescending { it.dateAdded }
    }

    /** Videos from the gallery index. */
    @Suppress("DEPRECATION")
    fun queryMediaStore(): List<Indexed> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATA,
        ) + listOfNotNull(
            MediaStore.Video.Media.RELATIVE_PATH.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q },
        )
        val videos = mutableListOf<Indexed>()
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val folderCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val relCol = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val fileName = cursor.getString(nameCol) ?: "Video $id"
                val absolute = cursor.getString(dataCol)
                val item = VideoItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = fileName.substringBeforeLast('.'),
                    durationMs = cursor.getLong(durationCol),
                    dateAdded = cursor.getLong(dateCol),
                    folder = cursor.getString(folderCol),
                    fileName = fileName,
                    path = if (relCol >= 0) cursor.getString(relCol).orEmpty() else relativeDir(absolute),
                    size = cursor.getLong(sizeCol),
                )
                videos += Indexed(item, absolute)
            }
        }
        return videos
    }

    /** What the last storage walk found (possibly slightly out of date), without touching storage. */
    fun cachedScan(): List<VideoItem> = runCatching { scanner.cached(storageRoots).map(::toItem) }.getOrDefault(emptyList())

    /** Walks every storage volume, re-reading only folders that changed since last time. */
    fun freshScan(): List<VideoItem> = scanner.scan(storageRoots).map(::toItem)

    private fun toItem(v: StorageScanner.FoundVideo): VideoItem {
        val relDir = v.file.parentFile?.path.orEmpty().removePrefix(v.root.path).trim('/') + "/"
        return fileItem(v.file, v.size, v.modified, relDir)
    }

    /** Internal storage plus SD cards / USB drives. */
    @Suppress("DEPRECATION")
    private val storageRoots: List<File> by lazy {
        val primary = Environment.getExternalStorageDirectory()
        val others = context.getExternalFilesDirs(null).drop(1).mapNotNull { appDir ->
            appDir?.absolutePath?.substringBefore("/Android/data/")?.let(::File)
        }
        (listOf(primary) + others).distinctBy { it.absolutePath }
    }

    private fun fileItem(f: File, size: Long, modified: Long, relDir: String) = VideoItem(
        id = stableId(f.absolutePath),
        uri = Uri.fromFile(f),
        name = f.nameWithoutExtension,
        durationMs = 0L, // Unknown without opening the file; too slow for a whole-storage scan.
        dateAdded = modified / 1000,
        folder = f.parentFile?.name,
        fileName = f.name,
        path = relDir,
        size = size,
    )

    private fun relativeDir(absolute: String?): String {
        val dir = absolute?.let { File(it).parent } ?: return ""
        val root = storageRoots.firstOrNull { dir.startsWith(it.absolutePath) }?.absolutePath ?: return ""
        return dir.removePrefix(root).trim('/') + "/"
    }

    companion object {
        /** True when the app may read files directly, which the storage walk needs. */
        fun canScanAllFiles(): Boolean = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> Environment.isExternalStorageLegacy()
            else -> true
        }

        /**
         * Negative ids for scanned files, so they never collide with MediaStore ids (always positive)
         * and stay the same between launches. FNV-1a over the absolute path.
         */
        private fun stableId(path: String): Long {
            var h = -0x340d631b7bdddcdbL
            for (c in path) {
                h = h xor c.code.toLong()
                h *= 0x100000001b3L
            }
            return -((h ushr 1) or 1L)
        }
    }
}
