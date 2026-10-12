package com.oflinetok.app.data

import android.net.Uri

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val dateAdded: Long,
    val folder: String?,
    /** File name with extension, e.g. "VID_20240101.mp4". */
    val fileName: String,
    /** Directory relative to shared storage, e.g. "DCIM/Camera/". Used to match backups. */
    val path: String,
    val size: Long,
)

data class VideoMeta(
    val tags: Set<String> = emptySet(),
    val liked: Boolean = false,
    val favorite: Boolean = false,
    /** "Don't show this video again": hidden everywhere except Me → Hidden. */
    val blocked: Boolean = false,
    /** AI tags the user said are wrong for this video. */
    val rejectedAi: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = tags.isEmpty() && !liked && !favorite && !blocked && rejectedAi.isEmpty()
}

/** Lowercase, no leading '#', single spaces. Returns null for blank input. */
fun normalizeTag(raw: String): String? {
    val tag = raw.trim().trimStart('#').trim().lowercase().replace(Regex("\\s+"), " ")
    return tag.ifEmpty { null }
}
