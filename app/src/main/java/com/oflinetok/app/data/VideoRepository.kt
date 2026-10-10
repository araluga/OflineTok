package com.oflinetok.app.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Reads every video on the device from MediaStore (the gallery). */
class VideoRepository(private val context: Context) {

    fun loadVideos(): List<VideoItem> {
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
            pathColumn,
        )
        val videos = mutableListOf<VideoItem>()
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
            val pathCol = cursor.getColumnIndexOrThrow(pathColumn)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val fileName = cursor.getString(nameCol) ?: "Video $id"
                videos += VideoItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = fileName.substringBeforeLast('.'),
                    durationMs = cursor.getLong(durationCol),
                    dateAdded = cursor.getLong(dateCol),
                    folder = cursor.getString(folderCol),
                    fileName = fileName,
                    path = relativeDir(cursor.getString(pathCol)),
                    size = cursor.getLong(sizeCol),
                )
            }
        }
        return videos
    }

    // RELATIVE_PATH ("DCIM/Camera/") exists from Android 10; before that only the absolute DATA path does.
    @Suppress("DEPRECATION")
    private val pathColumn =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.RELATIVE_PATH
        else MediaStore.Video.Media.DATA

    @Suppress("DEPRECATION")
    private fun relativeDir(value: String?): String {
        if (value == null) return ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return value
        val root = Environment.getExternalStorageDirectory().absolutePath
        val dir = File(value).parent ?: return ""
        return dir.removePrefix(root).trim('/') + "/"
    }
}
