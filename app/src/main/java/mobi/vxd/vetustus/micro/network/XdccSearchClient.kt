package mobi.vxd.vetustus.micro.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.vxd.vetustus.micro.data.XdccSearchResult
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class XdccSearchClient {
    suspend fun search(query: String): List<XdccSearchResult> = withContext(Dispatchers.IO) {
        val term = query.trim()
        require(term.length in 2..120) { "La ricerca deve contenere da 2 a 120 caratteri" }
        val encoded = URLEncoder.encode(term, Charsets.UTF_8.name())
        val connection = URL("https://www.xdcc.eu/search.php?searchkey=$encoded")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "VETUSTUS-Micro/0.1 (+https://vxd.mobi/vetustus/)")
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            val status = connection.responseCode
            if (status !in 200..299) error("Il motore XDCC ha risposto HTTP $status")
            val html = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_RESPONSE_BYTES) error("Risposta del motore XDCC troppo grande")
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
            XdccHtmlParser.parse(html)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024
    }
}

object XdccHtmlParser {
    private val rowRegex = Regex("<tr\\b[^>]*>[\\s\\S]*?</tr>", RegexOption.IGNORE_CASE)
    private val cellRegex = Regex("<td\\b[^>]*>([\\s\\S]*?)</td>", RegexOption.IGNORE_CASE)
    private val scriptRegex = Regex("<script\\b[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
    private val styleRegex = Regex("<style\\b[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
    private val tagRegex = Regex("<[^>]+>")
    private val entityRegex = Regex("&(#x?[0-9a-f]+|[a-z]+);", RegexOption.IGNORE_CASE)
    private val whitespaceRegex = Regex("\\s+")

    fun parse(html: String, limit: Int = 200): List<XdccSearchResult> = buildList {
        for (row in rowRegex.findAll(html)) {
            val cells = cellRegex.findAll(row.value).map { stripHtml(it.groupValues[1]) }.toList()
            if (cells.size < 7) continue
            val pack = Regex("#?\\s*(\\d{1,9})").find(cells[3])?.groupValues?.get(1)?.toIntOrNull()
                ?: continue
            val channel = Regex("#[^\\s]+", RegexOption.IGNORE_CASE).find(cells[1])?.value ?: cells[1]
            val network = cells[0].trim()
            val bot = cells[2].trim()
            val gets = cells[4].filter(Char::isDigit).toIntOrNull() ?: 0
            val sizeLabel = cells[5].trim()
            val filename = cells.drop(6).joinToString(" ").trim()
            if (network.isBlank() || channel.isBlank() || bot.isBlank() || pack <= 0 || filename.isBlank()) continue
            add(
                XdccSearchResult(
                    source = "xdcc.eu",
                    network = network,
                    channel = channel,
                    bot = bot,
                    pack = pack,
                    gets = gets,
                    sizeLabel = sizeLabel,
                    filename = filename,
                ),
            )
            if (this.size >= limit.coerceIn(1, 500)) break
        }
    }

    private fun stripHtml(value: String): String {
        val withoutTags = value
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
            .replace(scriptRegex, " ")
            .replace(styleRegex, " ")
            .replace(tagRegex, " ")
        val decoded = entityRegex.replace(withoutTags) { match -> decodeEntity(match.groupValues[1]) ?: match.value }
        return decoded.replace(whitespaceRegex, " ").trim()
    }

    private fun decodeEntity(entity: String): String? {
        if (entity.startsWith("#x", ignoreCase = true)) {
            return entity.drop(2).toIntOrNull(16)?.let(::safeCodePoint)
        }
        if (entity.startsWith('#')) {
            return entity.drop(1).toIntOrNull()?.let(::safeCodePoint)
        }
        return when (entity.lowercase()) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos", "#39" -> "'"
            "nbsp" -> " "
            else -> null
        }
    }

    private fun safeCodePoint(value: Int): String? =
        if (Character.isValidCodePoint(value)) String(Character.toChars(value)) else null
}
