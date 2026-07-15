package com.worklogai.app.core.backup

import androidx.room.withTransaction
import com.worklogai.app.core.common.di.IoDispatcher
import com.worklogai.app.core.database.WorkLogDatabase
import com.worklogai.app.core.database.dao.AttachmentDao
import com.worklogai.app.core.database.dao.ContentBlockDao
import com.worklogai.app.core.database.dao.WorkEntryDao
import com.worklogai.app.core.database.dao.WorkSummaryBackupDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal interface BackupDataGateway {
    suspend fun snapshot(): Result<BackupDatabaseSnapshot>

    suspend fun replace(snapshot: BackupDatabaseSnapshot): Result<Unit>
}

internal enum class RestoreDatabaseCheckpoint {
    AFTER_DELETE_OLD_DATA,
    AFTER_INSERT_WORK_ENTRIES,
    AFTER_INSERT_CONTENT_BLOCKS,
    AFTER_INSERT_ATTACHMENTS,
    AFTER_INSERT_WORK_SUMMARIES,
    BEFORE_TRANSACTION_COMPLETE,
}

internal fun interface RestoreDatabaseFailureInjector {
    fun onCheckpoint(checkpoint: RestoreDatabaseCheckpoint)
}

internal class NoOpRestoreDatabaseFailureInjector
    @Inject
    constructor() : RestoreDatabaseFailureInjector {
        override fun onCheckpoint(checkpoint: RestoreDatabaseCheckpoint) = Unit
    }

internal class BackupDatabaseDaos
    @Inject
    constructor(
        val workEntries: WorkEntryDao,
        val contentBlocks: ContentBlockDao,
        val attachments: AttachmentDao,
        val summaries: WorkSummaryBackupDao,
    )

internal class RoomBackupDataGateway
    @Inject
    constructor(
        private val database: WorkLogDatabase,
        private val daos: BackupDatabaseDaos,
        private val failureInjector: RestoreDatabaseFailureInjector,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : BackupDataGateway {
        override suspend fun snapshot(): Result<BackupDatabaseSnapshot> =
            guarded {
                database.withTransaction {
                    BackupDatabaseSnapshot(
                        entries = daos.workEntries.getAllIncludingDeleted(),
                        blocks = daos.contentBlocks.getAll(),
                        attachments = daos.attachments.getAll(),
                        summaries = daos.summaries.getAll(),
                    )
                }
            }

        override suspend fun replace(snapshot: BackupDatabaseSnapshot): Result<Unit> =
            guarded {
                database.withTransaction {
                    daos.attachments.deleteAll()
                    daos.contentBlocks.deleteAll()
                    daos.workEntries.deleteAll()
                    daos.summaries.deleteAll()
                    failureInjector.onCheckpoint(RestoreDatabaseCheckpoint.AFTER_DELETE_OLD_DATA)
                    daos.workEntries.insertAll(snapshot.entries)
                    failureInjector.onCheckpoint(RestoreDatabaseCheckpoint.AFTER_INSERT_WORK_ENTRIES)
                    daos.contentBlocks.insertAll(snapshot.blocks)
                    failureInjector.onCheckpoint(RestoreDatabaseCheckpoint.AFTER_INSERT_CONTENT_BLOCKS)
                    daos.attachments.insertAll(snapshot.attachments)
                    failureInjector.onCheckpoint(RestoreDatabaseCheckpoint.AFTER_INSERT_ATTACHMENTS)
                    daos.summaries.insertAll(snapshot.summaries)
                    failureInjector.onCheckpoint(RestoreDatabaseCheckpoint.AFTER_INSERT_WORK_SUMMARIES)
                    failureInjector.onCheckpoint(RestoreDatabaseCheckpoint.BEFORE_TRANSACTION_COMPLETE)
                }
            }

        private suspend fun <T> guarded(block: suspend () -> T): Result<T> =
            withContext(ioDispatcher) {
                try {
                    Result.success(block())
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    Result.failure(BackupStorageException())
                }
            }
    }

internal class BackupStorageException : IllegalStateException("Unable to access backup data.")
