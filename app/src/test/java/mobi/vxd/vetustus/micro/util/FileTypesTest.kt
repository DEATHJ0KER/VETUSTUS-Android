package mobi.vxd.vetustus.micro.util

import mobi.vxd.vetustus.micro.data.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTypesTest {
    @Test
    fun recognizesMediaArchivesUsedByXdccPacks() {
        assertEquals(ArchiveKind.ZIP, FileTypes.archiveKind("album.zip"))
        assertEquals(ArchiveKind.RAR, FileTypes.archiveKind("album.FLAC.rar"))
        assertEquals(ArchiveKind.TAR, FileTypes.archiveKind("release.tar"))
        assertEquals(ArchiveKind.TAR_GZ, FileTypes.archiveKind("release.tar.gz"))
        assertEquals(ArchiveKind.TAR_GZ, FileTypes.archiveKind("release.tgz"))
        assertEquals(ArchiveKind.TAR_BZ2, FileTypes.archiveKind("release.tbz2"))
        assertEquals(ArchiveKind.TAR_XZ, FileTypes.archiveKind("release.txz"))
        assertEquals(MediaKind.ARCHIVE, FileTypes.kind("Vasco.Rossi.FLAC.rar"))
    }

    @Test
    fun extractsOnlyRecognizedAudioAndVideoPayloads() {
        assertTrue(FileTypes.isExtractableMedia("disc/01-track.flac"))
        assertTrue(FileTypes.isExtractableMedia("disc/02-track.mp3"))
        assertTrue(FileTypes.isExtractableMedia("movie/feature.mkv"))
        assertTrue(FileTypes.isExtractableMedia("movie/feature.mp4"))
        assertFalse(FileTypes.isExtractableMedia("release.nfo"))
        assertFalse(FileTypes.isExtractableMedia("readme.txt"))
        assertFalse(FileTypes.isExtractableMedia("setup.exe"))
    }
}
