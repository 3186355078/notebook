package com.worklogai.app.core.backup

import org.junit.Assert.assertThrows
import org.junit.Test

class BackupArchiveEntryValidatorTest {
    @Test
    fun `rejects identical and case-insensitive duplicate zip entries`() {
        assertRejected(
            listOf(
                file(BackupArchiveContract.MANIFEST_FILE),
                file(BackupArchiveContract.MANIFEST_FILE),
            ),
        )
        assertRejected(
            listOf(
                file(BackupArchiveContract.ENTRIES_FILE),
                file(BackupArchiveContract.ENTRIES_FILE.uppercase()),
            ),
        )
    }

    @Test
    fun `rejects path normalization conflicts before a zip entry can overwrite another`() {
        assertRejected(
            listOf(
                file(BackupArchiveContract.ENTRIES_FILE),
                file("./${BackupArchiveContract.ENTRIES_FILE}"),
            ),
        )
        assertRejected(
            listOf(
                file(BackupArchiveContract.ENTRIES_FILE),
                file("data//work_entries.json"),
            ),
        )
        assertRejected(
            listOf(
                file(BackupArchiveContract.ENTRIES_FILE),
                file("data\\work_entries.json"),
            ),
        )
    }

    @Test
    fun `unknown entry size is permitted only for stream based follow-up validation`() {
        BackupArchiveEntryValidator.validate(
            listOf(file(BackupArchiveContract.MANIFEST_FILE, size = -1, compressedSize = -1)),
            limits(),
        )
    }

    @Test
    fun `enforces entry count individual size total size and compression ratio limits`() {
        assertRejected(
            listOf(
                file(BackupArchiveContract.MANIFEST_FILE),
                file(BackupArchiveContract.ENTRIES_FILE),
            ),
            limits(maxEntryCount = 1),
        )
        assertRejected(
            listOf(file(BackupArchiveContract.MANIFEST_FILE, size = 11)),
            limits(maxSingleEntrySizeBytes = 10),
        )
        assertRejected(
            listOf(
                file(BackupArchiveContract.MANIFEST_FILE, size = 6),
                file(BackupArchiveContract.ENTRIES_FILE, size = 6),
            ),
            limits(maxTotalUncompressedSizeBytes = 10),
        )
        assertRejected(
            listOf(file(BackupArchiveContract.MANIFEST_FILE, size = 11, compressedSize = 1)),
            limits(maxCompressionRatio = 10),
        )
    }

    @Test
    fun `accepts every archive metadata limit exactly at its boundary`() {
        BackupArchiveEntryValidator.validate(
            listOf(
                file(BackupArchiveContract.MANIFEST_FILE, size = 10, compressedSize = 1),
                file(BackupArchiveContract.ENTRIES_FILE, size = 10, compressedSize = 1),
            ),
            limits(
                maxEntryCount = 2,
                maxSingleEntrySizeBytes = 10,
                maxTotalUncompressedSizeBytes = 20,
                maxCompressionRatio = 10,
            ),
        )
    }

    @Test
    fun `directory entries count toward the archive entry limit`() {
        assertRejected(
            listOf(
                directory("data/"),
                file(BackupArchiveContract.MANIFEST_FILE),
            ),
            limits(maxEntryCount = 1),
        )
    }

    @Test
    fun `zero compressed size never causes division by zero`() {
        BackupArchiveEntryValidator.validate(
            listOf(file(BackupArchiveContract.MANIFEST_FILE, size = 10, compressedSize = 0)),
            limits(maxSingleEntrySizeBytes = 10),
        )
    }

    @Test
    fun `rejects a path longer than the configured archive path limit`() {
        val attachmentPath = "attachments/images/${"a".repeat(20)}.jpg"

        assertRejected(
            listOf(file(attachmentPath)),
            BackupSafetyLimits(maxPathLength = attachmentPath.length - 1),
        )
    }

    private fun assertRejected(
        entries: List<BackupZipEntryMetadata>,
        limits: BackupSafetyLimits = limits(),
    ) {
        assertThrows(IllegalArgumentException::class.java) {
            BackupArchiveEntryValidator.validate(entries, limits)
        }
    }

    private fun limits(
        maxEntryCount: Int = 10,
        maxSingleEntrySizeBytes: Long = 100,
        maxTotalUncompressedSizeBytes: Long = 200,
        maxCompressionRatio: Long = 100,
    ): BackupSafetyLimits =
        BackupSafetyLimits(
            maxEntryCount = maxEntryCount,
            maxSingleEntrySizeBytes = maxSingleEntrySizeBytes,
            maxTotalUncompressedSizeBytes = maxTotalUncompressedSizeBytes,
            maxCompressionRatio = maxCompressionRatio,
        )

    private fun file(
        name: String,
        size: Long = 1,
        compressedSize: Long = 1,
    ): BackupZipEntryMetadata =
        BackupZipEntryMetadata(
            name = name,
            isDirectory = false,
            size = size,
            compressedSize = compressedSize,
        )

    private fun directory(name: String): BackupZipEntryMetadata =
        BackupZipEntryMetadata(
            name = name,
            isDirectory = true,
            size = 0,
            compressedSize = 0,
        )
}
