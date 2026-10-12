package com.oflinetok.app.data

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageScannerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun video(dir: File, name: String, bytes: Int = 60_000) =
        File(dir, name).apply { parentFile!!.mkdirs(); writeBytes(ByteArray(bytes)) }

    private fun names(found: List<StorageScanner.FoundVideo>) = found.map { it.file.relativeTo(tmp.root).path.replace('\\', '/') }.sorted()

    @Test
    fun findsVideosEverywhereExceptHiddenJunkAndAndroidData() {
        val root = tmp.newFolder("storage")
        video(root, "DCIM/Camera/a.mp4")
        video(root, "WhatsApp/Media/.Statuses/b.mp4")
        File(root, "Telegram/Telegram Video").mkdirs()
        File(root, "Telegram/Telegram Video/.nomedia").writeText("")
        video(root, "Telegram/Telegram Video/c.MKV")
        video(root, "Android/media/org.telegram.messenger/Telegram/d.mp4")
        video(root, "Android/data/org.telegram.messenger/cache/e.mp4") // Unreadable on real phones; skipped.
        video(root, "DCIM/.thumbnails/f.mp4")
        video(root, "Download/tiny.mp4", bytes = 100) // Too small to be a real video.
        File(root, "Pictures/photo.jpg").apply { parentFile!!.mkdirs(); writeText("x") }

        val found = StorageScanner(File(tmp.root, "index.bin")).scan(listOf(root))

        assertEquals(
            listOf(
                "storage/Android/media/org.telegram.messenger/Telegram/d.mp4",
                "storage/DCIM/Camera/a.mp4",
                "storage/Telegram/Telegram Video/c.MKV",
                "storage/WhatsApp/Media/.Statuses/b.mp4",
            ),
            names(found),
        )
    }

    @Test
    fun secondScanSeesNewAndDeletedFilesAndCacheMatches() {
        val root = tmp.newFolder("storage")
        val index = File(tmp.root, "index.bin")
        video(root, "Movies/a.mp4")
        val old = video(root, "Movies/old.mp4")
        assertEquals(2, StorageScanner(index).scan(listOf(root)).size)

        old.delete()
        video(root, "Movies/new.webm")
        video(root, "Movies/Sub/deep.mp4")
        // Folder times on some file systems have 1-second resolution; make the change visible.
        File(root, "Movies").setLastModified(System.currentTimeMillis() + 5_000)

        val second = StorageScanner(index).scan(listOf(root))
        assertEquals(
            listOf("storage/Movies/Sub/deep.mp4", "storage/Movies/a.mp4", "storage/Movies/new.webm"),
            names(second),
        )
        // What the next launch shows instantly equals what was just found.
        assertEquals(names(second), names(StorageScanner(index).cached(listOf(root))))
    }

    @Test
    fun unchangedFolderIsNotListedAgainButSizesRefresh() {
        val root = tmp.newFolder("storage")
        val index = File(tmp.root, "index.bin")
        val a = video(root, "Movies/a.mp4")
        StorageScanner(index).scan(listOf(root))

        val folderTime = File(root, "Movies").lastModified()
        a.writeBytes(ByteArray(90_000)) // Same entries, bigger file (e.g. a copy finished).
        File(root, "Movies").setLastModified(folderTime)

        val found = StorageScanner(index).scan(listOf(root)).single()
        assertEquals(90_000L, found.size)
    }
}
