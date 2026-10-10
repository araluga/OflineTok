package com.oflinetok.app.ui

import android.app.Application
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.oflinetok.app.data.UpdateChecker
import com.oflinetok.app.data.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    /** progress is 0..1, or null while the size is unknown. */
    data class Downloading(val info: UpdateInfo, val progress: Float?) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val apk: Uri) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/** Optional update checks against GitHub releases. Never forces anything on the user. */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val downloads = app.getSystemService(DownloadManager::class.java)
    private var downloadJob: Job? = null

    val currentVersion: String =
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "?"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** The update dialog is visible. Automatic checks only open it when there's something new. */
    private val _dialogOpen = MutableStateFlow(false)
    val dialogOpen: StateFlow<Boolean> = _dialogOpen.asStateFlow()

    private val _autoCheck = MutableStateFlow(prefs.getBoolean("auto", true))
    val autoCheck: StateFlow<Boolean> = _autoCheck.asStateFlow()

    fun setAutoCheck(enabled: Boolean) {
        _autoCheck.value = enabled
        prefs.edit().putBoolean("auto", enabled).apply()
    }

    /** Called on launch: at most once a day, silent unless a new, non-skipped version exists. */
    fun autoCheck() {
        if (!_autoCheck.value || _state.value != UpdateState.Idle) return
        val last = prefs.getLong("lastCheck", 0L)
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        check(silent = true)
    }

    /** The user tapped "Check for updates": always shows the result. */
    fun checkNow() {
        if (_state.value is UpdateState.Downloading) {
            _dialogOpen.value = true
            return
        }
        check(silent = false)
    }

    private fun check(silent: Boolean) {
        _state.value = UpdateState.Checking
        if (!silent) _dialogOpen.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { UpdateChecker.fetchLatest() } }
            prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
            val info = result.getOrNull()
            _state.value = when {
                result.isFailure -> UpdateState.Failed("Couldn't reach GitHub. Check your internet connection.")
                info != null && UpdateChecker.isNewer(info.version, currentVersion) -> UpdateState.Available(info)
                else -> UpdateState.UpToDate
            }
            if (silent) {
                val available = _state.value as? UpdateState.Available
                if (available != null && available.info.version != prefs.getString("skipped", null)) {
                    _dialogOpen.value = true
                } else {
                    _state.value = UpdateState.Idle // Silent checks leave no trace when there's nothing new.
                }
            }
        }
    }

    /** Hides the dialog; the next automatic check (tomorrow) may show it again. */
    fun later() {
        _dialogOpen.value = false
        if (_state.value !is UpdateState.Downloading && _state.value !is UpdateState.ReadyToInstall) {
            _state.value = UpdateState.Idle
        }
    }

    /** Never prompt automatically for this version again (a newer one still will). */
    fun skip(info: UpdateInfo) {
        prefs.edit().putString("skipped", info.version).apply()
        later()
    }

    fun download(info: UpdateInfo) {
        val dir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val name = "OflineTok-${info.version}.apk"
        dir?.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach(File::delete)

        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("OflineTok ${info.version}")
            .setDescription("Downloading update")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(getApplication(), Environment.DIRECTORY_DOWNLOADS, name)
        val id = try {
            downloads.enqueue(request)
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Couldn't start the download.", info)
            return
        }

        _state.value = UpdateState.Downloading(info, null)
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch { watchDownload(id, info) }
    }

    private suspend fun watchDownload(id: Long, info: UpdateInfo) {
        while (true) { // delay() ends the loop when the job is cancelled.
            val next = withContext(Dispatchers.IO) {
                downloads.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                    if (!c.moveToFirst()) return@use UpdateState.Available(info) // Cancelled from the notification.
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> downloads.getUriForDownloadedFile(id)
                            ?.let { UpdateState.ReadyToInstall(info, it) }
                            ?: UpdateState.Failed("The download finished but the file is missing.", info)
                        DownloadManager.STATUS_FAILED -> UpdateState.Failed("The download failed.", info)
                        else -> UpdateState.Downloading(info, if (total > 0) done.toFloat() / total else null)
                    }
                } ?: UpdateState.Failed("The download failed.", info)
            }
            _state.value = next
            if (next !is UpdateState.Downloading) return
            delay(400)
        }
    }

    /** Hands the APK to Android's installer, which asks the user to confirm. */
    fun install(context: Context, apk: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(apk, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Couldn't open the installer", Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
