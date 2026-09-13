package mobi.vxd.vetustus.micro.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mobi.vxd.vetustus.micro.data.LibraryItem
import mobi.vxd.vetustus.micro.data.LibraryRepository
import mobi.vxd.vetustus.micro.data.MediaKind
import mobi.vxd.vetustus.micro.storage.MediaStorePublisher
import mobi.vxd.vetustus.micro.util.formatBytes

@Composable
fun LibraryScreen(
    repository: LibraryRepository,
    publisher: MediaStorePublisher,
    onPlay: (LibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val allItems by repository.items.collectAsStateWithLifecycle()
    var filterName by rememberSaveable { mutableStateOf("ALL") }
    var deleting by remember { mutableStateOf<LibraryItem?>(null) }
    val items = allItems.filter { filterName == "ALL" || it.kind.name == filterName }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibraryFilter("ALL", "Tutto", filterName) { filterName = it }
            LibraryFilter(MediaKind.VIDEO.name, "Video", filterName) { filterName = it }
            LibraryFilter(MediaKind.AUDIO.name, "Audio", filterName) { filterName = it }
            LibraryFilter(MediaKind.ARCHIVE.name, "Archivi", filterName) { filterName = it }
        }
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
                Text(if (allItems.isEmpty()) "Libreria vuota" else "Nessun file in questa categoria")
                if (allItems.isEmpty()) Text("I media completati appariranno qui.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = LibraryItem::id) { item ->
                    LibraryCard(
                        item = item,
                        onPlay = { onPlay(item) },
                        onOpen = { openExternally(context, item) },
                        onShare = { shareFile(context, item) },
                        onDelete = { deleting = item },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminare il file?") },
            text = { Text("${item.displayName} verrà rimosso anche da Download/VETUSTUS Micro.") },
            confirmButton = {
                Button(onClick = {
                    publisher.delete(Uri.parse(item.contentUri))
                    repository.delete(item.id)
                    deleting = null
                }) { Text("Elimina") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun LibraryFilter(value: String, label: String, selected: String, onSelect: (String) -> Unit) {
    FilterChip(
        selected = value == selected,
        onClick = { onSelect(value) },
        label = { Text(label) },
    )
}

@Composable
private fun LibraryCard(
    item: LibraryItem,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    libraryIcon(item.kind),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.displayName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${item.kind.name.lowercase().replaceFirstChar { it.uppercase() }} · ${item.sizeBytes.formatBytes()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.parentArchive.isNotBlank()) {
                        Text(
                            "Estratto da ${item.parentArchive}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Elimina")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (item.playable) {
                    Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Riproduci")
                    }
                } else {
                    OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text("Apri")
                    }
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Condividi")
                }
            }
        }
    }
}

private fun libraryIcon(kind: MediaKind): ImageVector = when (kind) {
    MediaKind.VIDEO -> Icons.Default.VideoFile
    MediaKind.AUDIO -> Icons.Default.AudioFile
    MediaKind.ARCHIVE -> Icons.Default.Archive
    MediaKind.OTHER -> Icons.Default.InsertDriveFile
}

private fun openExternally(context: android.content.Context, item: LibraryItem) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(item.contentUri), item.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Apri con")) }
}

private fun shareFile(context: android.content.Context, item: LibraryItem) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = item.mimeType
        putExtra(Intent.EXTRA_STREAM, Uri.parse(item.contentUri))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Condividi file")) }
}
