package com.oflinetok.app.data

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Walks storage for video files as cheaply as Android allows:
 *  - Files are classified by extension, so photos, music and documents are never stat'ed
 *    (each stat goes through Android's storage layer and is the slow part).
 *  - Every folder's modification time is remembered. A folder whose time hasn't changed
 *    has the same entries, so it isn't listed again; only its videos are re-checked.
 *  - Top-level folders are walked in parallel.
 */
class StorageScanner(indexFile: File) {

    /** A video file found in a folder. */
    class FoundVideo(val file: File, val size: Long, val modified: Long, val root: File)

    private class Video(val name: String, val size: Long, val modified: Long)

    private class Folder(val modified: Long, val videos: List<Video>, val subfolders: List<String>)

    // Plain java.io (no Android classes), so the scanner can be unit-tested on a PC.
    private val store = indexFile

    fun scan(roots: List<File>): List<FoundVideo> {
        val previous = load()
        val next = ConcurrentHashMap<String, Folder>()
        val found = java.util.Collections.synchronizedList(mutableListOf<FoundVideo>())
        val pool = Executors.newFixedThreadPool(PARALLELISM)
        try {
            val tasks = mutableListOf<Future<*>>()
            for (root in roots) {
                // The root itself is read here; each top-level folder becomes its own task.
                val top = visit(root, root, previous, next, found) ?: continue
                for (name in top) {
                    // Of the Android folder only Android/media is readable and holds user media.
                    val dir = if (name == "Android") File(root, "Android/media") else File(root, name)
                    tasks += pool.submit { walk(dir, root, depth = 1, previous, next, found) }
                }
            }
            tasks.forEach { runCatching { it.get() } }
        } finally {
            pool.shutdown()
        }
        save(next)
        return found.toList()
    }

    private fun walk(
        dir: File, root: File, depth: Int,
        previous: Map<String, Folder>, next: MutableMap<String, Folder>, found: MutableList<FoundVideo>,
    ) {
        if (depth > MAX_DEPTH) return
        val subfolders = visit(dir, root, previous, next, found) ?: return
        for (name in subfolders) walk(File(dir, name), root, depth + 1, previous, next, found)
    }

    /** Records [dir]'s videos and returns its subfolder names (null if unreadable). */
    private fun visit(
        dir: File, root: File,
        previous: Map<String, Folder>, next: MutableMap<String, Folder>, found: MutableList<FoundVideo>,
    ): List<String>? {
        val modified = dir.lastModified()
        if (modified == 0L) return null
        val cached = previous[dir.path]
        val folder = if (cached != null && cached.modified == modified) {
            // Same entries as last time. Videos are few, so re-check their size (one may still be copying).
            Folder(modified, cached.videos.map { v -> File(dir, v.name).let { Video(v.name, it.length(), it.lastModified()) } }, cached.subfolders)
        } else {
            list(dir, modified) ?: return null
        }
        next[dir.path] = folder
        for (v in folder.videos) {
            if (v.size >= MIN_SIZE_BYTES) found += FoundVideo(File(dir, v.name), v.size, v.modified, root)
        }
        return folder.subfolders
    }

    private fun list(dir: File, modified: Long): Folder? {
        val names = dir.list() ?: return null
        val videos = mutableListOf<Video>()
        val subfolders = mutableListOf<String>()
        for (name in names) {
            val ext = name.substringAfterLast('.', "").lowercase()
            when {
                ext in VIDEO_EXTENSIONS -> File(dir, name).let { f ->
                    if (f.isFile) videos += Video(name, f.length(), f.lastModified()) else subfolders += name
                }
                ext in NOT_VIDEO_EXTENSIONS -> Unit // A photo, song, document… no need to look closer.
                name.equals(".thumbnails", true) || name.startsWith(".trash", true) || name.equals("LOST.DIR", true) -> Unit
                File(dir, name).isDirectory -> subfolders += name
            }
        }
        return Folder(modified, videos, subfolders)
    }

    private fun load(): Map<String, Folder> = runCatching {
        DataInputStream(store.inputStream().buffered()).use { input ->
            if (input.readInt() != MAGIC) return@use emptyMap()
            val map = HashMap<String, Folder>()
            repeat(input.readInt()) {
                val path = input.readUTF()
                val modified = input.readLong()
                val videos = List(input.readInt()) { Video(input.readUTF(), input.readLong(), input.readLong()) }
                val subfolders = List(input.readInt()) { input.readUTF() }
                map[path] = Folder(modified, videos, subfolders)
            }
            map
        }
    }.getOrDefault(emptyMap())

    private fun save(folders: Map<String, Folder>) {
        val tmp = File(store.path + ".tmp")
        try {
            val data = DataOutputStream(tmp.outputStream().buffered())
            data.writeInt(MAGIC)
            data.writeInt(folders.size)
            for ((path, f) in folders) {
                data.writeUTF(path)
                data.writeLong(f.modified)
                data.writeInt(f.videos.size)
                f.videos.forEach { v ->
                    data.writeUTF(v.name)
                    data.writeLong(v.size)
                    data.writeLong(v.modified)
                }
                data.writeInt(f.subfolders.size)
                f.subfolders.forEach(data::writeUTF)
            }
            data.close()
            if (!tmp.renameTo(store)) {
                store.delete()
                tmp.renameTo(store)
            }
        } catch (e: Exception) {
            tmp.delete()
        }
    }

    /** Videos recorded by the last scan, without touching storage at all (for the instant first screen). */
    fun cached(roots: List<File>): List<FoundVideo> {
        val folders = load()
        return folders.flatMap { (path, f) ->
            val root = roots.firstOrNull { path.startsWith(it.path) } ?: return@flatMap emptyList()
            f.videos.filter { it.size >= MIN_SIZE_BYTES }.map { FoundVideo(File(path, it.name), it.size, it.modified, root) }
        }
    }

    companion object {
        private const val MAGIC = 0x4F545333 // "OTS3"
        private const val PARALLELISM = 4
        private const val MAX_DEPTH = 16
        const val MIN_SIZE_BYTES = 50_000L // Skips broken stubs and tiny cache fragments.
        val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mkv", "webm", "3gp", "3g2", "mov", "ts", "avi", "flv")
        private val NOT_VIDEO_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "raw", "svg", "ico",
            "mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "amr", "mid",
            "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "rtf", "csv", "md", "epub", "fb2",
            "json", "xml", "html", "htm", "js", "css", "log", "ini", "cfg", "conf", "yml", "yaml",
            "zip", "rar", "7z", "tar", "gz", "apk", "apks", "xapk", "obb", "bin", "dat", "db", "sqlite",
            "tmp", "temp", "bak", "nomedia", "thumb", "thumbnail", "cache", "lrc", "srt", "vtt", "ass",
            "ttf", "otf", "vcf", "ics", "kml", "gpx", "torrent", "exe", "so", "dex", "jar", "key", "pem",
        )
    }
}
