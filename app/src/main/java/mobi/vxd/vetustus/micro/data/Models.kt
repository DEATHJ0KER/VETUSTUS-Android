package mobi.vxd.vetustus.micro.data

data class XdccSearchResult(
    val source: String,
    val network: String,
    val channel: String,
    val bot: String,
    val pack: Int,
    val gets: Int,
    val sizeLabel: String,
    val filename: String,
)

enum class DownloadState {
    QUEUED,
    CONNECTING,
    REQUESTING,
    WAITING,
    DOWNLOADING,
    PUBLISHING,
    EXTRACTING,
    COMPLETE,
    PAUSED,
    INTERRUPTED,
    FAILED,
    CANCELLED;

    val terminal: Boolean
        get() = this in setOf(COMPLETE, PAUSED, INTERRUPTED, FAILED, CANCELLED)

    val canResume: Boolean
        get() = this in setOf(PAUSED, INTERRUPTED, FAILED, CANCELLED)

    companion object {
        fun fromDb(value: String): DownloadState =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FAILED
    }
}

data class DownloadItem(
    val id: String,
    val source: String,
    val network: String,
    val channel: String,
    val bot: String,
    val pack: Int,
    val requestedFilename: String,
    val filename: String,
    val state: DownloadState,
    val bytesDone: Long,
    val bytesTotal: Long,
    val speedBps: Long,
    val queuePosition: Int,
    val queueTotal: Int,
    val queueEta: String,
    val statusMessage: String,
    val errorCode: String,
    val errorMessage: String,
    val partialPath: String,
    val contentUri: String,
    val mimeType: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val progress: Float
        get() = if (bytesTotal > 0L) (bytesDone.toDouble() / bytesTotal).toFloat().coerceIn(0f, 1f) else 0f
}

enum class MediaKind {
    VIDEO,
    AUDIO,
    ARCHIVE,
    OTHER;

    companion object {
        fun fromDb(value: String): MediaKind =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
    }
}

data class LibraryItem(
    val id: String,
    val downloadId: String,
    val displayName: String,
    val contentUri: String,
    val mimeType: String,
    val sizeBytes: Long,
    val kind: MediaKind,
    val parentArchive: String,
    val createdAt: Long,
) {
    val playable: Boolean get() = kind == MediaKind.VIDEO || kind == MediaKind.AUDIO
}

data class AppSettings(
    val nick: String,
    val autoExtractArchives: Boolean,
    val deleteArchiveAfterExtract: Boolean,
    val allowPrivateDccHosts: Boolean,
)
