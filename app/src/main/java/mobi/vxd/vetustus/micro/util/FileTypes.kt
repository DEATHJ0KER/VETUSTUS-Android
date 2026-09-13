package mobi.vxd.vetustus.micro.util

import mobi.vxd.vetustus.micro.data.MediaKind

object FileTypes {
    private val videoExtensions = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "flv",
        "mpeg", "mpg", "m2p", "ps", "ts", "m2ts", "mts",
    )
    private val audioExtensions = setOf(
        "mp3", "flac", "m4a", "aac", "adts", "ogg", "oga", "opus",
        "wav", "wave", "ac3", "ec3", "eac3", "ac4", "amr", "mka",
    )
    private val archiveExtensions = setOf("zip")

    fun extension(filename: String): String = filename.substringAfterLast('.', "").lowercase()

    fun kind(filename: String): MediaKind = when (extension(filename)) {
        in videoExtensions -> MediaKind.VIDEO
        in audioExtensions -> MediaKind.AUDIO
        in archiveExtensions -> MediaKind.ARCHIVE
        else -> MediaKind.OTHER
    }

    fun mimeType(filename: String): String = when (extension(filename)) {
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
        "zip" -> "application/zip"
        "srt" -> "application/x-subrip"
        "ass", "ssa" -> "text/x-ssa"
        "vtt", "webvtt" -> "text/vtt"
        else -> "application/octet-stream"
    }

    fun canTryInternalPlayback(filename: String): Boolean = kind(filename) in setOf(MediaKind.VIDEO, MediaKind.AUDIO)

    fun isArchive(filename: String): Boolean = kind(filename) == MediaKind.ARCHIVE
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
