package com.worklogai.app.core.backup

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.worklogai.app.core.autosummary.AutoSummaryPeriod
import com.worklogai.app.core.autosummary.AutoSummaryScheduleState
import com.worklogai.app.core.autosummary.AutoSummaryScheduleStateRepository
import com.worklogai.app.core.autosummary.AutoSummaryScheduler
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.datastore.AiSettingsRepository
import com.worklogai.app.core.model.SummaryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RestoreJournalTest {
    private lateinit var context: Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cleanupRestoreArtifacts()
    }

    @After
    fun tearDown() {
        cleanupRestoreArtifacts()
    }

    @Test
    fun `file journal round trips only internal non-sensitive state`() {
        val store = FileRestoreJournalStore(context)
        val expected = journal(RestorePhase.DATABASE_REPLACED).copy(databaseReplaced = true)

        assertTrue(store.write(expected).isSuccess)
        assertEquals(expected, store.read().getOrThrow())
        val serialized = File(context.filesDir, "restore/restore_journal.json").readText()
        assertFalse(serialized.contains("content://"))
        assertFalse(serialized.contains("apiKey", ignoreCase = true))
    }

    @Test
    fun `file journal rejects unsafe directory names and corrupt content`() {
        val store = FileRestoreJournalStore(context)
        assertTrue(store.write(journal(RestorePhase.PREPARED).copy(stagingDirectoryName = "../escape")).isFailure)

        File(context.filesDir, "restore").mkdirs()
        File(context.filesDir, "restore/restore_journal.json").writeText("{")
        assertTrue(store.read().isFailure)
    }

    @Test
    fun `startup with no journal is a no-op`() =
        runBlocking {
            assertEquals(RestoreStartupResult.NothingToDo, coordinator(TransientRestoreJournalStore()).reconcile())
        }

    @Test
    fun `prepared journal removes staging without touching current attachments`() =
        runBlocking {
            val store = TransientRestoreJournalStore().also { it.write(journal(RestorePhase.PREPARED)) }
            val stage = File(context.cacheDir, STAGE_NAME).apply { mkdirs() }
            val target = File(context.filesDir, BackupArchiveContract.ATTACHMENTS_ROOT).apply { mkdirs() }
            File(target, "current.txt").writeText("current")

            assertEquals(RestoreStartupResult.Recovered, coordinator(store).reconcile())
            assertFalse(stage.exists())
            assertTrue(File(target, "current.txt").isFile)
            assertEquals(null, store.read().getOrThrow())
        }

    @Test
    fun `attachments switched journal restores old directory and is idempotent`() =
        runBlocking {
            val store =
                TransientRestoreJournalStore().also {
                    it.write(journal(RestorePhase.ATTACHMENTS_SWITCHED).copy(oldDirectoryName = OLD_NAME))
                }
            val old = File(context.cacheDir, OLD_NAME).apply { mkdirs() }
            File(old, "old.txt").writeText("old")
            val target = File(context.filesDir, BackupArchiveContract.ATTACHMENTS_ROOT).apply { mkdirs() }
            File(target, "new.txt").writeText("new")

            assertEquals(RestoreStartupResult.Recovered, coordinator(store).reconcile())
            assertTrue(File(target, "old.txt").isFile)
            assertFalse(File(target, "new.txt").exists())
            assertEquals(RestoreStartupResult.NothingToDo, coordinator(store).reconcile())
        }

    @Test
    fun `recovery-required journal preserves every attachment directory`() =
        runBlocking {
            val store =
                TransientRestoreJournalStore().also {
                    it.write(journal(RestorePhase.RECOVERY_REQUIRED).copy(oldDirectoryName = OLD_NAME))
                }
            val old = File(context.cacheDir, OLD_NAME).apply { mkdirs() }
            val target = File(context.filesDir, BackupArchiveContract.ATTACHMENTS_ROOT).apply { mkdirs() }

            assertEquals(RestoreStartupResult.RecoveryRequired, coordinator(store).reconcile())
            assertTrue(old.isDirectory)
            assertTrue(target.isDirectory)
            assertTrue(store.read().getOrThrow() != null)
        }

    @Test
    fun `completed journal retries scheduler and clears only after success`() =
        runBlocking {
            val store =
                TransientRestoreJournalStore().also {
                    it.write(journal(RestorePhase.COMPLETED).copy(schedulerPending = true))
                }
            val failingScheduler = FakeScheduler(fail = true)

            assertEquals(RestoreStartupResult.RecoveryRequired, coordinator(store, failingScheduler).reconcile())
            assertTrue(store.read().getOrThrow() != null)
            assertEquals(RestoreStartupResult.Recovered, coordinator(store, FakeScheduler()).reconcile())
            assertEquals(null, store.read().getOrThrow())
        }

    private fun coordinator(
        store: RestoreJournalStore,
        scheduler: AutoSummaryScheduler = FakeScheduler(),
    ): RestoreStartupRecoveryCoordinator =
        RestoreStartupRecoveryCoordinator(
            context,
            store,
            DefaultRestoreDirectoryOperations(),
            RestoreStartupDependencies(
                FakeSettingsRepository(),
                { scheduler },
                FakeScheduleStateRepository(),
            ),
            Dispatchers.Unconfined,
        )

    private fun journal(phase: RestorePhase): RestoreJournal =
        RestoreJournal(
            restoreId = RESTORE_ID,
            phase = phase,
            stagingDirectoryName = STAGE_NAME,
            createdAt = "2026-07-15T00:00:00Z",
        )

    private fun cleanupRestoreArtifacts() {
        File(context.filesDir, "restore").deleteRecursively()
        File(context.filesDir, BackupArchiveContract.ATTACHMENTS_ROOT).deleteRecursively()
        context.cacheDir
            .listFiles()
            .orEmpty()
            .filter { file -> file.name.startsWith("restore-") }
            .forEach(File::deleteRecursively)
    }

    private class FakeSettingsRepository : AiSettingsRepository {
        private val value = AiSettings(useMockProvider = true)
        override val settings: Flow<AiSettings> = flowOf(value)

        override suspend fun getSettings(): AiSettings = value

        override suspend fun saveSettings(settings: AiSettings): Result<Unit> = Result.success(Unit)
    }

    private class FakeScheduler(
        private val fail: Boolean = false,
    ) : AutoSummaryScheduler {
        override suspend fun applySettings(
            settings: AiSettings,
            enqueueImmediateCheck: Boolean,
        ): Result<Unit> = if (fail) Result.failure(IllegalStateException()) else Result.success(Unit)

        override suspend fun enqueueImmediateCheck(): Result<Unit> = Result.success(Unit)

        override suspend fun enqueueGeneration(
            period: AutoSummaryPeriod,
            settings: AiSettings,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun cancelAutomaticGeneration(type: SummaryType?): Result<Unit> = Result.success(Unit)
    }

    private class FakeScheduleStateRepository : AutoSummaryScheduleStateRepository {
        override val state: Flow<AutoSummaryScheduleState> = flowOf(AutoSummaryScheduleState(null, null, 1, null))

        override suspend fun getState(): Result<AutoSummaryScheduleState> = Result.success(stateValue())

        override suspend fun updateLastCheck(time: Instant): Result<Unit> = Result.success(Unit)

        override suspend fun markEvaluated(
            type: SummaryType,
            periodEnd: LocalDate,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun resetBaseline(type: SummaryType): Result<Unit> = Result.success(Unit)

        override suspend fun updateSchedulerVersion(version: Int): Result<Unit> = Result.success(Unit)

        private fun stateValue() = AutoSummaryScheduleState(null, null, 1, null)
    }

    private companion object {
        const val RESTORE_ID = "123e4567-e89b-12d3-a456-426614174000"
        const val STAGE_NAME = "restore-stage-$RESTORE_ID"
        const val OLD_NAME = "restore-previous-$RESTORE_ID"
    }
}
