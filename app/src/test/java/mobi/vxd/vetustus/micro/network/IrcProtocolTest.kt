package mobi.vxd.vetustus.micro.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IrcProtocolTest {
    @Test
    fun parsesQuotedDccSendAndIntegerIp() {
        val line = ":VxD|Bot!bot@example PRIVMSG VxDMobile :\u0001DCC SEND \"Album Demo.flac\" 3232235777 50000 123456789\u0001"
        val offer = IrcProtocol.parseDccOffer(IrcProtocol.parseLine(line), "vxd\\bot")
            ?: error("Offer not parsed")

        assertEquals("Album Demo.flac", offer.filename)
        assertEquals("192.168.1.1", offer.host)
        assertEquals(50000, offer.port)
        assertEquals(123456789L, offer.bytesTotal)
    }

    @Test
    fun rejectsOfferFromUnexpectedNick() {
        val line = ":OtherBot!bot@example PRIVMSG VxDMobile :\u0001DCC SEND file.zip 1 50000 42\u0001"
        assertNull(IrcProtocol.parseDccOffer(IrcProtocol.parseLine(line), "ExpectedBot"))
    }

    @Test
    fun parsesResumeAccept() {
        val offer = DccOffer("PackBot", "big file.mkv", "203.0.113.8", 50123, 9_000L)
        val line = ":PackBot!x@y PRIVMSG me :\u0001DCC ACCEPT \"big file.mkv\" 50123 4096\u0001"
        assertEquals(4096L, IrcProtocol.parseDccAccept(IrcProtocol.parseLine(line), "PackBot", offer))
    }

    @Test
    fun parsesQueuePosition() {
        val line = ":PackBot!x@y NOTICE me :You are queued, position 3 of 12, ETA: 8 minutes"
        val queue = IrcProtocol.parseQueueStatus(IrcProtocol.parseLine(line), "PackBot")
            ?: error("Queue not parsed")
        assertEquals(3, queue.position)
        assertEquals(12, queue.total)
        assertTrue(queue.eta.contains("8 minutes"))
    }

    @Test
    fun removesPathsAndControlCharactersFromFilenames() {
        assertEquals("evil_name_.mkv", IrcProtocol.sanitizeFilename("../../evil<name>.mkv"))
    }
}
