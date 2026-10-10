package com.oflinetok.app.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore

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
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                videos += VideoItem(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = cursor.getString(nameCol)?.substringBeforeLast('.') ?: "Video $id",
                    durationMs = cursor.getLong(durationCol),
                    dateAdded = cursor.getLong(dateCol),
                    folder = cursor.getString(folderCol),
                )
            }
        }
        return videos
    }
}
