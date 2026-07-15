package com.worklogai.app.core.autosummary

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.worklogai.app.core.datastore.AutoSummaryScheduleDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

interface AutoSummaryScheduleStateRepository {
    val state: Flow<AutoSummaryScheduleState>

    suspend fun getState(): Result<AutoSummaryScheduleState>

    suspend fun updateLastCheck(time: Instant): Result<Unit>

    suspend fun markEvaluated(
        type: com.worklogai.app.core.model.SummaryType,
        periodEnd: LocalDate,
    ): Result<Unit>

    suspend fun resetBaseline(type: com.worklogai.app.core.model.SummaryType): Result<Unit>

    suspend fun updateSchedulerVersion(version: Int): Result<Unit>
}

class PreferencesAutoSummaryScheduleStateRepository
    @Inject
    constructor(
        @AutoSummaryScheduleDataStore private val dataStore: DataStore<Preferences>,
    ) : AutoSummaryScheduleStateRepository {
        override val state: Flow<AutoSummaryScheduleState> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }.map(::toScheduleState)

        override suspend fun getState(): Result<AutoSummaryScheduleState> = guarded { state.first() }

        override suspend fun updateLastCheck(time: Instant): Result<Unit> =
            edit { preferences -> preferences[LAST_CHECK_AT] = time.toEpochMilli() }

        override suspend fun markEvaluated(
            type: com.worklogai.app.core.model.SummaryType,
            periodEnd: LocalDate,
        ): Result<Unit> =
            edit { preferences ->
                val key = type.evaluatedEndKey()
                val existing = preferences[key]?.let(::parseDateOrNull)
                if (existing == null || periodEnd > existing) {
                    preferences[key] = periodEnd.toString()
                }
            }

        override suspend fun resetBaseline(type: com.worklogai.app.core.model.SummaryType): Result<Unit> =
            edit { preferences -> preferences.remove(type.evaluatedEndKey()) }

        override suspend fun updateSchedulerVersion(version: Int): Result<Unit> =
            edit { preferences -> preferences[SCHEDULER_VERSION] = version.toString() }

        private suspend fun edit(block: suspend (MutablePreferences) -> Unit): Result<Unit> =
            guarded { dataStore.edit(block) }

        private suspend fun <T> guarded(block: suspend () -> T): Result<T> =
            try {
                Result.success(block())
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                Result.failure(AutoSummaryScheduleStateException(error))
            } catch (error: IllegalStateException) {
                Result.failure(AutoSummaryScheduleStateException(error))
            }
    }

class AutoSummaryScheduleStateException(
    cause: Throwable? = null,
) : IllegalStateException("Unable to access automatic summary scheduling state.", cause)

private fun toScheduleState(preferences: Preferences): AutoSummaryScheduleState =
    AutoSummaryScheduleState(
        lastWeeklyEvaluatedEnd = preferences[WEEKLY_EVALUATED_END]?.let(::parseDateOrNull),
        lastMonthlyEvaluatedEnd = preferences[MONTHLY_EVALUATED_END]?.let(::parseDateOrNull),
        schedulerVersion = preferences[SCHEDULER_VERSION]?.toIntOrNull() ?: AUTO_SUMMARY_SCHEDULER_VERSION,
        lastCheckAt = preferences[LAST_CHECK_AT]?.let(Instant::ofEpochMilli),
    )

private fun parseDateOrNull(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

private fun com.worklogai.app.core.model.SummaryType.evaluatedEndKey(): Preferences.Key<String> =
    when (this) {
        com.worklogai.app.core.model.SummaryType.WEEKLY -> WEEKLY_EVALUATED_END
        com.worklogai.app.core.model.SummaryType.MONTHLY -> MONTHLY_EVALUATED_END
    }

private val WEEKLY_EVALUATED_END = stringPreferencesKey("auto_summary_last_weekly_evaluated_end")
private val MONTHLY_EVALUATED_END = stringPreferencesKey("auto_summary_last_monthly_evaluated_end")
private val SCHEDULER_VERSION = stringPreferencesKey("auto_summary_scheduler_version")
private val LAST_CHECK_AT = longPreferencesKey("auto_summary_last_check_at")
