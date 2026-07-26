package com.worklogai.app.core.backup

import android.content.Context
import com.worklogai.app.core.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

data class BackupCreationResult(
    val warningCount: Int,
)

data class BackupPreview(
    val createdAt: java.time.Instant,
    val entryCount: Int,
    val blockCount: Int,
    val attachmentCount: Int,
    val summaryCount: Int,
    val warningCount: Int,
    val todoCount: Int = 0,
)

sealed interface BackupOperationResult<out T> {
    data class Success<T>(
        val value: T,
        val warningMessage: String? = null,
    ) : BackupOperationResult<T>

    data class Failure(
        val message: String,
        val requiresRecovery: Boolean = false,
    ) : BackupOperationResult<Nothing>
}

interface BackupArchiveService {
    suspend fun createBackup(destination: OutputStream): BackupOperationResult<BackupCreationResult>

    suspend fun inspectBackup(source: InputStream): BackupOperationResult<BackupPreview>

    suspend fun restoreBackup(source: InputStream): BackupOperationResult<BackupPreview>
}

class DefaultBackupArchiveService
    @Inject
    internal constructor(
        @ApplicationContext private val context: Context,
        private val archiveWriter: BackupArchiveWriter,
        private val archiveReader: BackupArchiveReader,
        private val restoreCoordinator: BackupRestoreCoordinator,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : BackupArchiveService {
        override suspend fun createBackup(destination: OutputStream): BackupOperationResult<BackupCreationResult> =
            withContext(ioDispatcher) { archiveWriter.createBackup(destination) }

        override suspend fun inspectBackup(source: InputStream): BackupOperationResult<BackupPreview> =
            withContext(ioDispatcher) {
                archiveReader
                    .readArchive(source, cacheDirectory())
                    .useResult { archive -> BackupOperationResult.Success(archive.preview) }
            }

        override suspend fun restoreBackup(source: InputStream): BackupOperationResult<BackupPreview> =
            withContext(ioDispatcher) {
                archiveReader
                    .readArchive(source, cacheDirectory())
                    .useResult(restoreCoordinator::restore)
            }

        private fun cacheDirectory(): File = File(context.cacheDir, BackupArchiveContract.BACKUP_CACHE_DIRECTORY)
    }

internal val backupJson =
    Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

internal fun failure(
    message: String,
    requiresRecovery: Boolean = false,
): BackupOperationResult.Failure = BackupOperationResult.Failure(message, requiresRecovery)
