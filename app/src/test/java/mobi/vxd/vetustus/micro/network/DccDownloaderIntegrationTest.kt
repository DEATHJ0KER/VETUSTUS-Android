package mobi.vxd.vetustus.micro.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DccDownloaderIntegrationTest {
    @Test
    fun resumesARealSocketTransferAndSendsCumulativeAcks() {
        val payload = ByteArray(180_000) { index -> (index % 251).toByte() }
        val resumeAt = 8_192
        val partial = Files.createTempFile("vetustus-dcc-", ".part").toFile().apply {
            writeBytes(payload.copyOfRange(0, resumeAt))
        }
        val server = ServerSocket(0)
        val accepted = AtomicReference<java.net.Socket?>()
        val executor = Executors.newSingleThreadExecutor()
        val acks = executor.submit<List<Long>> {
            server.accept().use { socket ->
                accepted.set(socket)
                val output = socket.getOutputStream()
                val input = DataInputStream(socket.getInputStream())
                val receivedAcks = mutableListOf<Long>()
                var offset = resumeAt
                while (offset < payload.size) {
                    val count = minOf(16_384, payload.size - offset)
                    output.write(payload, offset, count)
                    output.flush()
                    offset += count
                    do {
                        receivedAcks += input.readInt().toLong() and 0xffff_ffffL
                    } while (receivedAcks.last() < offset.toLong())
                }
                receivedAcks
            }
        }

        try {
            val result = runBlocking {
                DccDownloader { _, _ -> InetAddress.getLoopbackAddress() }.download(
                    offer = DccOffer("Bot", "payload.bin", "test.invalid", server.localPort, payload.size.toLong()),
                    partialFile = partial,
                    resumeOffset = resumeAt.toLong(),
                    allowPrivateHost = false,
                    onProgress = {},
                )
            }

            val receivedAcks = acks.get(5, TimeUnit.SECONDS)
            assertEquals(payload.size.toLong(), result.bytesWritten)
            assertEquals(resumeAt.toLong(), result.resumedFrom)
            assertEquals(payload.size.toLong(), receivedAcks.last())
            assertArrayEquals(payload, partial.readBytes())
        } finally {
            runCatching { accepted.get()?.close() }
            server.close()
            executor.shutdownNow()
            partial.delete()
        }
    }

    @Test
    fun blocksLoopbackDccHostsByDefault() {
        val error = assertThrows(DccException::class.java) {
            resolveSafeDccAddress("127.0.0.1", allowPrivateHost = false)
        }
        assertEquals("DCC_HOST_BLOCKED", error.code)
    }

    @Test
    fun privateLanHostsRequireExplicitOptIn() {
        assertThrows(DccException::class.java) {
            resolveSafeDccAddress("192.168.10.20", allowPrivateHost = false)
        }
        assertEquals("192.168.10.20", resolveSafeDccAddress("192.168.10.20", allowPrivateHost = true).hostAddress)
    }
}
