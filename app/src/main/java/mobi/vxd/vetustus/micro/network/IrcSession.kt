package mobi.vxd.vetustus.micro.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedWriter
import java.io.Closeable
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class IrcSession(
    private val network: IrcNetwork,
    initialNick: String,
    private val onStatus: (String) -> Unit,
    private val onQueue: (QueueStatus) -> Unit,
) : Closeable {
    @Volatile
    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var writer: BufferedWriter? = null
    private var nick = IrcProtocol.safeToken(initialNick).ifBlank { "VxDMobile" }
    @Volatile
    private var closed = false

    suspend fun connectAndRequest(channel: String, bot: String, pack: Int): DccOffer = withContext(Dispatchers.IO) {
        require(pack > 0) { "Numero pacchetto XDCC non valido" }
        open()
        onStatus("Registrazione IRC su ${network.name}")
        send("NICK $nick")
        send("USER vetustusmicro 0 * :VETUSTUS Micro")
        val startedAt = System.currentTimeMillis()
        var requestSent = false
        while (!closed) {
            currentCoroutineContext().ensureActive()
            val line = try {
                readLine()
            } catch (_: SocketTimeoutException) {
                if (!requestSent && System.currentTimeMillis() - startedAt > REGISTRATION_TIMEOUT_MS) {
                    throw IrcException("IRC_REGISTRATION_TIMEOUT", "Il server IRC non ha completato la registrazione")
                }
                continue
            } ?: throw IrcException("IRC_CONNECTION_CLOSED", "Connessione IRC chiusa")
            val message = IrcProtocol.parseLine(line)
            if (handlePing(message)) continue
            when (message.command) {
                "001" -> if (!requestSent) {
                    if (channel.startsWith('#') || channel.startsWith('&')) {
                        onStatus("Accesso a $channel")
                        send("JOIN ${IrcProtocol.safeToken(channel)}")
                    }
                    onStatus("Richiesta XDCC #$pack a $bot")
                    send("PRIVMSG ${IrcProtocol.safeToken(bot)} :XDCC SEND #$pack")
                    requestSent = true
                }
                "432", "433" -> if (!requestSent) {
                    nick = (nick.take(18) + (1000..9999).random()).take(24)
                    send("NICK $nick")
                }
                "401" -> {
                    val target = message.params.getOrNull(1).orEmpty()
                    if (IrcProtocol.ircEquals(target, bot)) {
                        throw IrcException("XDCC_BOT_OFFLINE", "Il bot $bot non è online")
                    }
                }
                "404" -> throw IrcException("IRC_CANNOT_SEND", message.trailing.ifBlank { "Il server non consente la richiesta XDCC" })
                "ERROR" -> throw IrcException("IRC_SERVER_ERROR", message.trailing.ifBlank { "Errore del server IRC" })
            }
            IrcProtocol.parseQueueStatus(message, bot)?.let { status ->
                onStatus(status.message)
                onQueue(status)
            }
            IrcProtocol.parseDccOffer(message, bot)?.let { offer ->
                onStatus("Offerta DCC ricevuta: ${offer.filename}")
                return@withContext offer
            }
        }
        throw IrcException("IRC_SESSION_CLOSED", "Sessione IRC chiusa")
    }

    suspend fun negotiateResume(offer: DccOffer, offset: Long): Long = withContext(Dispatchers.IO) {
        if (offset <= 0L || (offer.bytesTotal > 0L && offset >= offer.bytesTotal)) return@withContext 0L
        val filename = offer.filename.replace("\"", "")
        onStatus("Negoziazione resume da $offset byte")
        send("PRIVMSG ${IrcProtocol.safeToken(offer.nick)} :\u0001DCC RESUME \"$filename\" ${offer.port} $offset\u0001")
        val deadline = System.currentTimeMillis() + RESUME_TIMEOUT_MS
        val activeSocket = socket
        val previousTimeout = activeSocket?.soTimeout ?: READ_TICK_MS
        activeSocket?.soTimeout = RESUME_READ_TICK_MS
        try {
            while (!closed && System.currentTimeMillis() < deadline) {
                currentCoroutineContext().ensureActive()
                val line = try {
                    readLine()
                } catch (_: SocketTimeoutException) {
                    continue
                } ?: return@withContext 0L
                val message = IrcProtocol.parseLine(line)
                if (handlePing(message)) continue
                val accepted = IrcProtocol.parseDccAccept(message, offer.nick, offer)
                if (accepted != null) {
                    onStatus("Resume DCC accettato")
                    return@withContext accepted.takeIf { it == offset } ?: 0L
                }
                IrcProtocol.parseQueueStatus(message, offer.nick)?.let(onQueue)
            }
        } finally {
            runCatching { activeSocket?.soTimeout = previousTimeout }
        }
        onStatus("Resume non accettato: ripartenza da zero")
        0L
    }

    suspend fun keepAlive(bot: String) = withContext(Dispatchers.IO) {
        while (!closed) {
            currentCoroutineContext().ensureActive()
            val line = try {
                readLine()
            } catch (_: SocketTimeoutException) {
                continue
            } ?: return@withContext
            val message = IrcProtocol.parseLine(line)
            if (handlePing(message)) continue
            IrcProtocol.parseQueueStatus(message, bot)?.let(onQueue)
        }
    }

    private fun open() {
        check(!closed) { "Sessione IRC già chiusa" }
        val failures = mutableListOf<Pair<IrcEndpoint, Throwable>>()

        network.endpoints.forEachIndexed { index, endpoint ->
            if (index > 0) {
                onStatus("Fallback IRC compatibile su ${endpoint.address}:${endpoint.port}")
            } else {
                onStatus("Connessione a ${endpoint.address}:${endpoint.port}${if (endpoint.tls) " TLS" else ""}")
            }
            try {
                val connected = openEndpoint(endpoint)
                connected.keepAlive = true
                connected.tcpNoDelay = true
                connected.soTimeout = READ_TICK_MS
                socket = connected
                input = BufferedInputStream(connected.getInputStream())
                writer = BufferedWriter(OutputStreamWriter(connected.getOutputStream(), Charsets.UTF_8))
                if (index > 0) onStatus("Connesso tramite fallback IRC non cifrato")
                return
            } catch (error: Throwable) {
                failures += endpoint to error
                val hasFallback = index < network.endpoints.lastIndex
                if (hasFallback) {
                    val reason = when (error) {
                        is SSLHandshakeException, is SSLPeerUnverifiedException -> "Certificato TLS non compatibile"
                        else -> "Endpoint IRC non disponibile"
                    }
                    onStatus("$reason · provo il fallback configurato")
                }
            }
        }

        val detail = failures.joinToString(" · ") { (endpoint, error) ->
            "${endpoint.address}:${endpoint.port}${if (endpoint.tls) "/TLS" else ""}: ${error.message ?: error.javaClass.simpleName}"
        }.take(600)
        val tlsFailure = failures.any { (_, error) -> error is SSLHandshakeException || error is SSLPeerUnverifiedException }
        throw IrcException(
            if (tlsFailure) "IRC_TLS_OR_CONNECT_FAILED" else "IRC_CONNECT_FAILED",
            if (detail.isBlank()) "Nessun endpoint IRC disponibile" else "Nessun endpoint IRC disponibile · $detail",
        )
    }

    private fun openEndpoint(endpoint: IrcEndpoint): Socket {
        if (!endpoint.tls) {
            return Socket().apply {
                connect(InetSocketAddress(endpoint.address, endpoint.port), CONNECT_TIMEOUT_MS)
            }
        }

        val plain = Socket()
        try {
            plain.connect(InetSocketAddress(endpoint.address, endpoint.port), CONNECT_TIMEOUT_MS)
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val ssl = factory.createSocket(plain, endpoint.address, endpoint.port, true) as SSLSocket
            ssl.sslParameters = ssl.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            ssl.soTimeout = CONNECT_TIMEOUT_MS
            ssl.startHandshake()
            return ssl
        } catch (error: Throwable) {
            runCatching { plain.close() }
            throw error
        }
    }

    @Synchronized
    private fun send(line: String) {
        if (closed) return
        require(!line.contains('\r') && !line.contains('\n') && !line.contains('\u0000')) { "Riga IRC non valida" }
        val clean = line.take(MAX_OUTGOING_LINE)
        writer?.apply {
            write(clean)
            write("\r\n")
            flush()
        } ?: throw IrcException("IRC_NOT_CONNECTED", "Socket IRC non connesso")
    }

    private fun handlePing(message: IrcMessage): Boolean {
        if (message.command != "PING") return false
        val token = message.trailing.ifBlank { message.params.firstOrNull().orEmpty() }
        send("PONG :${token.replace(Regex("[\\r\\n\\u0000]"), "")}")
        return true
    }

    private fun readLine(): String? {
        val stream = input ?: return null
        val output = ByteArray(MAX_INCOMING_LINE)
        var count = 0
        while (count < output.size) {
            val value = stream.read()
            if (value < 0) return if (count == 0) null else output.decodeToString(0, count).trimEnd('\r')
            if (value == '\n'.code) return output.decodeToString(0, count).trimEnd('\r')
            output[count++] = value.toByte()
        }
        throw IrcException("IRC_LINE_TOO_LONG", "Il server ha inviato una riga IRC eccessiva")
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching {
            writer?.apply {
                write("QUIT :VETUSTUS Micro\r\n")
                flush()
            }
        }
        runCatching { socket?.close() }
        socket = null
        input = null
        writer = null
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TICK_MS = 15_000
        const val RESUME_READ_TICK_MS = 1_000
        const val REGISTRATION_TIMEOUT_MS = 45_000L
        const val RESUME_TIMEOUT_MS = 5_000L
        const val MAX_INCOMING_LINE = 64 * 1024
        const val MAX_OUTGOING_LINE = 510
    }
}

class IrcException(val code: String, message: String) : Exception(message)
