package mobi.vxd.vetustus.micro.util

import mobi.vxd.vetustus.micro.data.MediaKind

object FileTypes {
    private val videoExtensions = setOf("mp4", "mkv", "webm", "m4v")
    private val audioExtensions = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")
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
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        "zip" -> "application/zip"
        "srt" -> "application/x-subrip"
        "ass", "ssa" -> "text/x-ssa"
        else -> "application/octet-stream"
    }

    fun isAlphaSupported(filename: String): Boolean = extension(filename) in setOf("mp4", "mkv", "mp3", "flac", "zip")
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
