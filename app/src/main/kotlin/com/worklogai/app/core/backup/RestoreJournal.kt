package com.worklogai.app.core.backup

import android.content.Context
import com.worklogai.app.core.autosummary.AutoSummaryScheduleStateRepository
import com.worklogai.app.core.autosummary.AutoSummaryScheduler
import com.worklogai.app.core.common.di.IoDispatcher
import com.worklogai.app.core.datastore.AiSettingsRepository
import com.worklogai.app.core.model.SummaryType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Serializable
internal data class RestoreJournal(
    val restoreId: String,
    val phase: RestorePhase,
    val stagingDirectoryName: String,
    val oldDirectoryName: String? = null,
    val databaseReplaced: Boolean = false,
    val settingsReplaced: Boolean = false,
    val schedulerPending: Boolean = false,
    val createdAt: String,
)

@Serializable
internal enum class RestorePhase {
    PREPARED,
    ATTACHMENTS_SWITCHED,
    DATABASE_REPLACED,
    SETTINGS_REPLACED,
    COMPLETED,
    RECOVERY_REQUIRED,
}

internal interface RestoreJournalStore {
    fun read(): Result<RestoreJournal?>

    fun write(journal: RestoreJournal): Result<Unit>

    fun clear(): Result<Unit>
}

internal class TransientRestoreJournalStore : RestoreJournalStore {
    private var journal: RestoreJournal? = null

    override fun read(): Result<RestoreJournal?> = Result.success(journal)

    override fun write(journal: RestoreJournal): Result<Unit> = Result.success(Unit).also { this.journal = journal }

    override fun clear(): Result<Unit> = Result.success(Unit).also { journal = null }
}

internal class RestoreJournalLifecycle(
    private val store: RestoreJournalStore,
) {
    fun begin(stageDirectory: File): RestoreJournal =
        RestoreJournal(
            restoreId = UUID.randomUUID().toString(),
            phase = RestorePhase.PREPARED,
            stagingDirectoryName = stageDirectory.name,
            createdAt = Instant.now().toString(),
        ).also { store.write(it).getOrThrow() }

    fun update(
        journal: RestoreJournal,
        phase: RestorePhase,
        oldDirectoryName: String? = journal.oldDirectoryName,
    ): RestoreJournal =
        journal
            .copy(
                phase = phase,
                oldDirectoryName = oldDirectoryName,
                databaseReplaced = journal.databaseReplaced || phase == RestorePhase.DATABASE_REPLACED,
                settingsReplaced = journal.settingsReplaced || phase == RestorePhase.SETTINGS_REPLACED,
            ).also { store.write(it).getOrThrow() }

    fun finish(
        journal: RestoreJournal,
        schedulerPending: Boolean,
    ) {
        if (schedulerPending) {
            store.write(journal.copy(phase = RestorePhase.COMPLETED, schedulerPending = true)).getOrThrow()
        } else {
            store.clear().getOrThrow()
        }
    }

    fun clear() {
        store.clear()
    }

    fun requireRecovery() {
        store.read().getOrNull()?.let { journal ->
            journal?.let { store.write(it.copy(phase = RestorePhase.RECOVERY_REQUIRED)) }
        }
    }
}

@Singleton
internal class FileRestoreJournalStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : RestoreJournalStore {
        private val directory = File(context.filesDir, JOURNAL_DIRECTORY)
        private val journalFile = File(directory, JOURNAL_FILE)

        override fun read(): Result<RestoreJournal?> =
            runCatching {
                if (!journalFile.exists()) return@runCatching null
                backupJson.decodeFromString<RestoreJournal>(journalFile.readText()).also(::validate)
            }

        override fun write(journal: RestoreJournal): Result<Unit> =
            runCatching {
                validate(journal)
                check(directory.exists() || directory.mkdirs())
                val temporary = File(directory, "$JOURNAL_FILE.tmp")
                temporary.writeText(backupJson.encodeToString(journal))
                check(temporary.renameTo(journalFile) || replaceJournal(temporary))
            }

        override fun clear(): Result<Unit> = runCatching { check(!journalFile.exists() || journalFile.delete()) }

        private fun replaceJournal(temporary: File): Boolean =
            (!journalFile.exists() || journalFile.delete()) && temporary.renameTo(journalFile)

        private fun validate(journal: RestoreJournal) {
            UUID.fromString(journal.restoreId)
            Instant.parse(journal.createdAt)
            require(journal.stagingDirectoryName.isRestoreDirectoryName(STAGE_PREFIX))
            require(
                journal.oldDirectoryName == null || journal.oldDirectoryName.isRestoreDirectoryName(PREVIOUS_PREFIX),
            )
        }

        private fun String.isRestoreDirectoryName(prefix: String): Boolean =
            startsWith(prefix) &&
                length <= MAX_DIRECTORY_NAME_LENGTH &&
                none { it == '/' || it == '\\' || it.code < CONTROL_CHARACTER_LIMIT }

