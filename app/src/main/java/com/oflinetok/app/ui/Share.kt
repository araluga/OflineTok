package com.oflinetok.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.oflinetok.app.data.VideoItem
import java.io.File

/**
 * Opens the system share sheet (Telegram, WhatsApp, Bluetooth, ...) for a gallery video.
 * The receiving app gets temporary read access to that one file only.
 */
fun shareVideo(context: Context, video: VideoItem) {
    // Videos found by the storage scan are plain files; other apps need a content:// link to them.
    val uri = if (video.uri.scheme == "file") {
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(video.uri.path!!))
    } else {
        video.uri
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = context.contentResolver.getType(uri) ?: "video/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        // ClipData is what carries the read grant through the chooser to the target app.
        clipData = ClipData.newRawUri(video.fileName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, "Share video"))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this phone can share videos", Toast.LENGTH_SHORT).show()
    }
}
