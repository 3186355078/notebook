package com.worklogai.app.core.autosummary

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.worklogai.app.core.model.SummaryType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PreferencesAutoSummaryScheduleStateRepositoryTest {
    @Test
    fun `evaluation boundaries are persistent monotonic and resettable by summary type`() =
        runBlocking {
            val repository = repository()

            assertSuccessful(repository.markEvaluated(SummaryType.WEEKLY, LocalDate.of(2026, 7, 19)))
            assertSuccessful(repository.markEvaluated(SummaryType.WEEKLY, LocalDate.of(2026, 7, 12)))
            assertSuccessful(repository.markEvaluated(SummaryType.MONTHLY, LocalDate.of(2026, 6, 30)))

            assertEquals(LocalDate.of(2026, 7, 19), repository.state.first().lastWeeklyEvaluatedEnd)
            assertEquals(LocalDate.of(2026, 6, 30), repository.state.first().lastMonthlyEvaluatedEnd)

            assertSuccessful(repository.resetBaseline(SummaryType.WEEKLY))
            assertNull(repository.getState().getOrThrow().lastWeeklyEvaluatedEnd)
            assertEquals(LocalDate.of(2026, 6, 30), repository.getState().getOrThrow().lastMonthlyEvaluatedEnd)
        }

    @Test
    fun `last check and scheduler version are stored in schedule state`() =
        runBlocking {
            val repository = repository()
            val checkedAt = Instant.parse("2026-07-20T01:02:03Z")

            assertSuccessful(repository.updateLastCheck(checkedAt))
            assertSuccessful(repository.updateSchedulerVersion(7))

            val state = repository.getState().getOrThrow()
            assertEquals(checkedAt, state.lastCheckAt)
            assertEquals(7, state.schedulerVersion)
        }

    private fun repository(): PreferencesAutoSummaryScheduleStateRepository =
        PreferencesAutoSummaryScheduleStateRepository(InMemoryPreferencesDataStore())

    private fun assertSuccessful(result: Result<Unit>) {
        val error = result.exceptionOrNull()
        assertTrue("$error, cause=${error?.cause}", result.isSuccess)
    }
}

private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val mutableData = MutableStateFlow<Preferences>(emptyPreferences())

    override val data = mutableData

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(mutableData.value)
        mutableData.value = updated
        return updated
    }
}
