package com.worklogai.app.core.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipEntry

class BackupArchivePathsTest {
    @Test
    fun `managed archive paths are accepted`() {
        assertTrue(BackupArchivePaths.run { "manifest.json".isKnownBackupPath() })
        assertTrue(BackupArchivePaths.run { "data/work_entries.json".isKnownBackupPath() })
        assertTrue(BackupArchivePaths.run { "attachments/images/photo.jpg".isKnownBackupPath() })
        assertTrue(BackupArchivePaths.run { ZipEntry("attachments/images/photo.jpg").isSafeFile() })
    }

    @Test
    fun `zip slip absolute and unknown paths are rejected`() {
        val unsafePaths =
            listOf(
                "../evil",
                "attachments/../../../evil",
                "/absolute/path",
                "\\absolute\\path",
                "C:/evil",
                "C:\\evil",
                "attachments\\..\\..\\evil",
                "attachments//../evil",
                "./../evil",
                "attachments/images/../evil",
                "unknown/file.txt",
            )

        unsafePaths.forEach { path ->
            assertFalse(path, BackupArchivePaths.run { path.isKnownBackupPath() })
            assertFalse(path, BackupArchivePaths.run { ZipEntry(path).isSafeFile() })
        }
    }

    @Test
    fun `blank control character and directory entries are rejected`() {
        assertFalse(BackupArchivePaths.run { "".isKnownBackupPath() })
        assertFalse(BackupArchivePaths.run { "attachments/images/\u0000photo.jpg".isKnownBackupPath() })
        assertFalse(BackupArchivePaths.run { ZipEntry("attachments/images/").isSafeFile() })
    }
}
