package mobi.vxd.vetustus.micro.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XdccHtmlParserTest {
    @Test
    fun parsesDesktopCompatibleResultRows() {
        val html = """
            <table>
              <tr>
                <td>Rizon</td>
                <td><a>#movies</a></td>
                <td>VxD|Bot</td>
                <td>#4242</td>
                <td>1,337x</td>
                <td>1.4 GB</td>
                <td>Example &amp; Test 1080p.mkv</td>
              </tr>
            </table>
        """.trimIndent()

        val result = XdccHtmlParser.parse(html).single()

        assertEquals("Rizon", result.network)
        assertEquals("#movies", result.channel)
        assertEquals("VxD|Bot", result.bot)
        assertEquals(4242, result.pack)
        assertEquals(1337, result.gets)
        assertEquals("1.4 GB", result.sizeLabel)
        assertEquals("Example & Test 1080p.mkv", result.filename)
    }

    @Test
    fun ignoresIncompleteRowsAndHonorsExplicitLimit() {
        val valid = "<tr><td>Rizon</td><td>#a</td><td>Bot</td><td>#1</td><td>2</td><td>3 MB</td><td>a.mp3</td></tr>"
        val malformed = "<tr><td>not enough</td></tr>"
        val results = XdccHtmlParser.parse(malformed + valid + valid.replace("#1", "#2"), limit = 1)
        assertEquals(1, results.size)
        assertTrue(results.first().filename.endsWith(".mp3"))
    }

    @Test
    fun defaultParsingDoesNotCutResultsAtTwoHundred() {
        val html = buildString {
            repeat(250) { index ->
                append("<tr><td>Rizon</td><td>#a</td><td>Bot</td><td>#${index + 1}</td><td>2</td><td>700 MB</td><td>video-${index + 1}-1080p.mkv</td></tr>")
            }
        }
        val results = XdccHtmlParser.parse(html)
        assertEquals(250, results.size)
        assertEquals(250, results.last().pack)
    }
}
