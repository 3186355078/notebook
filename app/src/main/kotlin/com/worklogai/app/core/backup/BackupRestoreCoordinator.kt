package com.worklogai.app.core.backup

import android.content.Context
import com.worklogai.app.core.attachment.AttachmentFileStore
import com.worklogai.app.core.autosummary.AutoSummaryScheduleStateRepository
import com.worklogai.app.core.autosummary.AutoSummaryScheduler
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.datastore.AiSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipFile
import javax.inject.Inject

/** Runtime collaborators whose state is not part of the backed-up payload. */
internal class RestoreDependencies
    @Inject
    constructor(
        val settingsRepository: AiSettingsRepository,
        val autoSummaryScheduler: AutoSummaryScheduler,
        val scheduleStateRepository: AutoSummaryScheduleStateRepository,
        val directoryOperations: RestoreDirectoryOperations,
        val journalStore: RestoreJournalStore = TransientRestoreJournalStore(),
    )

internal class BackupRestoreCoordinator
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val dataGateway: BackupDataGateway,
        private val attachmentFileStore: AttachmentFileStore,
        private val dependencies: RestoreDependencies,
    ) {
        private val directories = AttachmentRestoreDirectories(context, dependencies.directoryOperations)
        private val journals = RestoreJournalLifecycle(dependencies.journalStore)

        suspend fun restore(archive: ParsedArchive): BackupOperationResult<BackupPreview> =
            loadOriginalState().fold(
                onFailure = { failure("无法读取当前数据") },
                onSuccess = { original -> restoreWithOriginalState(archive, original) },
            )

        private suspend fun loadOriginalState(): Result<RestoreOriginalState> =
            try {
                Result.success(
                    RestoreOriginalState(
                        database = dataGateway.snapshot().getOrThrow(),
                        settings = dependencies.settingsRepository.getSettings(),
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Result.failure(BackupStorageException())
            }

        private suspend fun restoreWithOriginalState(
            archive: ParsedArchive,
            original: RestoreOriginalState,
        ): BackupOperationResult<BackupPreview> {
            var stageDirectory: File? = null
            var swap: AttachmentSwap? = null
            return try {
                stageDirectory = directories.createStageDirectory()
                var journal = journals.begin(stageDirectory)
                directories.extractAttachments(archive, stageDirectory)
                directories.swap(stageDirectory).fold(
                    onFailure = {
                        journals.clear()
                        failure("恢复附件失败，当前数据未修改")
                    },
                    onSuccess = { attachmentSwap ->
                        swap = attachmentSwap
                        journal =
                            journals.update(
                                journal,
                                RestorePhase.ATTACHMENTS_SWITCHED,
                                attachmentSwap.previous.name,
                            )
                        replaceDataAndSettings(archive, original, attachmentSwap, journal)
                    },
                )
            } catch (error: CancellationException) {
                swap?.let { compensateCancellation(original, it) }
                throw error
            } catch (_: AttachmentRollbackException) {
                journals.requireRecovery()
                failure("恢复遇到严重问题，请重新启动后再检查数据", requiresRecovery = true)
            } catch (_: IOException) {
                failureAfterUnexpectedError(original, swap)
            } catch (_: IllegalStateException) {
                failureAfterUnexpectedError(original, swap)
            } finally {
                stageDirectory?.let(directories::cleanup)
            }
        }

        private suspend fun replaceDataAndSettings(
            archive: ParsedArchive,
            original: RestoreOriginalState,
            swap: AttachmentSwap,
            journal: RestoreJournal,
        ): BackupOperationResult<BackupPreview> =
            if (dataGateway.replace(BackupArchivePayloadMapper.toDatabaseSnapshot(archive.payload)).isFailure) {
                failureAfterAttachmentRollback(swap, "恢复数据失败，当前数据未修改")
            } else {
                val databaseJournal = journals.update(journal, RestorePhase.DATABASE_REPLACED)
                restoreSettingsAndFinish(archive, original, swap, databaseJournal)
            }

        private suspend fun restoreSettingsAndFinish(
            archive: ParsedArchive,
            original: RestoreOriginalState,
            swap: AttachmentSwap,
            journal: RestoreJournal,
        ): BackupOperationResult<BackupPreview> {
            val restoredSettings = archive.payload.settings.toAiSettings()
            if (dependencies.settingsRepository.saveSettings(restoredSettings).isFailure) {
                return rollbackAfterSettingsFailure(original, swap)
            }
            val settingsJournal = journals.update(journal, RestorePhase.SETTINGS_REPLACED)
            val schedulerWarning = !reconcileAfterRestore(restoredSettings)
            attachmentFileStore.cleanupOrphans(
                archive.payload.attachments
                    .map(BackupAttachment::localPath)
                    .toSet(),
            )
            directories.cleanup(swap.previous)
            journals.finish(settingsJournal, schedulerWarning)
            return BackupOperationResult.Success(
                archive.preview,
                warningMessage = if (schedulerWarning) SCHEDULER_WARNING else null,
            )
        }

        private suspend fun rollbackAfterSettingsFailure(
            original: RestoreOriginalState,
            swap: AttachmentSwap,
        ): BackupOperationResult<BackupPreview> {
            val databaseRestored = dataGateway.replace(original.database).isSuccess
            val settingsRestored = dependencies.settingsRepository.saveSettings(original.settings).isSuccess
            val attachmentRestored = directories.rollback(swap).isSuccess
            return if (databaseRestored && settingsRestored && attachmentRestored) {
                journals.clear()
                failure("恢复设置失败，当前数据未修改")
            } else {
                journals.requireRecovery()
                failure("恢复遇到严重问题，请重新启动后再检查数据", requiresRecovery = true)
            }
        }

        private fun failureAfterAttachmentRollback(
            swap: AttachmentSwap,
            rolledBackMessage: String,
        ): BackupOperationResult<BackupPreview> =
            if (directories.rollback(swap).isSuccess) {
                journals.clear()
                failure(rolledBackMessage)
            } else {
                journals.requireRecovery()
                failure("恢复遇到严重问题，请重新启动后再检查数据", requiresRecovery = true)
            }

        private suspend fun compensateCancellation(
            original: RestoreOriginalState,
            swap: AttachmentSwap,
        ) {
            withContext(NonCancellable) {
                dataGateway.replace(original.database)
                dependencies.settingsRepository.saveSettings(original.settings)
                if (directories.rollback(swap).isSuccess) {
                    journals.clear()
                } else {
                    journals.requireRecovery()
                }
            }
        }

        private suspend fun failureAfterUnexpectedError(
            original: RestoreOriginalState,
            swap: AttachmentSwap?,
        ): BackupOperationResult<BackupPreview> {
            if (swap == null) {
                journals.clear()
                return failure("恢复备份失败，当前数据未修改")
            }
            val databaseRestored = dataGateway.replace(original.database).isSuccess
            val settingsRestored = dependencies.settingsRepository.saveSettings(original.settings).isSuccess
            val attachmentsRestored = directories.rollback(swap).isSuccess
            return if (databaseRestored && settingsRestored && attachmentsRestored) {
                journals.clear()
                failure("恢复备份失败，当前数据未修改")
            } else {
                journals.requireRecovery()
                failure("恢复遇到严重问题，请重新启动后再检查数据", requiresRecovery = true)
            }
        }

        private suspend fun reconcileAfterRestore(settings: AiSettings): Boolean =
            dependencies.scheduleStateRepository
                .resetBaseline(com.worklogai.app.core.model.SummaryType.WEEKLY)
                .isSuccess &&
                dependencies.scheduleStateRepository
                    .resetBaseline(com.worklogai.app.core.model.SummaryType.MONTHLY)
                    .isSuccess &&
                dependencies.autoSummaryScheduler.applySettings(settings, enqueueImmediateCheck = false).isSuccess

        private companion object {
            const val SCHEDULER_WARNING = "数据已恢复，自动任务将在下次启动时重新协调"
        }
    }

internal class AttachmentRestoreDirectories(
    private val context: Context,
    private val operations: RestoreDirectoryOperations,
) {
    fun createStageDirectory(): File = operations.createStageDirectory(context.cacheDir)

    fun extractAttachments(
        archive: ParsedArchive,
        stageDirectory: File,
    ) {
        val stagedAttachments = File(stageDirectory, BackupArchiveContract.ATTACHMENTS_ROOT).apply { mkdirs() }
        ZipFile(archive.file).use { zip ->
            archive.payload.attachments.filter(BackupAttachment::fileIncluded).forEach { attachment ->
                val destination = BackupArchivePaths.stageFile(stagedAttachments, attachment.localPath)
                destination.parentFile?.mkdirs()
                val entry = zip.getEntry(BackupArchivePaths.attachmentEntryPath(attachment.localPath))
                requireNotNull(entry)
                zip.getInputStream(entry).use { input ->
                    BackupArchiveStreams.copyToFile(input, destination, BackupArchiveContract.MAX_ATTACHMENT_BYTES)
                }
            }
        }
    }

    fun swap(stageDirectory: File): Result<AttachmentSwap> =
        runCatching {
            val target = File(context.filesDir, BackupArchiveContract.ATTACHMENTS_ROOT)
            val previous = File(context.cacheDir, "restore-previous-${UUID.randomUUID()}")
            val staged = File(stageDirectory, BackupArchiveContract.ATTACHMENTS_ROOT)
            if (!staged.exists()) staged.mkdirs()
            if (target.exists()) operations.moveDirectory(target, previous)
            try {
                operations.moveDirectory(staged, target)
            } catch (error: IOException) {
                try {
                    if (previous.exists()) operations.moveDirectory(previous, target)
                } catch (_: IOException) {
                    throw AttachmentRollbackException()
                }
                throw error
            }
            AttachmentSwap(target, previous)
        }

    fun rollback(swap: AttachmentSwap): Result<Unit> =
        runCatching {
            val failed = File(context.cacheDir, "restore-failed-${UUID.randomUUID()}")
            if (swap.target.exists()) operations.moveDirectory(swap.target, failed)
            if (swap.previous.exists()) operations.moveDirectory(swap.previous, swap.target)
            cleanup(failed)
        }

    fun cleanup(directory: File) {
        operations.deleteRecursively(directory)
    }
}

internal data class AttachmentSwap(
    val target: File,
    val previous: File,
)

private data class RestoreOriginalState(
    val database: BackupDatabaseSnapshot,
    val settings: AiSettings,
)

private object BackupArchivePayloadMapper {
    fun toDatabaseSnapshot(payload: BackupPayload): BackupDatabaseSnapshot =
        BackupDatabaseSnapshot(
            payload.entries.map(BackupWorkEntry::toEntity),
            payload.blocks.map(BackupContentBlock::toEntity),
            payload.attachments.map(BackupAttachment::toEntity),
            payload.summaries.map(BackupWorkSummary::toEntity),
            payload.todos.map(BackupTodoItem::toEntity),
        )
}

private class AttachmentRollbackException : IOException()
