package com.oflinetok.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One video's tags as stored in a backup file. */
data class BackupEntry(
    val id: Long,
    val fileName: String?,
    val path: String?,
    val size: Long,
    val meta: VideoMeta,
)

data class RestoreResult(val restored: Int, val total: Int)

/**
 * Keeps a copy of all tags, likes and favorites in Download/OflineTok/, which survives an
 * uninstall. Entries carry the file name, folder and size so they can be matched to videos
 * again after a reinstall or on a new phone, where gallery ids are different.
 */
class BackupManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    val lastBackupAt: Long get() = prefs.getLong("time", 0L)

    /** Writes the backup, replacing this install's previous one. Throws on failure. */
    fun export(videos: List<VideoItem>, meta: Map<Long, VideoMeta>) {
        val byId = videos.associateBy { it.id }
        val entries = JSONArray()
        for ((id, m) in meta) {
            if (m.isEmpty) continue
            val video = byId[id]
            entries.put(
                JSONObject()
                    .put("id", id)
                    .put("fileName", video?.fileName)
                    .put("path", video?.path)
                    .put("size", video?.size ?: 0L)
                    .put("tags", JSONArray(m.tags.sorted()))
                    .put("liked", m.liked)
                    .put("favorite", m.favorite)
                    .put("blocked", m.blocked),
            )
        }
        val json = JSONObject()
            .put("app", "OflineTok")
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("videos", entries)
            .toString(1)
            .toByteArray()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) writeMediaStore(json) else writeLegacy(json)
        prefs.edit().putLong("time", System.currentTimeMillis()).apply()
    }

    private fun writeMediaStore(bytes: ByteArray) {
        val resolver = context.contentResolver
        // Reuse the file this install created. After a reinstall the old file belongs to the
        // previous install and can't be opened, so a new one is created next to it.
        prefs.getString("uri", null)?.let { saved ->
            val ok = runCatching {
                resolver.openOutputStream(Uri.parse(saved), "wt")!!.use { it.write(bytes) }
            }.isSuccess
            if (ok) return
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
        }
        val uri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: error("Couldn't create the backup file")
        resolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
        prefs.edit().putString("uri", uri.toString()).apply()
    }

    @Suppress("DEPRECATION")
    private fun writeLegacy(bytes: ByteArray) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
        dir.mkdirs()
        val tmp = File(dir, "$FILE_NAME.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(File(dir, FILE_NAME))) {
            File(dir, FILE_NAME).writeBytes(bytes)
            tmp.delete()
        }
    }

    /** Reads a backup file chosen by the user. Throws if it isn't an OflineTok backup. */
    fun read(uri: Uri): List<BackupEntry> {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val root = JSONObject(text)
        require(root.optString("app") == "OflineTok") { "This isn't an OflineTok backup file." }
        val videos = root.getJSONArray("videos")
        return (0 until videos.length()).map { i ->
            val o = videos.getJSONObject(i)
            val tags = o.optJSONArray("tags") ?: JSONArray()
            BackupEntry(
                id = o.optLong("id"),
                fileName = o.optString("fileName").ifEmpty { null }.takeUnless { o.isNull("fileName") },
                path = o.optString("path").ifEmpty { null }.takeUnless { o.isNull("path") },
                size = o.optLong("size"),
                meta = VideoMeta(
                    tags = (0 until tags.length()).mapNotNull { normalizeTag(tags.getString(it)) }.toSet(),
                    liked = o.optBoolean("liked"),
                    favorite = o.optBoolean("favorite"),
                    blocked = o.optBoolean("blocked"),
                ),
            )
        }
    }

    companion object {
        const val FOLDER = "OflineTok"
        const val FILE_NAME = "OflineTok-tags.json"
        const val DISPLAY_PATH = "Download/$FOLDER/$FILE_NAME"

        /**
         * Finds each backup entry's video, trying the most exact match first:
         * folder + name + size, folder + name, name + size, then the old gallery id.
         */
        fun match(entries: List<BackupEntry>, videos: List<VideoItem>): Map<Long, VideoMeta> {
            fun dir(p: String?) = p.orEmpty().trim('/').lowercase()
            val byPathNameSize = videos.associateBy { Triple(dir(it.path), it.fileName.lowercase(), it.size) }
            val byPathName = videos.associateBy { dir(it.path) to it.fileName.lowercase() }
            val byNameSize = videos.associateBy { it.fileName.lowercase() to it.size }
            val byId = videos.associateBy { it.id }

            val result = mutableMapOf<Long, VideoMeta>()
            for (e in entries) {
                val name = e.fileName?.lowercase()
                val video = when {
                    name != null -> byPathNameSize[Triple(dir(e.path), name, e.size)]
                        ?: byPathName[dir(e.path) to name]
                        ?: byNameSize[name to e.size]
                    else -> byId[e.id]
                } ?: continue
                result[video.id] = result[video.id]?.let { it.mergedWith(e.meta) } ?: e.meta
            }
            return result
        }
    }
}

/** Union of tags; liked/favorite/blocked if either side is. */
fun VideoMeta.mergedWith(other: VideoMeta) = VideoMeta(
    tags = tags + other.tags,
    liked = liked || other.liked,
    favorite = favorite || other.favorite,
    blocked = blocked || other.blocked,
)
