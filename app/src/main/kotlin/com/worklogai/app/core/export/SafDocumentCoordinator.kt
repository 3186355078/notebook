package com.worklogai.app.core.export

import android.content.Context
import android.net.Uri
import com.worklogai.app.core.backup.BackupArchiveService
import com.worklogai.app.core.backup.BackupCreationResult
import com.worklogai.app.core.backup.BackupOperationResult
import com.worklogai.app.core.backup.BackupPreview
import com.worklogai.app.core.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/** Platform boundary for short-lived Storage Access Framework streams. */
interface SafDocumentCoordinator {
    suspend fun writeMarkdown(
        destination: Uri,
        document: MarkdownDocument,
    ): Result<Unit>

    suspend fun createBackup(destination: Uri): BackupOperationResult<BackupCreationResult>

    suspend fun inspectBackup(source: Uri): BackupOperationResult<BackupPreview>

    suspend fun restoreBackup(source: Uri): BackupOperationResult<BackupPreview>
}

class AndroidSafDocumentCoordinator
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val backupArchiveService: BackupArchiveService,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : SafDocumentCoordinator {
        override suspend fun writeMarkdown(
            destination: Uri,
            document: MarkdownDocument,
        ): Result<Unit> =
            withContext(ioDispatcher) {
                try {
                    context.contentResolver.openOutputStream(destination)?.use { output ->
                        output.write(document.content.encodeToByteArray())
                        output.flush()
                    } ?: return@withContext Result.failure(SafDocumentException())
                    Result.success(Unit)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IOException) {
                    Result.failure(SafDocumentException())
                } catch (_: SecurityException) {
                    Result.failure(SafDocumentException())
                }
            }

        override suspend fun createBackup(destination: Uri): BackupOperationResult<BackupCreationResult> =
            withContext(ioDispatcher) {
                try {
                    context.contentResolver.openOutputStream(destination)?.use { output ->
                        backupArchiveService.createBackup(output)
                    }
                        ?: BackupOperationResult.Failure("无法创建备份文件")
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IOException) {
                    BackupOperationResult.Failure("无法创建备份文件")
                } catch (_: SecurityException) {
                    BackupOperationResult.Failure("无法创建备份文件")
                }
            }

        override suspend fun inspectBackup(source: Uri): BackupOperationResult<BackupPreview> =
            withContext(ioDispatcher) {
                try {
                    context.contentResolver.openInputStream(source)?.use { input ->
                        backupArchiveService.inspectBackup(input)
                    }
                        ?: BackupOperationResult.Failure("无法读取备份文件")
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IOException) {
                    BackupOperationResult.Failure("无法读取备份文件")
                } catch (_: SecurityException) {
                    BackupOperationResult.Failure("无法读取备份文件")
                }
            }

        override suspend fun restoreBackup(source: Uri): BackupOperationResult<BackupPreview> =
            withContext(ioDispatcher) {
                try {
                    context.contentResolver.openInputStream(source)?.use { input ->
                        backupArchiveService.restoreBackup(input)
                    }
                        ?: BackupOperationResult.Failure("无法读取备份文件")
                } catch (error: CancellationException) {
                    throw error
                } catch (_: IOException) {
                    BackupOperationResult.Failure("无法读取备份文件")
                } catch (_: SecurityException) {
                    BackupOperationResult.Failure("无法读取备份文件")
                }
            }
    }

private class SafDocumentException : IOException("Unable to access SAF document")
