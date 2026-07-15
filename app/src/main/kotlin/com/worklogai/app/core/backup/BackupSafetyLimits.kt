package com.worklogai.app.core.backup

/**
 * Bounds applied while reading an untrusted backup archive.
 *
 * The limits intentionally live outside the reader so JVM tests can exercise every rejection
 * path with small archives instead of manufacturing large files.
 */
internal data class BackupSafetyLimits(
    val maxArchiveSizeBytes: Long = BackupArchiveContract.MAX_ARCHIVE_BYTES,
    val maxEntryCount: Int = BackupArchiveContract.MAX_ZIP_ENTRIES,
    val maxSingleEntrySizeBytes: Long = BackupArchiveContract.MAX_UNCOMPRESSED_ENTRY_BYTES,
    val maxTotalUncompressedSizeBytes: Long = BackupArchiveContract.MAX_UNCOMPRESSED_TOTAL_BYTES,
    val maxCompressionRatio: Long = BackupArchiveContract.MAX_COMPRESSION_RATIO,
    val maxJsonSizeBytes: Long = BackupArchiveContract.MAX_JSON_BYTES,
    val maxAttachmentSizeBytes: Long = BackupArchiveContract.MAX_ATTACHMENT_BYTES,
    val maxPathLength: Int = BackupArchiveContract.MAX_PATH_LENGTH,
    val maxEntries: Int = BackupArchiveContract.MAX_ENTRIES,
    val maxBlocks: Int = BackupArchiveContract.MAX_BLOCKS,
    val maxAttachments: Int = BackupArchiveContract.MAX_ATTACHMENTS,
    val maxSummaries: Int = BackupArchiveContract.MAX_SUMMARIES,
) {
    init {
        require(maxArchiveSizeBytes > 0)
        require(maxEntryCount > 0)
        require(maxSingleEntrySizeBytes > 0)
        require(maxTotalUncompressedSizeBytes > 0)
        require(maxCompressionRatio > 0)
        require(maxJsonSizeBytes > 0)
        require(maxAttachmentSizeBytes > 0)
        require(maxPathLength > 0)
        require(maxEntries >= 0)
        require(maxBlocks >= 0)
        require(maxAttachments >= 0)
        require(maxSummaries >= 0)
    }
}
