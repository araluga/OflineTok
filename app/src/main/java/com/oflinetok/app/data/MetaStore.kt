package com.oflinetok.app.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists tags, likes and favorites as a small JSON file in app storage.
 * Keyed by MediaStore video id.
 */
class MetaStore(context: Context) {

    private val file = AtomicFile(File(context.filesDir, "video_meta.json"))

    fun load(): Map<Long, VideoMeta> {
        val text = try {
            file.readFully().decodeToString()
        } catch (e: Exception) {
            return emptyMap()
        }
        return try {
            val videos = JSONObject(text).getJSONObject("videos")
            buildMap {
                for (key in videos.keys()) {
                    val obj = videos.getJSONObject(key)
                    val tagsJson = obj.optJSONArray("tags") ?: JSONArray()
                    val tags = (0 until tagsJson.length()).map { tagsJson.getString(it) }.toSet()
                    put(
                        key.toLong(),
                        VideoMeta(
                            tags = tags,
                            liked = obj.optBoolean("liked"),
                            favorite = obj.optBoolean("favorite"),
                            blocked = obj.optBoolean("blocked"),
                            rejectedAi = obj.optJSONArray("rejectedAi").let { a ->
                                if (a == null) emptySet() else (0 until a.length()).map { a.getString(it) }.toSet()
                            },
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun save(meta: Map<Long, VideoMeta>) {
        val videos = JSONObject()
        for ((id, m) in meta) {
            if (m.isEmpty) continue
            videos.put(
                id.toString(),
                JSONObject()
                    .put("tags", JSONArray(m.tags.sorted()))
                    .put("liked", m.liked)
                    .put("favorite", m.favorite)
                    .put("blocked", m.blocked)
                    .put("rejectedAi", JSONArray(m.rejectedAi.sorted())),
            )
        }
        val json = JSONObject().put("version", 1).put("videos", videos).toString()
        val out = file.startWrite()
        try {
            out.write(json.toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }
}
