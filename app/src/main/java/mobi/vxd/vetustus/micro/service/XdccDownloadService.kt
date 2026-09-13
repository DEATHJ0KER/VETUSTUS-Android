package mobi.vxd.vetustus.micro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mobi.vxd.vetustus.micro.MainActivity
import mobi.vxd.vetustus.micro.R
import mobi.vxd.vetustus.micro.VetustusMicroApp
import mobi.vxd.vetustus.micro.data.DownloadItem
import mobi.vxd.vetustus.micro.data.DownloadState
import mobi.vxd.vetustus.micro.data.XdccSearchResult
import mobi.vxd.vetustus.micro.network.DccDownloader
import mobi.vxd.vetustus.micro.network.DccException
import mobi.vxd.vetustus.micro.network.IrcException
import mobi.vxd.vetustus.micro.network.IrcSession
import mobi.vxd.vetustus.micro.storage.toLibraryItem
import mobi.vxd.vetustus.micro.util.FileTypes
import mobi.vxd.vetustus.micro.util.formatBytes
import java.io.File
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class XdccDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transferSlot = Semaphore(1)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val sessions = ConcurrentHashMap<String, IrcSession>()
    private val dccSockets = ConcurrentHashMap<String, Socket>()
    private val requestedStops = ConcurrentHashMap<String, DownloadState>()
    private var lastNotificationAt = 0L

    private val app get() = application as VetustusMicroApp
    private val container get() = app.container

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()
        val id = intent?.getStringExtra(EXTRA_DOWNLOAD_ID).orEmpty()
        when (intent?.action) {
            ACTION_ENQUEUE, ACTION_RESUME, ACTION_RETRY -> if (id.isNotBlank()) launchDownload(id)
            ACTION_PAUSE -> if (id.isNotBlank()) stopDownload(id, DownloadState.PAUSED)
            ACTION_CANCEL -> if (id.isNotBlank()) stopDownload(id, DownloadState.CANCELLED)
            else -> if (jobs.isEmpty()) stopNow()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun launchDownload(id: String) {
        if (jobs[id]?.isActive == true) return
        requestedStops.remove(id)
        container.downloads.state(id, DownloadState.QUEUED, "In coda locale")
        lateinit var job: Job
        job = serviceScope.launch(start = CoroutineStart.LAZY) {
            transferSlot.withPermit { runDownload(id) }
        }
        jobs[id] = job
        job.invokeOnCompletion {
            jobs.remove(id, job)
            requestedStops.remove(id)
            updateNotification(force = true)
            if (jobs.values.none { it.isActive }) stopNow()
        }
        job.start()
        updateNotification(force = true)
    }

    private suspend fun runDownload(id: String) {
        var session: IrcSession? = null
        var keepAliveJob: Job? = null
        var wakeLock: PowerManager.WakeLock? = null
        try {
            val initial = container.downloads.find(id) ?: return
            val network = container.networkDirectory.resolve(initial.network)
                ?: throw IrcException("XDCC_NETWORK_UNKNOWN", "Rete XDCC non riconosciuta: ${initial.network}")
            val settings = container.settings.settings.value
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VetustusMicro:XdccDownload:$id")
                .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }

            container.downloads.state(id, DownloadState.CONNECTING, "Connessione IRC a ${network.name}")
            updateNotification(force = true)
            val activeSession = IrcSession(
                network = network,
                initialNick = settings.nick,
                onStatus = { message ->
                    if (message.startsWith("Richiesta XDCC")) {
                        container.downloads.state(id, DownloadState.REQUESTING, message)
                    } else {
                        container.downloads.message(id, message)
                    }
                    updateNotification()
                },
                onQueue = { queue ->
                    container.downloads.queue(id, queue.position, queue.total, queue.eta, queue.message)
                    updateNotification(force = true)
                },
            )
            session = activeSession
            sessions[id] = activeSession
            val offer = activeSession.connectAndRequest(initial.channel, initial.bot, initial.pack)
            container.downloads.offer(id, offer.filename, offer.bytesTotal)
            val partial = File(initial.partialPath)
            if (offer.bytesTotal > 0L && partial.length() > offer.bytesTotal) {
                partial.delete()
            }
            var offset = partial.takeIf(File::isFile)?.length() ?: 0L
            if (offer.bytesTotal > 0L && offset == offer.bytesTotal) {
                container.downloads.progress(id, offset, offer.bytesTotal, 0L)
            } else {
                offset = activeSession.negotiateResume(offer, offset)
                keepAliveJob = serviceScope.launch { runCatching { activeSession.keepAlive(initial.bot) } }
                val result = DccDownloader().download(
                    offer = offer,
                    partialFile = partial,
                    resumeOffset = offset,
                    allowPrivateHost = settings.allowPrivateDccHosts,
                    onSocket = { socket ->
                        if (socket == null) dccSockets.remove(id) else dccSockets[id] = socket
                    },
                    onProgress = { progress ->
                        container.downloads.progress(id, progress.bytesDone, progress.bytesTotal, progress.speedBps)
                        updateNotification()
                    },
                )
                check(result.bytesWritten == partial.length()) { "Verifica del file parziale non riuscita" }
            }

            val archiveKind = FileTypes.archiveKind(offer.filename)
            if (archiveKind != null && settings.autoExtractArchives) {
                val archiveBytes = partial.length()
                container.downloads.state(id, DownloadState.EXTRACTING, "Analisi archivio · estrazione solo audio/video")
                updateNotification(force = true)

                var extracted = emptyList<mobi.vxd.vetustus.micro.data.LibraryItem>()
                var archiveWarning: Pair<String, String>? = null
                try {
                    extracted = container.archiveManager.extractMedia(id, partial, offer.filename)
                    if (extracted.isEmpty()) {
                        archiveWarning = "ARCHIVE_NO_MEDIA" to "Nessun brano o video riconosciuto nell'archivio; archivio originale conservato"
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    archiveWarning = "ARCHIVE_EXTRACTION_FAILED" to (error.message ?: "Estrazione archivio non riuscita")
                }

                val keepOriginal = extracted.isEmpty() || !settings.deleteArchiveAfterExtract
                var finalUri = ""
                var finalMime = FileTypes.mimeType(offer.filename)
                var finalName = offer.filename
                if (keepOriginal) {
                    container.downloads.state(id, DownloadState.PUBLISHING, "Conservazione archivio originale")
                    updateNotification(force = true)
                    try {
                        val published = container.publisher.publishFile(partial, offer.filename)
                        finalUri = published.uri.toString()
                        finalMime = published.mimeType
                        finalName = published.displayName
                        if (extracted.isEmpty()) {
                            container.library.insert(published.toLibraryItem(id))
                        }
                    } catch (error: Throwable) {
                        if (extracted.isNotEmpty()) container.archiveManager.rollback(extracted)
                        throw error
                    }
                }
                partial.delete()
                container.downloads.completed(
                    id = id,
                    contentUri = finalUri,
                    mimeType = finalMime,
                    filename = finalName,
                    bytes = archiveBytes,
                )
                archiveWarning?.let { (code, message) ->
                    container.downloads.state(
                        id,
                        DownloadState.COMPLETE,
                        "Download completo · archivio conservato",
                        code,
                        message,
                    )
                }
            } else {
                container.downloads.state(id, DownloadState.PUBLISHING, "Salvataggio in Download/VETUSTUS Micro")
                updateNotification(force = true)
                val published = container.publisher.publishFile(partial, offer.filename)
                val libraryItem = published.toLibraryItem(id)
                try {
                    container.library.insert(libraryItem)
                    container.downloads.completed(
                        id = id,
                        contentUri = published.uri.toString(),
                        mimeType = published.mimeType,
                        filename = published.displayName,
                        bytes = published.sizeBytes,
                    )
                } catch (error: Throwable) {
                    container.library.delete(libraryItem.id)
                    container.publisher.delete(published.uri)
                    throw error
                }
                partial.delete()
            }
            updateNotification(force = true)
        } catch (cancelled: CancellationException) {
            val requested = requestedStops.remove(id) ?: DownloadState.INTERRUPTED
            container.downloads.state(
                id,
                requested,
                if (requested == DownloadState.PAUSED) "In pausa · il file parziale è conservato" else "Download annullato",
            )
        } catch (error: Throwable) {
            val requested = requestedStops.remove(id)
            if (requested != null) {
                container.downloads.state(
                    id,
                    requested,
                    if (requested == DownloadState.PAUSED) "In pausa · il file parziale è conservato" else "Download annullato",
                )
            } else {
                val code = when (error) {
                    is IrcException -> error.code
                    is DccException -> error.code
                    else -> error.javaClass.simpleName.uppercase().ifBlank { "DOWNLOAD_FAILED" }
                }
                val partialBytes = container.downloads.find(id)?.partialPath?.let(::File)?.length() ?: 0L
                val existing = container.downloads.find(id)
                if (existing != null && partialBytes > existing.bytesDone) {
                    container.downloads.progress(id, partialBytes, existing.bytesTotal, 0L)
                }
                container.downloads.state(
                    id,
                    DownloadState.FAILED,
                    if (partialBytes > 0L) "Interrotto · ripresa disponibile" else "Download non riuscito",
                    code,
                    error.message ?: "Errore sconosciuto",
                )
            }
            updateNotification(force = true)
        } finally {
            keepAliveJob?.cancel()
            dccSockets.remove(id)?.let { runCatching { it.close() } }
            sessions.remove(id)?.close()
            session?.close()
            wakeLock?.takeIf { it.isHeld }?.release()
        }
    }

    private fun stopDownload(id: String, state: DownloadState) {
        requestedStops[id] = state
        sessions.remove(id)?.close()
        dccSockets.remove(id)?.let { runCatching { it.close() } }
        jobs[id]?.cancel(CancellationException(state.name))
        val label = if (state == DownloadState.PAUSED) "In pausa · il file parziale è conservato" else "Download annullato"
        container.downloads.state(id, state, label)
        updateNotification(force = true)
        if (jobs[id]?.isActive != true && jobs.values.none { it.isActive }) stopNow()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        jobs.keys.forEach { id ->
            requestedStops[id] = DownloadState.INTERRUPTED
            sessions.remove(id)?.close()
            dccSockets.remove(id)?.let { runCatching { it.close() } }
            jobs[id]?.cancel(CancellationException("FGS_TIMEOUT"))
            container.downloads.state(id, DownloadState.INTERRUPTED, "Limite Android raggiunto · premi Riprendi")
        }
        stopNow()
    }

    override fun onDestroy() {
        jobs.keys.forEach { id ->
            val item = container.downloads.find(id)
            if (item != null && !item.state.terminal) {
                container.downloads.state(id, DownloadState.INTERRUPTED, "Servizio arrestato · premi Riprendi")
            }
        }
        sessions.values.forEach(IrcSession::close)
        dccSockets.values.forEach { socket -> runCatching { socket.close() } }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureForeground() {
        val notification = buildNotification(activeDownload())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotificationAt < NOTIFICATION_THROTTLE_MS) return
        lastNotificationAt = now
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(activeDownload()))
    }

    private fun activeDownload(): DownloadItem? {
        val items = container.downloads.items.value
        val priority = listOf(
            DownloadState.DOWNLOADING,
            DownloadState.PUBLISHING,
            DownloadState.EXTRACTING,
            DownloadState.REQUESTING,
            DownloadState.CONNECTING,
            DownloadState.WAITING,
            DownloadState.QUEUED,
        )
        return priority.firstNotNullOfOrNull { state -> items.firstOrNull { it.state == state } }
    }

    private fun buildNotification(item: DownloadItem?): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = item?.filename?.ifBlank { item.requestedFilename } ?: "VETUSTUS Micro"
        val text = item?.let { current ->
            when (current.state) {
                DownloadState.DOWNLOADING -> "${current.bytesDone.formatBytes()} / ${current.bytesTotal.formatBytes()} · ${current.speedBps.formatBytes()}/s"
                else -> current.statusMessage.ifBlank { current.state.name }
            }
        } ?: "Gestione trasferimenti XDCC"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(item != null)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (item != null && item.bytesTotal > 0L && item.state == DownloadState.DOWNLOADING) {
            val progress = ((item.bytesDone * 100L) / item.bytesTotal).toInt().coerceIn(0, 100)
            builder.setProgress(100, progress, false)
        } else if (item != null && item.state in setOf(DownloadState.CONNECTING, DownloadState.REQUESTING, DownloadState.WAITING)) {
            builder.setProgress(0, 0, true)
        }
        if (item != null) {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pausa",
                servicePendingIntent(ACTION_PAUSE, item.id, 10),
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Annulla",
                servicePendingIntent(ACTION_CANCEL, item.id, 20),
            )
        }
        return builder.build()
    }

    private fun servicePendingIntent(action: String, id: String, salt: Int): PendingIntent = PendingIntent.getService(
        this,
        id.hashCode() xor salt,
        Intent(this, XdccDownloadService::class.java).setAction(action).putExtra(EXTRA_DOWNLOAD_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.download_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.download_channel_description) }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun stopNow() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_ID = "vetustus_xdcc_downloads"
        private const val NOTIFICATION_ID = 8001
        private const val EXTRA_DOWNLOAD_ID = "download_id"
        private const val ACTION_ENQUEUE = "mobi.vxd.vetustus.micro.action.ENQUEUE"
        private const val ACTION_RESUME = "mobi.vxd.vetustus.micro.action.RESUME"
        private const val ACTION_RETRY = "mobi.vxd.vetustus.micro.action.RETRY"
        private const val ACTION_PAUSE = "mobi.vxd.vetustus.micro.action.PAUSE"
        private const val ACTION_CANCEL = "mobi.vxd.vetustus.micro.action.CANCEL"
        private const val NOTIFICATION_THROTTLE_MS = 1_000L
        private const val WAKE_LOCK_TIMEOUT_MS = 6L * 60L * 60L * 1_000L

        fun enqueue(context: Context, result: XdccSearchResult): String {
            val app = context.applicationContext as VetustusMicroApp
            val item = app.container.downloads.create(result)
            start(context, ACTION_ENQUEUE, item.id)
            return item.id
        }

        fun resume(context: Context, id: String) = start(context, ACTION_RESUME, id)
        fun retry(context: Context, id: String) = start(context, ACTION_RETRY, id)
        fun pause(context: Context, id: String) = start(context, ACTION_PAUSE, id)
        fun cancel(context: Context, id: String) = start(context, ACTION_CANCEL, id)

        private fun start(context: Context, action: String, id: String) {
            val intent = Intent(context, XdccDownloadService::class.java)
                .setAction(action)
                .putExtra(EXTRA_DOWNLOAD_ID, id)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
