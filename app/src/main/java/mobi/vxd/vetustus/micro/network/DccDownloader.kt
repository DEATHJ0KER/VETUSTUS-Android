package mobi.vxd.vetustus.micro.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.coroutines.coroutineContext

data class DccProgress(
    val bytesDone: Long,
    val bytesTotal: Long,
    val speedBps: Long,
)

data class DccDownloadResult(
    val bytesWritten: Long,
    val resumedFrom: Long,
)

class DccDownloader internal constructor(
    private val addressResolver: (String, Boolean) -> InetAddress = ::resolveSafeDccAddress,
) {
    suspend fun download(
        offer: DccOffer,
        partialFile: File,
        resumeOffset: Long,
        allowPrivateHost: Boolean,
        onSocket: (Socket?) -> Unit = {},
        onProgress: (DccProgress) -> Unit,
    ): DccDownloadResult = withContext(Dispatchers.IO) {
        require(resumeOffset >= 0L) { "Offset DCC non valido" }
        val address = addressResolver(offer.host, allowPrivateHost)
        partialFile.parentFile?.mkdirs()
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.soTimeout = READ_TICK_MS
            socket.connect(InetSocketAddress(address, offer.port), CONNECT_TIMEOUT_MS)
            onSocket(socket)
            val input = socket.getInputStream()
            val ack = DataOutputStream(socket.getOutputStream())
            RandomAccessFile(partialFile, "rw").use { output ->
                val safeOffset = resumeOffset.coerceAtMost(output.length())
                if (safeOffset == 0L) output.setLength(0L)
                output.seek(safeOffset)
                var done = safeOffset
                var lastBytes = done
                var lastTick = System.currentTimeMillis()
                var lastActivity = lastTick
                val buffer = ByteArray(64 * 1024)
                onProgress(DccProgress(done, offer.bytesTotal, 0L))
                while (offer.bytesTotal <= 0L || done < offer.bytesTotal) {
                    coroutineContext.ensureActive()
                    val remaining = if (offer.bytesTotal > 0L) offer.bytesTotal - done else buffer.size.toLong()
                    val wanted = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = try {
                        input.read(buffer, 0, wanted)
                    } catch (_: SocketTimeoutException) {
                        if (System.currentTimeMillis() - lastActivity >= STALL_TIMEOUT_MS) {
                            throw DccException("DCC_STALLED", "Nessun dato DCC ricevuto per 120 secondi")
                        }
                        continue
                    }
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    done += read
                    lastActivity = System.currentTimeMillis()
                    ack.writeInt(done.toInt())
                    ack.flush()
                    val now = System.currentTimeMillis()
                    if (now - lastTick >= 500L) {
                        val elapsed = (now - lastTick).coerceAtLeast(1L)
                        val speed = ((done - lastBytes) * 1000L / elapsed).coerceAtLeast(0L)
                        onProgress(DccProgress(done, offer.bytesTotal, speed))
                        lastTick = now
                        lastBytes = done
                    }
                }
                output.fd.sync()
                val diskBytes = output.length()
                if (offer.bytesTotal > 0L && diskBytes != offer.bytesTotal) {
                    throw DccException(
                        "DCC_SIZE_MISMATCH",
                        "Download incompleto: attesi ${offer.bytesTotal} byte, presenti $diskBytes",
                    )
                }
                if (diskBytes <= 0L) throw DccException("DCC_EMPTY_FILE", "Il bot ha inviato un file vuoto")
                onProgress(DccProgress(diskBytes, offer.bytesTotal.takeIf { it > 0L } ?: diskBytes, 0L))
                DccDownloadResult(diskBytes, safeOffset)
            }
        } finally {
            runCatching { socket.close() }
            onSocket(null)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TICK_MS = 15_000
        const val STALL_TIMEOUT_MS = 120_000L
    }
}

class DccException(val code: String, message: String) : Exception(message)

internal fun resolveSafeDccAddress(host: String, allowPrivateHost: Boolean): InetAddress {
    val addresses = InetAddress.getAllByName(host)
    val allowed = addresses.firstOrNull { address ->
        !address.isAnyLocalAddress &&
            !address.isLoopbackAddress &&
            !address.isLinkLocalAddress &&
            !address.isMulticastAddress &&
            (allowPrivateHost || (!address.isSiteLocalAddress && !address.isAdditionalPrivateRange()))
    }
    return allowed ?: throw DccException(
        "DCC_HOST_BLOCKED",
        "L'offerta DCC punta a un indirizzo locale o non sicuro",
    )
}

private fun InetAddress.isAdditionalPrivateRange(): Boolean {
    val raw = address
    if (raw.size == 4) {
        val first = raw[0].toInt() and 0xff
        val second = raw[1].toInt() and 0xff
        return first == 100 && second in 64..127 // RFC 6598 shared address space
    }
    if (raw.size == 16) {
        val first = raw[0].toInt() and 0xff
        return (first and 0xfe) == 0xfc // IPv6 unique-local fc00::/7
    }
    return false
}
