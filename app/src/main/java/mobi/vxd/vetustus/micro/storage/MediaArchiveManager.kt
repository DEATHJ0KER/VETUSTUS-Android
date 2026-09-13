package mobi.vxd.vetustus.micro.storage

import android.content.Context
import android.os.StatFs
import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import mobi.vxd.vetustus.micro.data.LibraryItem
import mobi.vxd.vetustus.micro.data.LibraryRepository
import mobi.vxd.vetustus.micro.network.IrcProtocol
import mobi.vxd.vetustus.micro.util.ArchiveKind
import mobi.vxd.vetustus.micro.util.FileTypes
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

class MediaArchiveManager(
    private val context: Context,
    private val publisher: MediaStorePublisher,
    private val library: LibraryRepository,
) {
    suspend fun extractMedia(
        downloadId: String,
        archiveFile: File,
        archiveName: String,
    ): List<LibraryItem> = withContext(Dispatchers.IO) {
        require(archiveFile.isFile && archiveFile.length() > 0L) { "Archivio locale assente o vuoto" }
        val kind = FileTypes.archiveKind(archiveName) ?: error("Formato archivio non riconosciuto")
        val created = mutableListOf<PublishedFile>()
        try {
            val budget = extractionBudget(archiveFile.length())
            val state = ExtractionState(
                baseDirectory = archiveBaseName(archiveName),
                totalLimit = budget,
            )
            when (kind) {
                ArchiveKind.ZIP -> extractZip(archiveFile, state, created)
                ArchiveKind.RAR -> extractRar(archiveFile, state, created)
                ArchiveKind.TAR,
                ArchiveKind.TAR_GZ,
                ArchiveKind.TAR_BZ2,
                ArchiveKind.TAR_XZ,
                -> extractTar(archiveFile, kind, state, created)
            }
            val items = created.map { file ->
                LibraryItem(
                    id = UUID.randomUUID().toString(),
                    downloadId = downloadId,
                    displayName = file.displayName,
                    contentUri = file.uri.toString(),
                    mimeType = file.mimeType,
                    sizeBytes = file.sizeBytes,
                    kind = FileTypes.kind(file.displayName),
                    parentArchive = archiveName,
                    createdAt = System.currentTimeMillis(),
                )
            }
            items.forEach(library::insert)
            items
        } catch (error: Throwable) {
            created.forEach { file -> publisher.delete(file.uri) }
            throw error
        }
    }

    fun rollback(items: List<LibraryItem>) {
        items.forEach { item ->
            publisher.delete(android.net.Uri.parse(item.contentUri))
            library.delete(item.id)
        }
    }

    private suspend fun extractZip(
        source: File,
        state: ExtractionState,
        created: MutableList<PublishedFile>,
    ) {
        ZipInputStream(BufferedInputStream(source.inputStream())).use { zip ->
            while (true) {
                coroutineContext.ensureActive()
                val entry = zip.nextEntry ?: break
                state.entrySeen()
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val normalized = normalizeEntry(entry.name)
                    ?: error("Archivio rifiutato: percorso interno non sicuro")
                if (!state.acceptPath(normalized)) error("Archivio rifiutato: percorso duplicato $normalized")
                if (!FileTypes.isExtractableMedia(normalized)) {
                    zip.closeEntry()
                    continue
                }
                val expected = entry.size.coerceAtLeast(0L)
                state.checkDeclaredSize(expected)
                created += publishMediaEntry(normalized, expected, state) { output ->
                    copyEntry(zip, output, state)
                }
                zip.closeEntry()
            }
        }
    }

    private suspend fun extractTar(
        source: File,
        kind: ArchiveKind,
        state: ExtractionState,
        created: MutableList<PublishedFile>,
    ) {
        source.inputStream().buffered().use { raw ->
            val payload: InputStream = when (kind) {
                ArchiveKind.TAR -> raw
                ArchiveKind.TAR_GZ -> GzipCompressorInputStream(raw, true)
                ArchiveKind.TAR_BZ2 -> BZip2CompressorInputStream(raw, true)
                ArchiveKind.TAR_XZ -> XZCompressorInputStream(raw, true)
                else -> error("Tipo TAR non valido")
            }
            TarArchiveInputStream(payload).use { tar ->
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = tar.nextTarEntry ?: break
                    state.entrySeen()
                    if (entry.isDirectory) continue
                    val normalized = normalizeEntry(entry.name)
                        ?: error("Archivio rifiutato: percorso interno non sicuro")
                    if (!state.acceptPath(normalized)) error("Archivio rifiutato: percorso duplicato $normalized")
                    if (!FileTypes.isExtractableMedia(normalized)) continue
                    val expected = entry.size.coerceAtLeast(0L)
                    state.checkDeclaredSize(expected)
                    created += publishMediaEntry(normalized, expected, state) { output ->
                        copyEntry(tar, output, state)
                    }
                }
            }
        }
    }

    private suspend fun extractRar(
        source: File,
        state: ExtractionState,
        created: MutableList<PublishedFile>,
    ) {
        Archive(source).use { rar ->
            for (header in rar.fileHeaders) {
                coroutineContext.ensureActive()
                state.entrySeen()
                if (header.isDirectory) continue
                val normalized = normalizeEntry(header.fileName)
                    ?: error("Archivio rifiutato: percorso interno non sicuro")
                if (!state.acceptPath(normalized)) error("Archivio rifiutato: percorso duplicato $normalized")
                if (!FileTypes.isExtractableMedia(normalized)) continue
                val expected = header.unpSize.coerceAtLeast(0L)
                state.checkDeclaredSize(expected)
                created += publishMediaEntry(normalized, expected, state) { output ->
                    var written = 0L
                    val guarded = GuardedOutputStream(output) { count ->
                        written = count
                        state.checkProgress(count)
                    }
                    rar.extractFile(header, guarded)
                    guarded.flush()
                    written
                }
            }
        }
    }

    private suspend fun publishMediaEntry(
        normalizedPath: String,
        expectedSize: Long,
        state: ExtractionState,
        writer: suspend (OutputStream) -> Long,
    ): PublishedFile {
        val segments = normalizedPath.split('/')
        val filename = segments.last()
        val parent = segments.dropLast(1).joinToString("/")
        val relativeDirectory = listOf(state.baseDirectory, parent)
            .filter(String::isNotBlank)
            .joinToString("/")
        var before = state.totalWritten
        return publisher.publishStream(
            requestedName = filename,
            relativeSubdirectory = relativeDirectory,
            expectedSize = expectedSize,
            allowEmpty = false,
        ) { output ->
            val written = writer(output)
            val delta = state.totalWritten - before
            if (delta == 0L && written > 0L) {
                state.addWritten(written)
            }
            written
        }
    }

    private suspend fun copyEntry(
        input: InputStream,
        output: OutputStream,
        state: ExtractionState,
    ): Long {
        val buffer = ByteArray(128 * 1024)
        var entryWritten = 0L
        while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            entryWritten += read
            state.addWritten(read.toLong(), entryWritten)
            output.write(buffer, 0, read)
        }
        return entryWritten
    }

    private fun extractionBudget(archiveBytes: Long): Long {
        val available = runCatching {
            val volumePath = context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
            StatFs(volumePath).availableBytes
        }.getOrDefault(MAX_TOTAL_BYTES)
        val ratioLimit = (archiveBytes.coerceAtLeast(1L) * MAX_EXPANSION_RATIO)
            .coerceAtLeast(MIN_TOTAL_LIMIT)
        val totalLimit = minOf(
            MAX_TOTAL_BYTES,
            ratioLimit,
            (available - RESERVED_BYTES).coerceAtLeast(0L),
        )
        if (totalLimit <= 0L) error("Spazio insufficiente per estrarre l'archivio")
        return totalLimit
    }

    private fun normalizeEntry(raw: String): String? {
        val source = raw.replace('\\', '/').trim()
        if (source.isBlank() || source.startsWith('/') || DRIVE_PREFIX.containsMatchIn(source)) return null
        val output = mutableListOf<String>()
        for (segment in source.split('/')) {
            when {
                segment.isBlank() || segment == "." -> Unit
                segment == ".." -> return null
                else -> output += IrcProtocol.sanitizeFilename(segment)
            }
        }
        return output.takeIf { it.isNotEmpty() }?.joinToString("/")
    }

    private fun archiveBaseName(name: String): String {
        val lower = name.lowercase()
        val suffix = listOf(".tar.bz2", ".tar.gz", ".tar.xz", ".tbz2", ".tgz", ".txz", ".tbz", ".zip", ".rar", ".tar")
            .firstOrNull(lower::endsWith)
        val base = if (suffix == null) name.substringBeforeLast('.', name) else name.dropLast(suffix.length)
        return IrcProtocol.sanitizeFilename(base.ifBlank { "archive" })
    }

    private class ExtractionState(
        val baseDirectory: String,
        val totalLimit: Long,
    ) {
        private val seen = hashSetOf<String>()
        private var entries = 0
        var totalWritten = 0L
            private set
        private var currentEntryStart = 0L

        fun entrySeen() {
            entries++
            if (entries > MAX_ENTRIES) error("Archivio rifiutato: oltre $MAX_ENTRIES voci")
            currentEntryStart = totalWritten
        }

        fun acceptPath(path: String): Boolean = seen.add(path.lowercase())

        fun checkDeclaredSize(size: Long) {
            if (size > MAX_ENTRY_BYTES) error("Archivio rifiutato: file interno troppo grande")
            if (size > 0L && totalWritten + size > totalLimit) {
                error("Archivio rifiutato: spazio/espansione oltre il limite di sicurezza")
            }
        }

        fun addWritten(bytes: Long, entryWritten: Long = totalWritten - currentEntryStart + bytes) {
            if (bytes < 0L) error("Conteggio archivio non valido")
            totalWritten += bytes
            if (entryWritten > MAX_ENTRY_BYTES || totalWritten > totalLimit) {
                error("Archivio rifiutato: espansione oltre il limite di sicurezza")
            }
        }

        fun checkProgress(entryWritten: Long) {
            val expectedTotal = currentEntryStart + entryWritten
            if (entryWritten > MAX_ENTRY_BYTES || expectedTotal > totalLimit) {
                error("Archivio rifiutato: espansione oltre il limite di sicurezza")
            }
            totalWritten = expectedTotal
        }
    }

    private class GuardedOutputStream(
        delegate: OutputStream,
        private val onCount: (Long) -> Unit,
    ) : FilterOutputStream(delegate) {
        private var count = 0L

        override fun write(b: Int) {
            count++
            onCount(count)
            out.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (len <= 0) return
            count += len.toLong()
            onCount(count)
            out.write(b, off, len)
        }

        override fun close() {
            flush()
        }
    }

    private companion object {
        const val MAX_ENTRIES = 20_000
        const val MAX_ENTRY_BYTES = 128L * 1024L * 1024L * 1024L
        const val MAX_TOTAL_BYTES = 512L * 1024L * 1024L * 1024L
        const val MIN_TOTAL_LIMIT = 256L * 1024L * 1024L
        const val RESERVED_BYTES = 512L * 1024L * 1024L
        const val MAX_EXPANSION_RATIO = 200L
        val DRIVE_PREFIX = Regex("^[A-Za-z]:")
    }
}
