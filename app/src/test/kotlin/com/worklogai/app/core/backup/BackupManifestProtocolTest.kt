package com.worklogai.app.core.backup

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupManifestProtocolTest {
    @Test
    fun `manifest protocol has stable name version and serializable file metadata`() {
        val manifest = manifest()

        val decoded = backupJson.decodeFromString<BackupManifest>(backupJson.encodeToString(manifest))

        assertEquals(BACKUP_FORMAT_NAME, decoded.formatName)
        assertEquals(BACKUP_FORMAT_VERSION, decoded.formatVersion)
        assertEquals("data/work_entries.json", decoded.files.single().path)
    }

    @Test
    fun `unknown manifest fields remain forward compatible`() {
        val encoded = backupJson.encodeToString(manifest()).dropLast(1) + ",\"futureField\":true}"

        val decoded = backupJson.decodeFromString<BackupManifest>(encoded)

        assertEquals(BACKUP_FORMAT_NAME, decoded.formatName)
    }

    @Test
    fun `truncated manifest json is rejected`() {
        assertThrows(Exception::class.java) {
            backupJson.decodeFromString<BackupManifest>("{\"formatName\":")
        }
    }

    private fun manifest() =
        BackupManifest(
            formatName = BACKUP_FORMAT_NAME,
            formatVersion = BACKUP_FORMAT_VERSION,
            appId = "com.worklogai.app",
            appVersionName = "1.0",
            appVersionCode = 1,
            createdAt = "2026-07-15T00:00:00Z",
            databaseVersion = 1,
            entryCount = 1,
            blockCount = 1,
            attachmentCount = 0,
            summaryCount = 0,
            files = listOf(BackupFileManifest("data/work_entries.json", 2, "a".repeat(64))),
        )
}
