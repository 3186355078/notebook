package com.worklogai.app.core.backup

/** Metadata preflight shared by the real ZIP reader and adversarial JVM tests. */
internal data class BackupZipEntryMetadata(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long,
)

internal object BackupArchiveEntryValidator {
    fun validate(
        entries: List<BackupZipEntryMetadata>,
        limits: BackupSafetyLimits,
    ) {
        require(entries.size <= limits.maxEntryCount)
        require(entries.all { entry -> entry.isSafe(limits.maxPathLength) })
        require(entries.map { it.name.lowercase() }.distinct().size == entries.size)

        var totalUncompressed = 0L
        entries.filterNot(BackupZipEntryMetadata::isDirectory).forEach { entry ->
            require(entry.size == UNKNOWN_ARCHIVE_ENTRY_SIZE || entry.size in 0..limits.maxSingleEntrySizeBytes)
            if (entry.size != UNKNOWN_ARCHIVE_ENTRY_SIZE) {
                totalUncompressed += entry.size
                require(totalUncompressed <= limits.maxTotalUncompressedSizeBytes)
            }
            if (entry.compressedSize > 0 && entry.size != UNKNOWN_ARCHIVE_ENTRY_SIZE) {
                require(entry.size <= entry.compressedSize * limits.maxCompressionRatio)
            }
        }
    }

    private fun BackupZipEntryMetadata.isSafe(maxPathLength: Int): Boolean =
        if (isDirectory) {
            BackupArchivePaths.run { name.isSafeDirectoryName() }
        } else {
            BackupArchivePaths.run { name.isKnownBackupPath(maxPathLength) }
        }
}

internal const val UNKNOWN_ARCHIVE_ENTRY_SIZE = -1L
