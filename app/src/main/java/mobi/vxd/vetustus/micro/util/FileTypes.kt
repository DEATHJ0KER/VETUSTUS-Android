package mobi.vxd.vetustus.micro.util

import mobi.vxd.vetustus.micro.data.MediaKind

enum class ArchiveKind {
    ZIP,
    RAR,
    TAR,
    TAR_GZ,
    TAR_BZ2,
    TAR_XZ,
}

object FileTypes {
    private val videoExtensions = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "flv",
        "mpeg", "mpg", "m2p", "ps", "ts", "m2ts", "mts",
    )
    private val audioExtensions = setOf(
        "mp3", "flac", "m4a", "aac", "adts", "ogg", "oga", "opus",
        "wav", "wave", "ac3", "ec3", "eac3", "ac4", "amr", "mka",
    )

    fun extension(filename: String): String = filename.substringAfterLast('.', "").lowercase()

    fun archiveKind(filename: String): ArchiveKind? {
        val lower = filename.trim().lowercase()
        return when {
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> ArchiveKind.TAR_GZ
            lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") || lower.endsWith(".tbz") -> ArchiveKind.TAR_BZ2
            lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> ArchiveKind.TAR_XZ
            lower.endsWith(".tar") -> ArchiveKind.TAR
            lower.endsWith(".rar") -> ArchiveKind.RAR
            lower.endsWith(".zip") -> ArchiveKind.ZIP
            else -> null
        }
    }

    fun kind(filename: String): MediaKind = when {
        archiveKind(filename) != null -> MediaKind.ARCHIVE
        extension(filename) in videoExtensions -> MediaKind.VIDEO
        extension(filename) in audioExtensions -> MediaKind.AUDIO
        else -> MediaKind.OTHER
    }

    fun mimeType(filename: String): String {
        archiveKind(filename)?.let { archive ->
            return when (archive) {
                ArchiveKind.ZIP -> "application/zip"
                ArchiveKind.RAR -> "application/vnd.rar"
                ArchiveKind.TAR -> "application/x-tar"
                ArchiveKind.TAR_GZ -> "application/gzip"
                ArchiveKind.TAR_BZ2 -> "application/x-bzip2"
                ArchiveKind.TAR_XZ -> "application/x-xz"
            }
        }
        return when (extension(filename)) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mka" -> "audio/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "flv" -> "video/x-flv"
            "mpeg", "mpg", "m2p", "ps" -> "video/mpeg"
            "ts", "m2ts", "mts" -> "video/mp2t"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            "aac", "adts" -> "audio/aac"
            "ogg", "oga", "opus" -> "audio/ogg"
            "wav", "wave" -> "audio/wav"
            "ac3" -> "audio/ac3"
            "ec3", "eac3" -> "audio/eac3"
            "ac4" -> "audio/ac4"
            "amr" -> "audio/amr"
            "srt" -> "application/x-subrip"
            "ass", "ssa" -> "text/x-ssa"
            "vtt", "webvtt" -> "text/vtt"
            else -> "application/octet-stream"
        }
    }

    fun canTryInternalPlayback(filename: String): Boolean = kind(filename) in setOf(MediaKind.VIDEO, MediaKind.AUDIO)

    fun isArchive(filename: String): Boolean = archiveKind(filename) != null

    fun isExtractableMedia(filename: String): Boolean = kind(filename) in setOf(MediaKind.VIDEO, MediaKind.AUDIO)
}

fun Long.formatBytes(): String {
    if (this < 1024L) return "$this B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = toDouble()
    var unit = -1
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (value >= 100) "%.0f %s".format(value, units[unit]) else "%.1f %s".format(value, units[unit])
}
