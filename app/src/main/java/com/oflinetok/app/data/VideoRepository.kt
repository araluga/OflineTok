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
 *  1. MediaStore (the gallery index) — always.
 *  2. A walk over all storage when the app has file access. This finds videos the gallery
 *     skips, e.g. in folders marked with `.nomedia` by WhatsApp, Telegram and other apps.
 *
 * Android/data (where Telegram keeps unsaved videos) is off-limits to every app on Android 11+,
 * so nothing there can be found.
 */
class VideoRepository(private val context: Context) {

    private var scanCache: List<VideoItem>? = null
    private var scannedAt = 0L

    fun loadVideos(forceScan: Boolean = false): List<VideoItem> {
        val indexed = queryMediaStore()
        if (!canScanAllFiles()) {
            scanCache = null
            return indexed.map { it.first }
        }
        val known = indexed.mapNotNullTo(HashSet()) { it.second }
        val scanned = scanStorage(forceScan).filter { it.uri.path !in known }
        return (indexed.map { it.first } + scanned).sortedByDescending { it.dateAdded }
    }

    /** Videos from the gallery index, each with its absolute path (for de-duplicating the scan). */
    @Suppress("DEPRECATION")
    private fun queryMediaStore(): List<Pair<VideoItem, String?>> {
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
        val videos = mutableListOf<Pair<VideoItem, String?>>()
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
                videos += item to absolute
            }
        }
        return videos
    }

    /** Walks every storage volume. Results are cached for a few minutes; a walk can take seconds. */
    private fun scanStorage(force: Boolean): List<VideoItem> {
        val cached = scanCache
        if (!force && cached != null && System.currentTimeMillis() - scannedAt < SCAN_CACHE_MS) return cached
        val found = mutableListOf<VideoItem>()
        for (root in storageRoots) walk(root, root, depth = 0, out = found)
        scanCache = found
        scannedAt = System.currentTimeMillis()
        return found
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

    private fun walk(dir: File, root: File, depth: Int, out: MutableList<VideoItem>) {
        if (depth > MAX_DEPTH) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.isDirectory) {
                val lower = f.name.lowercase()
                when {
                    // Of the Android folder only Android/media is readable and holds user media.
                    depth == 0 && f.name == "Android" -> walk(File(f, "media"), root, depth + 2, out)
                    lower == ".thumbnails" || lower.startsWith(".trash") || lower == "lost.dir" -> Unit
                    else -> walk(f, root, depth + 1, out)
                }
            } else if (f.extension.lowercase() in VIDEO_EXTENSIONS && f.length() >= MIN_SIZE_BYTES) {
                out += fileItem(f, root)
            }
        }
    }

    private fun fileItem(f: File, root: File): VideoItem {
        val parent = f.parentFile
        return VideoItem(
            id = stableId(f.absolutePath),
            uri = Uri.fromFile(f),
            name = f.nameWithoutExtension,
            durationMs = 0L, // Unknown without opening the file; too slow for a whole-storage scan.
            dateAdded = f.lastModified() / 1000,
            folder = parent?.name,
            fileName = f.name,
            path = parent?.absolutePath.orEmpty().removePrefix(root.absolutePath).trim('/') + "/",
            size = f.length(),
        )
    }

    private fun relativeDir(absolute: String?): String {
        val dir = absolute?.let { File(it).parent } ?: return ""
        val root = storageRoots.firstOrNull { dir.startsWith(it.absolutePath) }?.absolutePath ?: return ""
        return dir.removePrefix(root).trim('/') + "/"
    }

    companion object {
        private const val MAX_DEPTH = 16
        private const val MIN_SIZE_BYTES = 50_000L // Skips broken stubs and tiny cache fragments.
        private const val SCAN_CACHE_MS = 5 * 60 * 1000L
        private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mkv", "webm", "3gp", "3g2", "mov", "ts", "avi", "flv")

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
