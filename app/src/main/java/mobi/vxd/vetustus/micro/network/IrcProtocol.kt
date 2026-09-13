package mobi.vxd.vetustus.micro.network

import java.io.File

data class IrcMessage(
    val tags: Map<String, String>,
    val prefix: String,
    val command: String,
    val params: List<String>,
    val trailing: String,
) {
    val nick: String get() = prefix.substringBefore('!')
}

data class DccOffer(
    val nick: String,
    val filename: String,
    val host: String,
    val port: Int,
    val bytesTotal: Long,
)

data class QueueStatus(
    val position: Int = 0,
    val total: Int = 0,
    val eta: String = "",
    val message: String,
)

object IrcProtocol {
    fun parseLine(raw: String): IrcMessage {
        var source = raw.trimEnd('\r', '\n')
        val tags = linkedMapOf<String, String>()
        var prefix = ""
        if (source.startsWith('@')) {
            val space = source.indexOf(' ')
            val rawTags = if (space >= 0) source.substring(1, space) else source.drop(1)
            source = if (space >= 0) source.substring(space + 1) else ""
            rawTags.split(';').forEach { token ->
                val at = token.indexOf('=')
                val key = if (at >= 0) token.substring(0, at) else token
                val value = if (at >= 0) token.substring(at + 1) else ""
                tags[key] = value
                    .replace("\\:", ";")
                    .replace("\\s", " ")
                    .replace("\\r", "\r")
                    .replace("\\n", "\n")
                    .replace("\\\\", "\\")
            }
        }
        if (source.startsWith(':')) {
            val space = source.indexOf(' ')
            prefix = if (space >= 0) source.substring(1, space) else source.drop(1)
            source = if (space >= 0) source.substring(space + 1) else ""
        }
        val trailingAt = source.indexOf(" :")
        val trailing = if (trailingAt >= 0) source.substring(trailingAt + 2) else ""
        if (trailingAt >= 0) source = source.substring(0, trailingAt)
        val pieces = source.trim().split(Regex("\\s+")).filter(String::isNotBlank).toMutableList()
        val command = pieces.removeFirstOrNull()?.uppercase().orEmpty()
        return IrcMessage(tags, prefix, command, pieces, trailing)
    }

    fun parseDccOffer(message: IrcMessage, expectedBot: String): DccOffer? {
        if (message.command !in setOf("PRIVMSG", "NOTICE") || !ircEquals(message.nick, expectedBot)) return null
        val text = message.trailing
        if (!text.startsWith('\u0001') || !text.endsWith('\u0001')) return null
        val inner = text.substring(1, text.length - 1)
        if (!inner.startsWith("DCC SEND ", ignoreCase = true)) return null
        val args = parseDccArguments(inner.substring(9))
        if (args.size < 3) return null
        val filename = sanitizeFilename(args[0])
        val host = decodeDccIp(args[1])
        val port = args[2].toIntOrNull() ?: return null
        val bytes = args.getOrNull(3)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        if (filename.isBlank() || host.isBlank() || port !in 1..65535) return null
        return DccOffer(message.nick, filename, host, port, bytes)
    }

    fun parseDccAccept(message: IrcMessage, expectedBot: String, offer: DccOffer): Long? {
        if (message.command !in setOf("PRIVMSG", "NOTICE") || !ircEquals(message.nick, expectedBot)) return null
        val text = message.trailing
        if (!text.startsWith('\u0001') || !text.endsWith('\u0001')) return null
        val inner = text.substring(1, text.length - 1)
        if (!inner.startsWith("DCC ACCEPT ", ignoreCase = true)) return null
        val args = parseDccArguments(inner.substring(11))
        if (args.size < 3) return null
        val filename = sanitizeFilename(args[0])
        val port = args[1].toIntOrNull() ?: return null
        val offset = args[2].toLongOrNull() ?: return null
        return offset.takeIf { ircEquals(filename, offer.filename) && port == offer.port && it >= 0L }
    }

    fun parseQueueStatus(message: IrcMessage, expectedBot: String): QueueStatus? {
        if (message.command !in setOf("PRIVMSG", "NOTICE") || !ircEquals(message.nick, expectedBot)) return null
        val text = message.trailing.trim()
        if (!QUEUE_WORDS.containsMatchIn(text)) return null
        val pair = QUEUE_PAIR.find(text)
        val position = pair?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: QUEUE_POSITION.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: 0
        val total = pair?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
        val eta = ETA.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        return QueueStatus(position, total, eta, text.take(300))
    }

    fun parseDccArguments(value: String): List<String> {
        val args = mutableListOf<String>()
        val token = StringBuilder()
        var quoted = false
        var escaped = false
        for (character in value) {
            when {
                escaped -> {
                    token.append(character)
                    escaped = false
                }
                character == '\\' && quoted -> escaped = true
                character == '"' -> quoted = !quoted
                character.isWhitespace() && !quoted -> if (token.isNotEmpty()) {
                    args += token.toString()
                    token.clear()
                }
                else -> token.append(character)
            }
        }
        if (token.isNotEmpty()) args += token.toString()
        return args
    }

    fun decodeDccIp(raw: String): String {
        val value = raw.trim()
        val numeric = value.toLongOrNull()
        if (numeric != null && numeric in 0L..0xffff_ffffL) {
            return listOf(
                (numeric shr 24) and 255,
                (numeric shr 16) and 255,
                (numeric shr 8) and 255,
                numeric and 255,
            ).joinToString(".")
        }
        return value.removePrefix("[").removeSuffix("]")
    }

    fun sanitizeFilename(raw: String): String {
        val base = raw.replace('\\', '/').substringAfterLast('/').trim()
        return File(base.replace(Regex("[\\u0000-\\u001f\\u007f]"), "_")).name
            .replace(Regex("[<>:\"/\\\\|?*]"), "_")
            .trim(' ', '.')
            .take(180)
            .ifBlank { "download.bin" }
    }

    fun safeToken(value: String): String =
        value.replace(Regex("[\\r\\n\\u0000 ]+"), "_").take(80)

    fun ircEquals(left: String, right: String): Boolean = ircFold(left) == ircFold(right)

    private fun ircFold(value: String): String = value.lowercase()
        .replace('[', '{')
        .replace(']', '}')
        .replace('\\', '|')
        .replace('^', '~')

    private val QUEUE_WORDS = Regex("\\b(queue|queued|position|slot|coda|attesa)\\b", RegexOption.IGNORE_CASE)
    private val QUEUE_PAIR = Regex("(?:position|queue|coda)?\\s*#?(\\d+)\\s*(?:of|/|su)\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val QUEUE_POSITION = Regex("(?:position|queue|coda)\\D{0,12}(\\d+)", RegexOption.IGNORE_CASE)
    private val ETA = Regex("(?:eta|estimated|attesa)\\s*[:=-]?\\s*([^.;]+)", RegexOption.IGNORE_CASE)
}
