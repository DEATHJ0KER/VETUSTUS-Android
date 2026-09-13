package mobi.vxd.vetustus.micro.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mobi.vxd.vetustus.micro.R
import mobi.vxd.vetustus.micro.data.DownloadItem
import mobi.vxd.vetustus.micro.data.DownloadRepository
import mobi.vxd.vetustus.micro.data.DownloadState
import mobi.vxd.vetustus.micro.data.LibraryItem
import mobi.vxd.vetustus.micro.data.LibraryRepository
import mobi.vxd.vetustus.micro.service.XdccDownloadService
import mobi.vxd.vetustus.micro.util.formatBytes

@Composable
fun DownloadsScreen(
    repository: DownloadRepository,
    library: LibraryRepository,
    onPlay: (LibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val downloads by repository.items.collectAsStateWithLifecycle()
    val libraryItems by library.items.collectAsStateWithLifecycle()
    if (downloads.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.downloads_empty))
            Text(stringResource(R.string.downloads_empty_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(16.dp)) }
        items(downloads, key = DownloadItem::id) { item ->
            val playable = libraryItems.firstOrNull { it.downloadId == item.id && it.playable }
            DownloadCard(
                item = item,
                onPause = { XdccDownloadService.pause(context, item.id) },
                onCancel = { XdccDownloadService.cancel(context, item.id) },
                onResume = { XdccDownloadService.resume(context, item.id) },
                onRemove = { repository.delete(item.id) },
                onPlay = playable?.let { media -> ({ onPlay(media) }) },
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onPlay: (() -> Unit)?,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                item.filename.ifBlank { item.requestedFilename },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "${item.network} · ${item.bot} · #${item.pack}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            when {
                item.state == DownloadState.DOWNLOADING && item.bytesTotal > 0L ->
                    LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                item.state in setOf(DownloadState.CONNECTING, DownloadState.REQUESTING, DownloadState.WAITING, DownloadState.PUBLISHING, DownloadState.EXTRACTING) ->
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(stateLabelRes(item.state)),
                        color = stateColor(item.state),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (item.state == DownloadState.DOWNLOADING) {
                        val total = item.bytesTotal.takeIf { it > 0L }?.formatBytes() ?: "?"
                        Text(
                            "${item.bytesDone.formatBytes()} / $total · ${item.speedBps.formatBytes()}/s",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else if (item.statusMessage.isNotBlank()) {
                        Text(item.statusMessage, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                    }
                    if (item.queuePosition > 0) {
                        val queue = buildString {
                            append(item.queuePosition)
                            if (item.queueTotal > 0) append("/${item.queueTotal}")
                            if (item.queueEta.isNotBlank()) append(" · ${item.queueEta}")
                        }
                        Text(
                            stringResource(R.string.queue_label, queue),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (item.errorMessage.isNotBlank()) {
                        Text(
                            "${item.errorCode}: ${item.errorMessage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.state == DownloadState.COMPLETE) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                when {
                    !item.state.terminal -> {
                        FilledTonalButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text(stringResource(R.string.pause_action))
                        }
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Cancel, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text(stringResource(R.string.cancel_action))
                        }
                    }
                    item.state.canResume -> {
                        Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text(stringResource(R.string.resume_action))
                        }
                        TextButton(onClick = onRemove) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.remove_action))
                        }
                    }
                    item.state == DownloadState.COMPLETE -> {
                        if (onPlay != null) {
                            Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(5.dp))
                                Text(stringResource(R.string.play_action))
                            }
                        }
                        TextButton(onClick = onRemove) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.remove_row_action))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun stateColor(state: DownloadState) = when (state) {
    DownloadState.COMPLETE -> MaterialTheme.colorScheme.primary
    DownloadState.FAILED, DownloadState.CANCELLED -> MaterialTheme.colorScheme.error
    DownloadState.PAUSED, DownloadState.INTERRUPTED -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

@StringRes
private fun stateLabelRes(state: DownloadState): Int = when (state) {
    DownloadState.QUEUED -> R.string.state_queued
    DownloadState.CONNECTING -> R.string.state_connecting
    DownloadState.REQUESTING -> R.string.state_requesting
    DownloadState.WAITING -> R.string.state_waiting
    DownloadState.DOWNLOADING -> R.string.state_downloading
    DownloadState.PUBLISHING -> R.string.state_publishing
    DownloadState.EXTRACTING -> R.string.state_extracting
    DownloadState.COMPLETE -> R.string.state_complete
    DownloadState.PAUSED -> R.string.state_paused
    DownloadState.INTERRUPTED -> R.string.state_interrupted
    DownloadState.FAILED -> R.string.state_failed
    DownloadState.CANCELLED -> R.string.state_cancelled
}
