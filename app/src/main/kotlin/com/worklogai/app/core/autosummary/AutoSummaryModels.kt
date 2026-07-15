package com.worklogai.app.core.autosummary

import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.SummaryType
import java.time.Instant
import java.time.LocalDate

data class AutoSummarySettings(
    val weeklyEnabled: Boolean = false,
    val monthlyEnabled: Boolean = false,
    val notifyOnCompletion: Boolean = false,
    val allowMobileNetwork: Boolean = false,
    val useMockProvider: Boolean = true,
) {
    val isEnabled: Boolean get() = weeklyEnabled || monthlyEnabled

    fun isEnabled(type: SummaryType): Boolean =
        when (type) {
            SummaryType.WEEKLY -> weeklyEnabled
            SummaryType.MONTHLY -> monthlyEnabled
        }
}

data class AutoSummaryScheduleState(
    val lastWeeklyEvaluatedEnd: LocalDate? = null,
    val lastMonthlyEvaluatedEnd: LocalDate? = null,
    val schedulerVersion: Int = AUTO_SUMMARY_SCHEDULER_VERSION,
    val lastCheckAt: Instant? = null,
)

data class AutoSummaryPeriod(
    val summaryType: SummaryType,
    val period: DateRange,
)

fun AiSettings.toAutoSummarySettings(): AutoSummarySettings =
    AutoSummarySettings(
        weeklyEnabled = autoWeeklySummaryEnabled,
        monthlyEnabled = autoMonthlySummaryEnabled,
        notifyOnCompletion = notifyOnAutoSummaryCompletion,
        allowMobileNetwork = allowMobileNetwork,
        useMockProvider = useMockProvider,
    )

const val AUTO_SUMMARY_SCHEDULER_VERSION = 1
const val MAX_WEEKLY_CATCH_UP_PERIODS = 4
const val MAX_MONTHLY_CATCH_UP_PERIODS = 2
const val MAX_AUTO_SUMMARY_PERIODS_PER_CHECK = 3
const val MAX_AUTO_SUMMARY_RUN_ATTEMPTS = 3
