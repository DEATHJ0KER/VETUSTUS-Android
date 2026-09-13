package mobi.vxd.vetustus.micro.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

class DownloadRepository(
    private val context: Context,
    private val database: AppDatabase,
) {
    private val _items = MutableStateFlow(database.listDownloads())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    @Synchronized
    fun create(result: XdccSearchResult): DownloadItem {
        val id = UUID.randomUUID().toString()
        val privateRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val transferDir = File(privateRoot, "transfers").apply { mkdirs() }
        val now = System.currentTimeMillis()
        val item = DownloadItem(
            id = id,
            source = result.source,
            network = result.network,
            channel = result.channel,
            bot = result.bot,
            pack = result.pack,
            requestedFilename = result.filename,
            filename = result.filename,
            state = DownloadState.QUEUED,
            bytesDone = 0,
            bytesTotal = 0,
            speedBps = 0,
            queuePosition = 0,
            queueTotal = 0,
            queueEta = "",
            statusMessage = "In coda locale",
            errorCode = "",
            errorMessage = "",
            partialPath = File(transferDir, "$id.part").absolutePath,
            contentUri = "",
            mimeType = "",
            createdAt = now,
            updatedAt = now,
        )
        database.insertDownload(item)
        refresh()
        return item
    }

    fun find(id: String): DownloadItem? = database.findDownload(id)

    fun state(id: String, state: DownloadState, message: String = "", errorCode: String = "", error: String = "") {
        database.updateDownload(id, ContentValues().apply {
            put("state", state.name)
            put("status_message", message)
            put("error_code", errorCode)
            put("error_message", error)
            if (state != DownloadState.DOWNLOADING) put("speed_bps", 0L)
        })
        refresh()
    }

    fun offer(id: String, filename: String, bytesTotal: Long) {
        database.updateDownload(id, ContentValues().apply {
            put("filename", filename)
            put("bytes_total", bytesTotal)
            put("status_message", "Offerta DCC ricevuta")
        })
        refresh()
    }

    fun message(id: String, message: String) {
        database.updateDownload(id, ContentValues().apply { put("status_message", message.take(300)) })
        refresh()
    }

    fun queue(id: String, position: Int, total: Int, eta: String, message: String) {
        database.updateDownload(id, ContentValues().apply {
            put("state", DownloadState.WAITING.name)
            put("queue_position", position)
            put("queue_total", total)
            put("queue_eta", eta)
            put("status_message", message)
        })
        refresh()
    }

    fun progress(id: String, done: Long, total: Long, speed: Long) {
        database.updateDownload(id, ContentValues().apply {
            put("state", DownloadState.DOWNLOADING.name)
            put("bytes_done", done)
            put("bytes_total", total)
            put("speed_bps", speed)
            put("status_message", "Download DCC")
            put("error_code", "")
            put("error_message", "")
        })
        refresh()
    }

    fun completed(id: String, contentUri: String, mimeType: String, filename: String, bytes: Long) {
        database.updateDownload(id, ContentValues().apply {
            put("state", DownloadState.COMPLETE.name)
            put("content_uri", contentUri)
            put("mime_type", mimeType)
            put("filename", filename)
            put("bytes_done", bytes)
            put("bytes_total", bytes)
            put("speed_bps", 0L)
            put("status_message", "Completato")
            put("error_code", "")
            put("error_message", "")
        })
        refresh()
    }

    fun delete(id: String, deletePartial: Boolean = true) {
        if (deletePartial) database.findDownload(id)?.partialPath?.let(::File)?.delete()
        database.deleteDownload(id)
        refresh()
    }

    @Synchronized
    fun refresh() {
        _items.value = database.listDownloads()
    }
}

class LibraryRepository(private val database: AppDatabase) {
    private val _items = MutableStateFlow(database.listLibrary())
    val items: StateFlow<List<LibraryItem>> = _items.asStateFlow()

    fun insert(item: LibraryItem) {
        database.insertLibraryItem(item)
        refresh()
    }

    fun find(id: String): LibraryItem? = database.findLibraryItem(id)

    fun delete(id: String) {
        database.deleteLibraryItem(id)
        refresh()
    }

    @Synchronized
    fun refresh() {
        _items.value = database.listLibrary()
    }
}

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("vetustus_micro_settings", Context.MODE_PRIVATE)
    private val defaultNick: String = preferences.getString(KEY_NICK, null) ?: run {
        val generated = "VxDM${UUID.randomUUID().toString().replace("-", "").take(6)}"
        preferences.edit().putString(KEY_NICK, generated).apply()
        generated
    }
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setNick(value: String) {
        val clean = value.trim().replace(Regex("[^A-Za-z0-9_\\-\\[\\]{}|`^]"), "").take(24)
        if (clean.length < 3) return
        preferences.edit().putString(KEY_NICK, clean).apply()
        refresh()
    }

    fun setAutoExtractZip(value: Boolean) = writeBoolean(KEY_AUTO_EXTRACT, value)
    fun setDeleteArchiveAfterExtract(value: Boolean) = writeBoolean(KEY_DELETE_ARCHIVE, value)
    fun setAllowPrivateDccHosts(value: Boolean) = writeBoolean(KEY_ALLOW_PRIVATE_DCC, value)

    private fun writeBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
        refresh()
    }

    private fun read() = AppSettings(
        nick = preferences.getString(KEY_NICK, defaultNick) ?: defaultNick,
        autoExtractZip = preferences.getBoolean(KEY_AUTO_EXTRACT, true),
        deleteArchiveAfterExtract = preferences.getBoolean(KEY_DELETE_ARCHIVE, false),
        allowPrivateDccHosts = preferences.getBoolean(KEY_ALLOW_PRIVATE_DCC, false),
    )

    private fun refresh() {
        _settings.value = read()
    }

    private companion object {
        const val KEY_NICK = "irc_nick"
        const val KEY_AUTO_EXTRACT = "archive_auto_extract_zip"
        const val KEY_DELETE_ARCHIVE = "archive_delete_after_extract"
        const val KEY_ALLOW_PRIVATE_DCC = "dcc_allow_private_hosts"
    }
}
