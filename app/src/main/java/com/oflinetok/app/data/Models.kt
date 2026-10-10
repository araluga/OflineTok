package com.oflinetok.app.data

import android.net.Uri

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val dateAdded: Long,
    val folder: String?,
)

data class VideoMeta(
    val tags: Set<String> = emptySet(),
    val liked: Boolean = false,
    val favorite: Boolean = false,
) {
    val isEmpty: Boolean get() = tags.isEmpty() && !liked && !favorite
}

/** Lowercase, no leading '#', single spaces. Returns null for blank input. */
fun normalizeTag(raw: String): String? {
    val tag = raw.trim().trimStart('#').trim().lowercase().replace(Regex("\\s+"), " ")
    return tag.ifEmpty { null }
}
