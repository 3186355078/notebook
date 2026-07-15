package com.worklogai.app.core.database.repository

import android.app.Application
import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataValidationReason
import com.worklogai.app.core.database.FixedTimeProvider
import com.worklogai.app.core.database.createInMemoryDatabase
import com.worklogai.app.core.database.failureValue
import com.worklogai.app.core.database.successValue
import com.worklogai.app.core.model.GeneratedSummaryContent
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.WorkSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OfflineWorkSummaryRepositoryTest {
    private lateinit var database: com.worklogai.app.core.database.WorkLogDatabase
    private lateinit var repository: OfflineWorkSummaryRepository

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
        repository =
            OfflineWorkSummaryRepository(
                database = database,
                workSummaryDao = database.workSummaryDao(),
                timeProvider = FixedTimeProvider(Instant.parse("2026-07-12T08:00:00Z")),
                ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `save updates a period instead of creating duplicate and preserves user editing`() {
        runBlocking {
            val weekly =
                summary(
                    id = "weekly-1",
                    type = SummaryType.WEEKLY,
                    start = LocalDate.of(2026, 7, 6),
                    end = LocalDate.of(2026, 7, 12),
                )
            val saved = repository.saveSummary(weekly).successValue()
            val reSaved = repository.saveSummary(weekly.copy(id = "weekly-new", sourceHash = "changed")).successValue()
            assertEquals(saved.id, reSaved.id)
            assertEquals(
                1,
                repository
                    .observeSummaries(SummaryType.WEEKLY)
                    .first()
                    .successValue()
                    .size,
            )

            repository
                .saveGeneratedContent(
                    GeneratedSummaryContent(
                        summaryType = saved.summaryType,
                        periodStart = saved.periodStart,
                        periodEnd = saved.periodEnd,
                        sourceHash = "generated-hash",
                        aiProvider = "mock",
                        modelName = "test-model",
                        originalContent = "AI 初稿",
                        generatedAt = Instant.parse("2026-07-12T09:00:00Z"),
                    ),
                ).successValue()
            repository
                .updateEditedContent(
                    summaryType = saved.summaryType,
                    periodStart = saved.periodStart,
                    periodEnd = saved.periodEnd,
                    editedContent = "人工修改版",
                ).successValue()

            val observed =
                repository
                    .observeSummary(
                        SummaryType.WEEKLY,
                        LocalDate.of(2026, 7, 6),
                        LocalDate.of(2026, 7, 12),
                    ).first()
                    .successValue()!!
            assertEquals(SummaryStatus.SUCCESS, observed.status)
            assertEquals("AI 初稿", observed.originalContent)
            assertEquals("人工修改版", observed.displayContent)
        }
    }

    @Test
    fun `summary types remain separate and validation protects unsafe data`() {
        runBlocking {
            val monthly =
                summary(
                    id = "monthly-1",
                    type = SummaryType.MONTHLY,
                    start = LocalDate.of(2026, 7, 1),
                    end = LocalDate.of(2026, 7, 31),
                )
            repository.saveSummary(monthly).successValue()

            assertEquals(
                1,
                repository
                    .observeSummaries(SummaryType.MONTHLY)
                    .first()
                    .successValue()
                    .size,
            )
            assertEquals(
                0,
                repository
                    .observeSummaries(SummaryType.WEEKLY)
                    .first()
                    .successValue()
                    .size,
            )
            assertEquals(
                listOf(monthly.id),
                repository.getNotSuccessfulSummaries(SummaryType.MONTHLY).successValue().map { it.id },
            )
            assertNull(
                repository
                    .getSummary(
                        SummaryType.WEEKLY,
                        LocalDate.of(2026, 7, 6),
                        LocalDate.of(2026, 7, 12),
                    ).successValue(),
            )

            val invalidPeriod = repository.saveSummary(monthly.copy(periodStart = monthly.periodEnd.plusDays(1)))
            assertEquals(
                DataError.Validation(DataValidationReason.INVALID_DATE_RANGE),
                invalidPeriod.failureValue(),
            )
            val unsafeError =
                repository.updateStatus(
                    summaryType = monthly.summaryType,
                    periodStart = monthly.periodStart,
                    periodEnd = monthly.periodEnd,
                    status = SummaryStatus.FAILED,
                    errorMessage = "authorization token",
                )
            assertEquals(
                DataError.Validation(DataValidationReason.UNSAFE_ERROR_MESSAGE),
                unsafeError.failureValue(),
            )
        }
    }

    private fun summary(
        id: String,
        type: SummaryType,
        start: LocalDate,
        end: LocalDate,
    ) = WorkSummary(
        id = id,
        summaryType = type,
        periodStart = start,
        periodEnd = end,
        status = SummaryStatus.PENDING,
        sourceHash = "hash",
        aiProvider = null,
        modelName = null,
        originalContent = null,
        editedContent = null,
        errorMessage = null,
        createdAt = Instant.parse("2026-07-12T08:00:00Z"),
        updatedAt = Instant.parse("2026-07-12T08:00:00Z"),
        generatedAt = null,
    )
}
