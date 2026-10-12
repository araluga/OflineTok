package com.oflinetok.app.ai

import android.util.AtomicFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Saves one embedding per video (2 KB each) so videos are analyzed only once.
 * [Entry.key] changes when the file behind an id changes, which triggers re-analysis.
 */
class EmbeddingStore(dir: File) {
    class Entry(val key: Long, val vector: FloatArray)

    private val file = AtomicFile(File(dir.apply { mkdirs() }, "embeddings.bin"))

    fun load(): HashMap<Long, Entry> {
        val map = HashMap<Long, Entry>()
        runCatching {
            DataInputStream(file.openRead().buffered()).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != ModelFiles.VERSION) return map
                val dim = input.readInt()
                repeat(input.readInt()) {
                    val id = input.readLong()
                    val key = input.readLong()
                    map[id] = Entry(key, FloatArray(dim) { input.readFloat() })
                }
            }
        }
        return map
    }

    fun save(entries: Map<Long, Entry>) {
        val out = file.startWrite()
        try {
            val data = DataOutputStream(out.buffered())
            data.writeInt(MAGIC)
            data.writeInt(ModelFiles.VERSION)
            data.writeInt(entries.values.firstOrNull()?.vector?.size ?: 0)
            data.writeInt(entries.size)
            for ((id, e) in entries) {
                data.writeLong(id)
                data.writeLong(e.key)
                e.vector.forEach(data::writeFloat)
            }
            data.flush()
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }

    fun delete() = file.delete()

    private companion object {
        const val MAGIC = 0x4F544531 // "OTE1"
    }
}
