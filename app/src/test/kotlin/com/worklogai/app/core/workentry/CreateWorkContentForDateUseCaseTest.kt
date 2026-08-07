package com.worklogai.app.core.workentry

import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.result.DataValidationReason
import com.worklogai.app.core.common.time.LocalDateProvider
import com.worklogai.app.core.model.CreatedWorkContent
import com.worklogai.app.core.model.NewWorkContent
import com.worklogai.app.core.repository.WorkEntryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CreateWorkContentForDateUseCaseTest {
    private val repository = mockk<WorkEntryRepository>()
    private val today = LocalDate.of(2026, 8, 7)
    private val useCase = CreateWorkContentForDateUseCase(repository, FixedDateProvider(today))

    @Test
    fun `past date and today delegate content creation`() =
        runTest {
            val past = LocalDate.of(2026, 8, 4)
            val todayContent = NewWorkContent.Text("今天补录")
            val pastContent = NewWorkContent.Text("历史补录")
            val created = mockk<CreatedWorkContent>()
            coEvery { repository.createContentForDate(past, pastContent) } returns DataResult.Success(created)
            coEvery { repository.createContentForDate(today, todayContent) } returns DataResult.Success(created)

            assertEquals(DataResult.Success(created), useCase(past, pastContent))
            assertEquals(DataResult.Success(created), useCase(today, todayContent))
            coVerify(exactly = 1) { repository.createContentForDate(past, pastContent) }
            coVerify(exactly = 1) { repository.createContentForDate(today, todayContent) }
        }

    @Test
    fun `future date is rejected before repository access`() =
        runTest {
            val result = useCase(today.plusDays(1), NewWorkContent.Text("未来内容"))

            assertEquals(
                DataResult.Failure(DataError.Validation(DataValidationReason.FUTURE_WORK_ENTRY)),
                result,
            )
            coVerify(exactly = 0) { repository.createContentForDate(any(), any()) }
        }

    @Test
    fun `leap day and cross-year historical dates remain valid`() =
        runTest {
            val dates = listOf(LocalDate.of(2024, 2, 29), LocalDate.of(2025, 12, 31))
            val created = mockk<CreatedWorkContent>()
            dates.forEach { date ->
                val content = NewWorkContent.Text(date.toString())
                coEvery { repository.createContentForDate(date, content) } returns DataResult.Success(created)
                assertEquals(DataResult.Success(created), useCase(date, content))
            }
            coVerify(exactly = dates.size) { repository.createContentForDate(any(), any()) }
        }
}

private class FixedDateProvider(
    private val date: LocalDate,
) : LocalDateProvider {
    override fun today(): LocalDate = date

    override fun zoneId(): ZoneId = ZoneId.of("UTC")
}
