package mobi.vxd.vetustus.micro.storage

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import mobi.vxd.vetustus.micro.data.LibraryItem
import mobi.vxd.vetustus.micro.data.LibraryRepository
import mobi.vxd.vetustus.micro.network.IrcProtocol
import mobi.vxd.vetustus.micro.util.FileTypes
import java.io.BufferedInputStream
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

class ZipArchiveManager(
    private val context: Context,
    private val publisher: MediaStorePublisher,
    private val library: LibraryRepository,
) {
    suspend fun extract(
        downloadId: String,
        archive: PublishedFile,
    ): List<LibraryItem> = withContext(Dispatchers.IO) {
        val created = mutableListOf<PublishedFile>()
        try {
            val available = runCatching {
                val volumePath = context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
                StatFs(volumePath).availableBytes
            }.getOrDefault(MAX_TOTAL_BYTES)
            val ratioLimit = (archive.sizeBytes.coerceAtLeast(1L) * MAX_EXPANSION_RATIO)
                .coerceAtLeast(MIN_TOTAL_LIMIT)
            val totalLimit = minOf(MAX_TOTAL_BYTES, ratioLimit, (available - RESERVED_BYTES).coerceAtLeast(0L))
            if (totalLimit <= 0L) error("Spazio insufficiente per estrarre l'archivio")
            val baseDirectory = IrcProtocol.sanitizeFilename(archive.displayName.substringBeforeLast('.', archive.displayName))
            val seen = hashSetOf<String>()
            var entries = 0
            var totalWritten = 0L
            val source = context.contentResolver.openInputStream(archive.uri)
                ?: error("Archivio ZIP non leggibile")
            ZipInputStream(BufferedInputStream(source)).use { zip ->
                while (true) {
                    coroutineContext.ensureActive()
                    val entry = zip.nextEntry ?: break
                    val normalized = normalizeEntry(entry.name)
                    if (normalized == null) error("Archivio rifiutato: percorso interno non sicuro")
                    entries++
                    if (entries > MAX_ENTRIES) error("Archivio rifiutato: oltre $MAX_ENTRIES voci")
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    val collisionKey = normalized.lowercase()
                    if (!seen.add(collisionKey)) error("Archivio rifiutato: percorso duplicato $normalized")
                    if (entry.size > MAX_ENTRY_BYTES) error("Archivio rifiutato: file interno troppo grande")
                    val segments = normalized.split('/')
                    val filename = segments.last()
                    val parent = segments.dropLast(1).joinToString("/")
                    val relativeDirectory = listOf(baseDirectory, parent).filter(String::isNotBlank).joinToString("/")
                    var entryWritten = 0L
                    val published = publisher.publishStream(
                        requestedName = filename,
                        relativeSubdirectory = relativeDirectory,
                        expectedSize = entry.size.coerceAtLeast(0L),
                        allowEmpty = true,
                    ) { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = zip.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            entryWritten += read
                            totalWritten += read
                            if (entryWritten > MAX_ENTRY_BYTES || totalWritten > totalLimit) {
                                error("Archivio rifiutato: espansione oltre il limite di sicurezza")
                            }
                            output.write(buffer, 0, read)
                        }
                        entryWritten
                    }
                    created += published
                    zip.closeEntry()
                }
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
                    parentArchive = archive.displayName,
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

    private companion object {
        const val MAX_ENTRIES = 5_000
        const val MAX_ENTRY_BYTES = 16L * 1024L * 1024L * 1024L
        const val MAX_TOTAL_BYTES = 64L * 1024L * 1024L * 1024L
        const val MIN_TOTAL_LIMIT = 128L * 1024L * 1024L
        const val RESERVED_BYTES = 256L * 1024L * 1024L
        const val MAX_EXPANSION_RATIO = 200L
        val DRIVE_PREFIX = Regex("^[A-Za-z]:")
    }
}

fun PublishedFile.toLibraryItem(downloadId: String, parentArchive: String = "") = LibraryItem(
    id = UUID.randomUUID().toString(),
    downloadId = downloadId,
    displayName = displayName,
    contentUri = uri.toString(),
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    kind = FileTypes.kind(displayName),
    parentArchive = parentArchive,
    createdAt = System.currentTimeMillis(),
)
