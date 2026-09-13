package mobi.vxd.vetustus.micro.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import mobi.vxd.vetustus.micro.network.IrcProtocol
import mobi.vxd.vetustus.micro.util.FileTypes
import java.io.File
import java.io.OutputStream

data class PublishedFile(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
)

class MediaStorePublisher(private val context: Context) {
    private val resolver get() = context.contentResolver

    suspend fun publishFile(source: File, requestedName: String): PublishedFile {
        require(source.isFile && source.length() > 0L) { "File parziale assente o vuoto" }
        return publishStream(
            requestedName = requestedName,
            relativeSubdirectory = "",
            expectedSize = source.length(),
        ) { output ->
            source.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    total += read
                }
                total
            }
        }
    }

    suspend fun publishStream(
        requestedName: String,
        relativeSubdirectory: String,
        expectedSize: Long = 0L,
        allowEmpty: Boolean = false,
        writer: suspend (OutputStream) -> Long,
    ): PublishedFile {
        val displayName = IrcProtocol.sanitizeFilename(requestedName)
        val mimeType = FileTypes.mimeType(displayName)
        val relativePath = buildRelativePath(relativeSubdirectory)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Impossibile creare il file in Download")
        try {
            val written = resolver.openOutputStream(uri, "w")?.buffered()?.use { output -> writer(output) }
                ?: error("Impossibile aprire il file di destinazione")
            if (written < 0L || (!allowEmpty && written == 0L)) error("Nessun dato scritto nel file finale")
            if (expectedSize > 0L && written != expectedSize) {
                error("Copia finale incompleta: attesi $expectedSize byte, scritti $written")
            }
            val updated = resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
            if (updated != 1) error("Impossibile completare la pubblicazione del file")
            return PublishedFile(uri, displayName, mimeType, written)
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    fun delete(uri: Uri): Boolean = runCatching { resolver.delete(uri, null, null) > 0 }.getOrDefault(false)

    private fun buildRelativePath(subdirectory: String): String {
        val safeSegments = subdirectory.replace('\\', '/').split('/')
            .map(String::trim)
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .map(IrcProtocol::sanitizeFilename)
        return buildString {
            append(Environment.DIRECTORY_DOWNLOADS)
            append("/VETUSTUS Micro/")
            if (safeSegments.isNotEmpty()) {
                append(safeSegments.joinToString("/"))
                append('/')
            }
        }
    }
}
