package com.worklogai.app.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worklogai.app.core.backup.RestoreJournal
import com.worklogai.app.core.backup.RestorePhase
import com.worklogai.app.core.backup.RestoreStartupResult
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DeviceRestoreJournalIntegrationTest {
    @Test
    fun restoreJournalHandlesColdStartFixtures() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dependencies = EntryPointAccessors.fromApplication(context, Stage9TestEntryPoint::class.java)
            when (InstrumentationRegistry.getArguments().getString(MODE_ARGUMENT).orEmpty()) {
                PREPARE_SWITCHED -> prepareSwitched(context, dependencies)
                VERIFY_SWITCHED -> verifySwitched(context, dependencies)
                else -> verifySelfContainedMatrix(context, dependencies)
            }
        }

    private suspend fun verifySelfContainedMatrix(
        context: android.content.Context,
        dependencies: Stage9TestEntryPoint,
    ) {
        cleanupTestFixtures(context, dependencies)
        try {
            prepareSwitched(context, dependencies)
            assertEquals(
                RestoreStartupResult.Recovered,
                dependencies.restoreStartupRecoveryCoordinator().reconcile(),
            )
            verifySwitched(context, dependencies)

            val restoreId = UUID.randomUUID().toString()
            val staging = context.cacheDir.resolve("restore-stage-$restoreId")
            val old = context.cacheDir.resolve("restore-previous-$restoreId")
            staging.mkdirs()
            old.mkdirs()
            staging.resolve(STAGING_MARKER).writeText("synthetic")
            old.resolve(PREVIOUS_MARKER).writeText("synthetic")
            dependencies
                .restoreJournalStore()
                .write(
                    journal(restoreId, RestorePhase.RECOVERY_REQUIRED),
                ).getOrThrow()

            assertEquals(
                RestoreStartupResult.RecoveryRequired,
                dependencies.restoreStartupRecoveryCoordinator().reconcile(),
            )
            assertTrue(staging.exists())
            assertTrue(old.exists())
            assertNotNull(dependencies.restoreJournalStore().read().getOrThrow())
        } finally {
            cleanupTestFixtures(context, dependencies)
        }
    }

    private fun prepareSwitched(
        context: android.content.Context,
        dependencies: Stage9TestEntryPoint,
    ) {
        val restoreId = UUID.randomUUID().toString()
        val target = context.filesDir.resolve(ATTACHMENTS_DIRECTORY)
        val staging = context.cacheDir.resolve("restore-stage-$restoreId")
        val old = context.cacheDir.resolve("restore-previous-$restoreId")
        staging.deleteRecursively()
        old.deleteRecursively()
        assertTrue(target.exists() || target.mkdirs())
        target.resolve(OLD_MARKER).apply {
            parentFile?.mkdirs()
            writeText("synthetic-original")
        }
        assertTrue(target.renameTo(old))
        assertTrue(target.mkdirs())
        target.resolve(NEW_MARKER).apply {
            parentFile?.mkdirs()
            writeText("synthetic-new")
        }
        assertTrue(staging.mkdirs())
        staging.resolve("stage9-staging-marker").writeText("synthetic")
        dependencies
            .restoreJournalStore()
            .write(
                journal(restoreId, RestorePhase.ATTACHMENTS_SWITCHED),
            ).getOrThrow()
    }

    private fun verifySwitched(
        context: android.content.Context,
        dependencies: Stage9TestEntryPoint,
    ) {
        val target = context.filesDir.resolve(ATTACHMENTS_DIRECTORY)
        assertTrue(target.resolve(OLD_MARKER).exists())
        assertFalse(target.resolve(NEW_MARKER).exists())
        assertEquals(null, dependencies.restoreJournalStore().read().getOrThrow())
        assertFalse(
            context.cacheDir.listFiles().orEmpty().any {
                it.name.startsWith("restore-stage-") || it.name.startsWith("restore-previous-")
            },
        )
        assertTrue(target.resolve(OLD_MARKER).delete())
    }

    private fun journal(
        restoreId: String,
        phase: RestorePhase,
    ): RestoreJournal =
        RestoreJournal(
            restoreId = restoreId,
            phase = phase,
            stagingDirectoryName = "restore-stage-$restoreId",
            oldDirectoryName = "restore-previous-$restoreId",
            databaseReplaced =
                phase == RestorePhase.DATABASE_REPLACED ||
                    phase == RestorePhase.SETTINGS_REPLACED ||
                    phase == RestorePhase.COMPLETED ||
                    phase == RestorePhase.RECOVERY_REQUIRED,
            settingsReplaced =
                phase == RestorePhase.SETTINGS_REPLACED ||
                    phase == RestorePhase.COMPLETED ||
                    phase == RestorePhase.RECOVERY_REQUIRED,
            schedulerPending = false,
            createdAt = Instant.now().toString(),
        )

    private fun cleanupTestFixtures(
        context: android.content.Context,
        dependencies: Stage9TestEntryPoint,
    ) {
        val target = context.filesDir.resolve(ATTACHMENTS_DIRECTORY)
        context.cacheDir
            .listFiles()
            .orEmpty()
            .filter { it.name.startsWith("restore-previous-") }
            .forEach { previous ->
                if (previous.resolve(OLD_MARKER).exists()) {
                    target.deleteRecursively()
                    assertTrue(previous.renameTo(target))
                } else if (previous.resolve(PREVIOUS_MARKER).exists()) {
                    assertTrue(previous.deleteRecursively())
                }
            }
        context.cacheDir
            .listFiles()
            .orEmpty()
            .filter { it.name.startsWith("restore-stage-") }
            .filter { it.resolve(STAGING_MARKER).exists() }
            .forEach { assertTrue(it.deleteRecursively()) }
        target.resolve(OLD_MARKER).delete()
        target.resolve(NEW_MARKER).delete()
        assertTrue(dependencies.restoreJournalStore().clear().isSuccess)
    }

    private companion object {
        const val MODE_ARGUMENT = "journalFixture"
        const val PREPARE_SWITCHED = "prepare-switched"
        const val VERIFY_SWITCHED = "verify-switched"
        const val ATTACHMENTS_DIRECTORY = "attachments"
        const val OLD_MARKER = "images/stage9-journal-old-marker.txt"
        const val NEW_MARKER = "images/stage9-journal-new-marker.txt"
        const val STAGING_MARKER = "stage9-staging-marker"
        const val PREVIOUS_MARKER = "stage9-old-marker"
    }
}
