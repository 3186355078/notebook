package com.worklogai.app.core.attachment

import android.net.Uri

data class StoredImage(
    val relativePath: String,
    val mimeType: String,
    val fileSize: Long,
    val width: Int,
    val height: Int,
)

data class CleanupResult(
    val deletedCount: Int,
    val failedCount: Int,
)

interface AttachmentFileStore {
    suspend fun importImage(sourceUri: Uri): Result<StoredImage>

    suspend fun delete(relativePath: String): Result<Unit>

    suspend fun exists(relativePath: String): Boolean

    suspend fun cleanupOrphans(referencedPaths: Set<String>): CleanupResult

    fun fileFor(relativePath: String): java.io.File?
}
