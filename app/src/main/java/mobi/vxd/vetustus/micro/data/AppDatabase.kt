package mobi.vxd.vetustus.micro.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE downloads (
                id TEXT PRIMARY KEY,
                source TEXT NOT NULL DEFAULT '',
                network TEXT NOT NULL,
                channel TEXT NOT NULL DEFAULT '',
                bot TEXT NOT NULL,
                pack INTEGER NOT NULL,
                requested_filename TEXT NOT NULL DEFAULT '',
                filename TEXT NOT NULL DEFAULT '',
                state TEXT NOT NULL,
                bytes_done INTEGER NOT NULL DEFAULT 0,
                bytes_total INTEGER NOT NULL DEFAULT 0,
                speed_bps INTEGER NOT NULL DEFAULT 0,
                queue_position INTEGER NOT NULL DEFAULT 0,
                queue_total INTEGER NOT NULL DEFAULT 0,
                queue_eta TEXT NOT NULL DEFAULT '',
                status_message TEXT NOT NULL DEFAULT '',
                error_code TEXT NOT NULL DEFAULT '',
                error_message TEXT NOT NULL DEFAULT '',
                partial_path TEXT NOT NULL,
                content_uri TEXT NOT NULL DEFAULT '',
                mime_type TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX downloads_state_idx ON downloads(state, updated_at)")
        db.execSQL(
            """
            CREATE TABLE library (
                id TEXT PRIMARY KEY,
                download_id TEXT NOT NULL DEFAULT '',
                display_name TEXT NOT NULL,
                content_uri TEXT NOT NULL UNIQUE,
                mime_type TEXT NOT NULL DEFAULT 'application/octet-stream',
                size_bytes INTEGER NOT NULL DEFAULT 0,
                kind TEXT NOT NULL DEFAULT 'OTHER',
                parent_archive TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX library_kind_idx ON library(kind, created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun recoverInterruptedTransfers() {
        val now = System.currentTimeMillis()
        writableDatabase.update(
            "downloads",
            ContentValues().apply {
                put("state", DownloadState.COMPLETE.name)
                put("speed_bps", 0L)
                put("status_message", "Download completo · estrazione ZIP interrotta")
                put("error_code", "ZIP_EXTRACTION_INTERRUPTED")
                put("error_message", "L'archivio originale è disponibile in Libreria")
                put("updated_at", now)
            },
            "state=? AND content_uri<>''",
            arrayOf(DownloadState.EXTRACTING.name),
        )
        val values = ContentValues().apply {
            put("state", DownloadState.INTERRUPTED.name)
            put("speed_bps", 0L)
            put("status_message", "Download interrotto: premi Riprendi")
            put("updated_at", now)
        }
        writableDatabase.update(
            "downloads",
            values,
            "state IN (?,?,?,?,?,?) OR (state=? AND content_uri='')",
            arrayOf(
                DownloadState.QUEUED.name,
                DownloadState.CONNECTING.name,
                DownloadState.REQUESTING.name,
                DownloadState.WAITING.name,
                DownloadState.DOWNLOADING.name,
                DownloadState.PUBLISHING.name,
                DownloadState.EXTRACTING.name,
            ),
        )
    }

    @Synchronized
    fun insertDownload(item: DownloadItem) {
        writableDatabase.insertOrThrow("downloads", null, item.toValues())
    }

    @Synchronized
    fun updateDownload(id: String, values: ContentValues) {
        values.put("updated_at", System.currentTimeMillis())
        writableDatabase.update("downloads", values, "id=?", arrayOf(id))
    }

    @Synchronized
    fun findDownload(id: String): DownloadItem? = readableDatabase.query(
        "downloads",
        null,
        "id=?",
        arrayOf(id),
        null,
        null,
        null,
        "1",
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toDownload() else null }

    @Synchronized
    fun listDownloads(): List<DownloadItem> = readableDatabase.query(
        "downloads",
        null,
        null,
        null,
        null,
        null,
        "created_at DESC",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toDownload()) } }

    @Synchronized
    fun deleteDownload(id: String) {
        writableDatabase.delete("downloads", "id=?", arrayOf(id))
    }

    @Synchronized
    fun insertLibraryItem(item: LibraryItem) {
        writableDatabase.insertWithOnConflict(
            "library",
            null,
            ContentValues().apply {
                put("id", item.id)
                put("download_id", item.downloadId)
                put("display_name", item.displayName)
                put("content_uri", item.contentUri)
                put("mime_type", item.mimeType)
                put("size_bytes", item.sizeBytes)
                put("kind", item.kind.name)
                put("parent_archive", item.parentArchive)
                put("created_at", item.createdAt)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    @Synchronized
    fun listLibrary(): List<LibraryItem> = readableDatabase.query(
        "library",
        null,
        null,
        null,
        null,
        null,
        "created_at DESC",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toLibrary()) } }

    @Synchronized
    fun findLibraryItem(id: String): LibraryItem? = readableDatabase.query(
        "library",
        null,
        "id=?",
        arrayOf(id),
        null,
        null,
        null,
        "1",
    ).use { cursor -> if (cursor.moveToFirst()) cursor.toLibrary() else null }

    @Synchronized
    fun deleteLibraryItem(id: String) {
        writableDatabase.delete("library", "id=?", arrayOf(id))
    }

    private fun DownloadItem.toValues() = ContentValues().apply {
        put("id", id)
        put("source", source)
        put("network", network)
        put("channel", channel)
        put("bot", bot)
        put("pack", pack)
        put("requested_filename", requestedFilename)
        put("filename", filename)
        put("state", state.name)
        put("bytes_done", bytesDone)
        put("bytes_total", bytesTotal)
        put("speed_bps", speedBps)
        put("queue_position", queuePosition)
        put("queue_total", queueTotal)
        put("queue_eta", queueEta)
        put("status_message", statusMessage)
        put("error_code", errorCode)
        put("error_message", errorMessage)
        put("partial_path", partialPath)
        put("content_uri", contentUri)
        put("mime_type", mimeType)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    private fun Cursor.toDownload() = DownloadItem(
        id = text("id"),
        source = text("source"),
        network = text("network"),
        channel = text("channel"),
        bot = text("bot"),
        pack = int("pack"),
        requestedFilename = text("requested_filename"),
        filename = text("filename"),
        state = DownloadState.fromDb(text("state")),
        bytesDone = long("bytes_done"),
        bytesTotal = long("bytes_total"),
        speedBps = long("speed_bps"),
        queuePosition = int("queue_position"),
        queueTotal = int("queue_total"),
        queueEta = text("queue_eta"),
        statusMessage = text("status_message"),
        errorCode = text("error_code"),
        errorMessage = text("error_message"),
        partialPath = text("partial_path"),
        contentUri = text("content_uri"),
        mimeType = text("mime_type"),
        createdAt = long("created_at"),
        updatedAt = long("updated_at"),
    )

    private fun Cursor.toLibrary() = LibraryItem(
        id = text("id"),
        downloadId = text("download_id"),
        displayName = text("display_name"),
        contentUri = text("content_uri"),
        mimeType = text("mime_type"),
        sizeBytes = long("size_bytes"),
        kind = MediaKind.fromDb(text("kind")),
        parentArchive = text("parent_archive"),
        createdAt = long("created_at"),
    )

    private fun Cursor.text(name: String) = getString(getColumnIndexOrThrow(name)).orEmpty()
    private fun Cursor.long(name: String) = getLong(getColumnIndexOrThrow(name))
    private fun Cursor.int(name: String) = getInt(getColumnIndexOrThrow(name))

    private companion object {
        const val DATABASE_NAME = "vetustus_micro.db"
        const val DATABASE_VERSION = 1
    }
}
