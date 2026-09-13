package mobi.vxd.vetustus.micro.ui.screens

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mobi.vxd.vetustus.micro.R
import mobi.vxd.vetustus.micro.data.MediaKind
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
    val minCharsText = stringResource(R.string.search_min_chars)
    val searchFailedText = stringResource(R.string.search_failed)
    var query by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<XdccSearchResult>>(emptyList()) }
    var typeFilter by rememberSaveable { mutableStateOf(ResultTypeFilter.ALL.name) }
    var sizeFilter by rememberSaveable { mutableStateOf(SizeFilter.ALL.name) }
    var qualityFilter by rememberSaveable { mutableStateOf(QualityFilter.ALL.name) }
    var sortMode by rememberSaveable { mutableStateOf(SortMode.SOURCE.name) }

    val visibleResults = remember(results, typeFilter, sizeFilter, qualityFilter, sortMode) {
        val selectedType = ResultTypeFilter.valueOf(typeFilter)
        val selectedSize = SizeFilter.valueOf(sizeFilter)
        val selectedQuality = QualityFilter.valueOf(qualityFilter)
        val selectedSort = SortMode.valueOf(sortMode)
        val filtered = results.asSequence()
            .filter { matchesType(it, selectedType) }
            .filter { matchesSize(it, selectedSize) }
            .filter { matchesQuality(it, selectedQuality) }
            .toList()
        when (selectedSort) {
            SortMode.SOURCE -> filtered
            SortMode.SMALL_FIRST -> filtered.sortedWith(compareBy(nullsLast()) { resultSizeBytes(it) })
            SortMode.LARGE_FIRST -> filtered.sortedWith(compareByDescending<XdccSearchResult> { resultSizeBytes(it) ?: Long.MIN_VALUE })
        }
    }

    val executeSearch = {
        val term = query.trim()
        if (term.length < 2) {
            error = minCharsText
        } else if (!loading) {
            scope.launch {
                loading = true
                error = ""
                searched = true
                runCatching { searchClient.search(term) }
                    .onSuccess { results = it }
                    .onFailure { failure ->
                        results = emptyList()
                        error = failure.message ?: searchFailedText
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
            stringResource(R.string.search_tagline),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.search_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(120) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.search_field_label)) },
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
            Text(stringResource(if (loading) R.string.search_loading else R.string.search_button))
        }
        if (error.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (results.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FilterRow(
                options = ResultTypeFilter.entries.map { it.name to it.labelRes },
                selected = typeFilter,
                onSelect = { typeFilter = it },
            )
            FilterRow(
                options = SizeFilter.entries.map { it.name to it.labelRes },
                selected = sizeFilter,
                onSelect = { sizeFilter = it },
            )
            FilterRow(
                options = QualityFilter.entries.map { it.name to it.labelRes },
                selected = qualityFilter,
                onSelect = { qualityFilter = it },
            )
            FilterRow(
                options = SortMode.entries.map { it.name to it.labelRes },
                selected = sortMode,
                onSelect = { sortMode = it },
            )
            Text(
                stringResource(R.string.mobile_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        when {
            loading && results.isEmpty() -> SearchPlaceholder()
            searched && results.isEmpty() && error.isBlank() -> EmptySearch()
            results.isNotEmpty() -> {
                Text(
                    if (visibleResults.size == results.size) {
                        stringResource(R.string.results_count, results.size)
                    } else {
                        stringResource(R.string.results_filtered, visibleResults.size, results.size)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(8.dp))
                if (visibleResults.isEmpty()) {
                    Text(
                        stringResource(R.string.no_filter_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        itemsIndexed(
                            visibleResults,
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
            }
            else -> SearchIntro()
        }
    }
}

@Composable
private fun FilterRow(
    options: List<Pair<String, Int>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, labelRes) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

@Composable
private fun SearchResultCard(
    result: XdccSearchResult,
    networkSupported: Boolean,
    onDownload: () -> Unit,
) {
    val extension = FileTypes.extension(result.filename).uppercase().ifBlank { "FILE" }
    val internalPlayback = FileTypes.canTryInternalPlayback(result.filename)
    val archive = FileTypes.isArchive(result.filename)
    val quality = detectQuality(result.filename)
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
                MetaPill(extension)
                quality?.let { MetaPill(stringResource(it.labelRes)) }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${result.bot} · ${result.sizeLabel}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        stringResource(
                            when {
                                !networkSupported -> R.string.network_not_configured
                                internalPlayback -> R.string.download_internal_player
                                archive -> R.string.download_archive_managed
                                else -> R.string.download_external_open
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (networkSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onDownload, enabled = networkSupported) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.download_action))
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
        Text(stringResource(R.string.search_intro_title), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.search_intro_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text(stringResource(R.string.no_results))
        Text(stringResource(R.string.try_less_specific), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private enum class ResultTypeFilter(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    VIDEO(R.string.filter_video),
    AUDIO(R.string.filter_audio),
    ARCHIVE(R.string.filter_archives),
    OTHER(R.string.filter_other),
}

private enum class SizeFilter(@StringRes val labelRes: Int) {
    ALL(R.string.size_any),
    MOBILE(R.string.size_mobile),
    UNDER_1_GB(R.string.size_under_1gb),
    ONE_TO_FIVE_GB(R.string.size_1_5gb),
    FIVE_TO_TWENTY_GB(R.string.size_5_20gb),
    OVER_20_GB(R.string.size_20plus_gb),
}

private enum class QualityFilter(@StringRes val labelRes: Int) {
    ALL(R.string.quality_any),
    SD(R.string.quality_sd),
    P720(R.string.quality_720p),
    P1080(R.string.quality_1080p),
    K2(R.string.quality_2k),
    K4(R.string.quality_4k),
    K5(R.string.quality_5k),
    K8(R.string.quality_8k),
}

private enum class SortMode(@StringRes val labelRes: Int) {
    SOURCE(R.string.sort_source),
    SMALL_FIRST(R.string.sort_small_first),
    LARGE_FIRST(R.string.sort_large_first),
}

private fun matchesType(result: XdccSearchResult, filter: ResultTypeFilter): Boolean {
    if (filter == ResultTypeFilter.ALL) return true
    return when (FileTypes.kind(result.filename)) {
        MediaKind.VIDEO -> filter == ResultTypeFilter.VIDEO
        MediaKind.AUDIO -> filter == ResultTypeFilter.AUDIO
        MediaKind.ARCHIVE -> filter == ResultTypeFilter.ARCHIVE
        MediaKind.OTHER -> filter == ResultTypeFilter.OTHER
    }
}

private fun matchesSize(result: XdccSearchResult, filter: SizeFilter): Boolean {
    if (filter == SizeFilter.ALL) return true
    val bytes = resultSizeBytes(result) ?: return false
    val gib = 1024L * 1024L * 1024L
    return when (filter) {
        SizeFilter.ALL -> true
        SizeFilter.MOBILE -> {
            val kind = FileTypes.kind(result.filename)
            val quality = detectQuality(result.filename)
            kind in setOf(MediaKind.VIDEO, MediaKind.AUDIO) && bytes <= 2L * gib && qualityRank(quality) <= qualityRank(QualityFilter.P1080)
        }
        SizeFilter.UNDER_1_GB -> bytes < gib
        SizeFilter.ONE_TO_FIVE_GB -> bytes in gib until 5L * gib
        SizeFilter.FIVE_TO_TWENTY_GB -> bytes in 5L * gib until 20L * gib
        SizeFilter.OVER_20_GB -> bytes >= 20L * gib
    }
}

private fun matchesQuality(result: XdccSearchResult, filter: QualityFilter): Boolean =
    filter == QualityFilter.ALL || detectQuality(result.filename) == filter

private fun resultSizeBytes(result: XdccSearchResult): Long? {
    val match = Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*([KMGT]?)(?:I?B)?").find(result.sizeLabel) ?: return null
    val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val multiplier = when (match.groupValues[2].uppercase()) {
        "K" -> 1024.0
        "M" -> 1024.0 * 1024.0
        "G" -> 1024.0 * 1024.0 * 1024.0
        "T" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
        else -> 1.0
    }
    return (value * multiplier).toLong()
}

private fun detectQuality(filename: String): QualityFilter? {
    val name = filename.lowercase()
    return when {
        Regex("(^|[^a-z0-9])8k([^a-z0-9]|$)").containsMatchIn(name) || "4320p" in name -> QualityFilter.K8
        Regex("(^|[^a-z0-9])5k([^a-z0-9]|$)").containsMatchIn(name) || "2880p" in name -> QualityFilter.K5
        Regex("(^|[^a-z0-9])4k([^a-z0-9]|$)").containsMatchIn(name) || "2160p" in name || "uhd" in name -> QualityFilter.K4
        Regex("(^|[^a-z0-9])2k([^a-z0-9]|$)").containsMatchIn(name) || "1440p" in name || "qhd" in name -> QualityFilter.K2
        "1080p" in name || "1080i" in name -> QualityFilter.P1080
        "720p" in name -> QualityFilter.P720
        "576p" in name || "480p" in name || "360p" in name || Regex("(^|[^a-z0-9])sd([^a-z0-9]|$)").containsMatchIn(name) -> QualityFilter.SD
        else -> null
    }
}

private fun qualityRank(quality: QualityFilter?): Int = when (quality) {
    null, QualityFilter.ALL -> 0
    QualityFilter.SD -> 1
    QualityFilter.P720 -> 2
    QualityFilter.P1080 -> 3
    QualityFilter.K2 -> 4
    QualityFilter.K4 -> 5
    QualityFilter.K5 -> 6
    QualityFilter.K8 -> 7
}
