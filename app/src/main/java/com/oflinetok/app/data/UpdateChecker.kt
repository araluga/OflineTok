package com.oflinetok.app.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    /** e.g. "1.4" (no leading "v"). */
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String,
)

/** Looks up the newest GitHub release. This is the only thing the app ever does online. */
object UpdateChecker {
    const val REPO = "araluga/OflineTok"
    const val REPO_URL = "https://github.com/$REPO"

    /** Blocking; call off the main thread. Returns null if the latest release has no APK. */
    fun fetchLatest(): UpdateInfo? {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "OflineTok")
            if (conn.responseCode != 200) error("GitHub answered ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.use { it.readBytes().decodeToString() })
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") } ?: return null
            return UpdateInfo(
                version = json.getString("tag_name").removePrefix("v"),
                notes = json.optString("body").takeUnless { json.isNull("body") }.orEmpty().trim(),
                apkUrl = apk.getString("browser_download_url"),
                apkSize = apk.optLong("size"),
                pageUrl = json.getString("html_url"),
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Numeric comparison, so "1.10" is newer than "1.9". */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