        private companion object {
            const val JOURNAL_DIRECTORY = "restore"
            const val JOURNAL_FILE = "restore_journal.json"
            const val STAGE_PREFIX = "restore-stage-"
            const val PREVIOUS_PREFIX = "restore-previous-"
            const val MAX_DIRECTORY_NAME_LENGTH = 100
            const val CONTROL_CHARACTER_LIMIT = 0x20
        }
    }

internal sealed interface RestoreStartupResult {
    data object NothingToDo : RestoreStartupResult

    data object Recovered : RestoreStartupResult

    data object RecoveryRequired : RestoreStartupResult
}

internal class RestoreStartupDependencies
    @Inject
    constructor(
        val settingsRepository: AiSettingsRepository,
        val scheduler: Provider<AutoSummaryScheduler>,
        val scheduleStateRepository: AutoSummaryScheduleStateRepository,
    )

@Singleton
internal class RestoreStartupRecoveryCoordinator
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val journalStore: RestoreJournalStore,
        private val directoryOperations: RestoreDirectoryOperations,
        private val dependencies: RestoreStartupDependencies,
        @IoDispatcher dispatcher: CoroutineDispatcher,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)

        fun reconcileInBackground() {
            scope.launch { reconcile() }
        }

        suspend fun reconcile(): RestoreStartupResult {
            val journalResult = journalStore.read()
            val journal = journalResult.getOrNull()
            return if (journalResult.isFailure) {
                RestoreStartupResult.RecoveryRequired
            } else if (journal == null) {
                RestoreStartupResult.NothingToDo
            } else {
                reconcile(journal)
            }
        }

        private suspend fun reconcile(journal: RestoreJournal): RestoreStartupResult =
            when (journal.phase) {
                RestorePhase.PREPARED -> cleanupPrepared(journal)
                RestorePhase.ATTACHMENTS_SWITCHED -> rollbackAttachments(journal)
                RestorePhase.DATABASE_REPLACED,
                RestorePhase.RECOVERY_REQUIRED,
                -> RestoreStartupResult.RecoveryRequired
                RestorePhase.SETTINGS_REPLACED,
                RestorePhase.COMPLETED,
                -> finishRestoredState(journal)
            }

        private fun cleanupPrepared(journal: RestoreJournal): RestoreStartupResult {
            val staging = journal.cacheDirectory(journal.stagingDirectoryName)
            return if (directoryOperations.deleteRecursively(staging) && journalStore.clear().isSuccess) {
                RestoreStartupResult.Recovered
            } else {
                RestoreStartupResult.RecoveryRequired
            }
        }

        private fun rollbackAttachments(journal: RestoreJournal): RestoreStartupResult {
            val oldName = journal.oldDirectoryName
            return if (oldName == null || !journal.cacheDirectory(oldName).exists()) {
                RestoreStartupResult.RecoveryRequired
            } else {
                rollbackAttachments(journal, oldName)
            }
        }

        private fun rollbackAttachments(
            journal: RestoreJournal,
            oldName: String,
        ): RestoreStartupResult =
            runCatching {
                val target = File(context.filesDir, BackupArchiveContract.ATTACHMENTS_ROOT)
                val failed = journal.cacheDirectory("restore-stage-${journal.restoreId}-failed")
                if (target.exists()) directoryOperations.moveDirectory(target, failed)
                directoryOperations.moveDirectory(journal.cacheDirectory(oldName), target)
                directoryOperations.deleteRecursively(failed)
                directoryOperations.deleteRecursively(journal.cacheDirectory(journal.stagingDirectoryName))
                journalStore.clear().getOrThrow()
                RestoreStartupResult.Recovered
            }.getOrElse { RestoreStartupResult.RecoveryRequired }

        private suspend fun finishRestoredState(journal: RestoreJournal): RestoreStartupResult =
            try {
                val settings = dependencies.settingsRepository.getSettings()
                val reconciled =
                    dependencies.scheduleStateRepository.resetBaseline(SummaryType.WEEKLY).isSuccess &&
                        dependencies.scheduleStateRepository.resetBaseline(SummaryType.MONTHLY).isSuccess &&
                        dependencies.scheduler
                            .get()
                            .applySettings(settings, enqueueImmediateCheck = false)
                            .isSuccess
                if (!reconciled) {
                    RestoreStartupResult.RecoveryRequired
                } else {
                    journal.oldDirectoryName?.let { oldName ->
                        directoryOperations.deleteRecursively(journal.cacheDirectory(oldName))
                    }
                    directoryOperations.deleteRecursively(journal.cacheDirectory(journal.stagingDirectoryName))
                    if (journalStore.clear().isSuccess) {
                        RestoreStartupResult.Recovered
                    } else {
                        RestoreStartupResult.RecoveryRequired
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IllegalStateException) {
                RestoreStartupResult.RecoveryRequired
            }

        private fun RestoreJournal.cacheDirectory(name: String): File = File(context.cacheDir, name)
    }
