package mobi.vxd.vetustus.micro.ui.screens

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mobi.vxd.vetustus.micro.data.XdccSearchResult
import mobi.vxd.vetustus.micro.network.NetworkDirectory
import mobi.vxd.vetustus.micro.network.XdccSearchClient
import mobi.vxd.vetustus.micro.service.XdccDownloadService
import mobi.vxd.vetustus.micro.util.FileTypes

@Composable
fun SearchScreen(
    searchClient: XdccSearchClient,
    networkDirectory: NetworkDirectory,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<XdccSearchResult>>(emptyList()) }

    val executeSearch = {
        val term = query.trim()
        if (term.length < 2) {
            error = "Inserisci almeno 2 caratteri"
        } else if (!loading) {
            scope.launch {
                loading = true
                error = ""
                searched = true
                runCatching { searchClient.search(term) }
                    .onSuccess { results = it }
                    .onFailure { failure ->
                        results = emptyList()
                        error = failure.message ?: "Ricerca XDCC non riuscita"
                    }
                loading = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Trova il file. Al resto pensa VETUSTUS.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "IRC rimane invisibile: la ricerca restituisce bot e pacchetto, il motore li gestisce dietro le quinte.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(120) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Film, album o nome file") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { executeSearch() }),
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = executeSearch,
            enabled = !loading && query.trim().length >= 2,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (loading) "Ricerca…" else "Cerca su XDCC")
        }
        if (error.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(14.dp))
        when {
            loading && results.isEmpty() -> SearchPlaceholder()
            searched && results.isEmpty() && error.isBlank() -> EmptySearch()
            results.isNotEmpty() -> {
                Text(
                    "${results.size} risultati",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    itemsIndexed(
                        results,
                        key = { index, result -> "$index|${result.network}|${result.bot}|${result.pack}|${result.filename}" },
                    ) { _, result ->
                        SearchResultCard(
                            result = result,
                            networkSupported = networkDirectory.supports(result.network),
                            onDownload = {
                                XdccDownloadService.enqueue(context, result)
                                onOpenDownloads()
                            },
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
            else -> SearchIntro()
        }
    }
}

@Composable
private fun SearchResultCard(
    result: XdccSearchResult,
    networkSupported: Boolean,
    onDownload: () -> Unit,
) {
    val formatSupported = FileTypes.isAlphaSupported(result.filename)
    val supported = formatSupported && networkSupported
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                result.filename,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MetaPill(result.network)
                MetaPill(result.channel)
                MetaPill("#${result.pack}")
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${result.bot} · ${result.sizeLabel}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        when {
                            !networkSupported -> "Rete non configurata in questa Alpha"
                            formatSupported -> "Formato Alpha supportato"
                            else -> "Formato fuori perimetro Alpha"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (supported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onDownload, enabled = supported) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Scarica")
                }
            }
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchIntro() {
    Column(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("MP4 · MKV · MP3 · FLAC · ZIP", fontWeight = FontWeight.Bold)
        Text("Prima Alpha", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchPlaceholder() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptySearch() {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Nessun risultato")
        Text("Prova un titolo meno specifico.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
