package com.nuvio.app.features.downloads

import com.nuvio.app.core.desktop.DesktopAppPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDownloadFilesTest {
    @Test
    fun sanitizeFileNameRemovesWindowsInvalidCharacters() {
        assertEquals(
            "Movie _ Pilot_ 1080p.mp4",
            DesktopDownloadFiles.sanitizeFileName("Movie / Pilot: 1080p.mp4"),
        )
    }

    @Test
    fun sanitizeFileNameRejectsParentDirectoryNames() {
        assertEquals("download.bin", DesktopDownloadFiles.sanitizeFileName(".."))
    }

    @Test
    fun partialFileUsesPartSuffixBesideTargetFile() {
        val target = DesktopDownloadFiles.targetFile("episode.mp4")
        val partial = DesktopDownloadFiles.partialFile("episode.mp4")

        assertEquals(target.parent, partial.parent)
        assertEquals("episode.mp4.part", partial.fileName.toString())
    }

    @Test
    fun managedPathMustStayInsideDownloadsDirectory() {
        val managed = DesktopAppPaths.downloadsDir.resolve("episode.mp4")
        val outside = DesktopAppPaths.downloadsDir.parent.resolve("episode.mp4")

        assertTrue(DesktopDownloadFiles.isManagedDownloadPath(managed))
        assertFalse(DesktopDownloadFiles.isManagedDownloadPath(outside))
        assertFalse(DesktopDownloadFiles.isManagedDownloadPath(DesktopAppPaths.downloadsDir))
    }
}
