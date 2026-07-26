package com.worklogai.app.feature.history

import androidx.lifecycle.SavedStateHandle
import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.history.DefaultWorkPeriodCalculator
import com.worklogai.app.core.history.WorkEntrySummary
import com.worklogai.app.core.history.WorkHistoryPage
import com.worklogai.app.core.history.WorkHistoryRepository
import com.worklogai.app.feature.editor.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeWorkHistoryRepository
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        repository = FakeWorkHistoryRepository()
        viewModel = createViewModel(repository)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
    }

    @Test
    fun `initialization loads recent entries using initial page size`() {
        repository.recentPages[0] = page(7)
        repository.recentRequests.clear()
        viewModel = createViewModel(repository)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(HistoryMode.RECENT, viewModel.uiState.value.mode)
        assertEquals(30, repository.recentRequests.single().first)
        assertEquals(
            listOf(LocalDate.of(2026, 7, 7)),
            viewModel.uiState.value.items
                .map { it.date },
        )
    }

    @Test
    fun `mode and period navigation query inclusive natural week range`() {
        viewModel.onAction(HistoryAction.ChangeMode(HistoryMode.WEEK))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            LocalDate.of(2026, 7, 6) to LocalDate.of(2026, 7, 12),
            repository.rangeRequests.last(),
        )

        viewModel.onAction(HistoryAction.PreviousPeriod)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            LocalDate.of(2026, 6, 29) to LocalDate.of(2026, 7, 5),
            repository.rangeRequests.last(),
        )
    }

    @Test
    fun `selecting a day and month mode use their full inclusive ranges`() {
        viewModel.onAction(HistoryAction.SelectDate(LocalDate.of(2026, 7, 8)))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            LocalDate.of(2026, 7, 8) to LocalDate.of(2026, 7, 8),
            repository.rangeRequests.last(),
        )

        viewModel.onAction(HistoryAction.ChangeMode(HistoryMode.MONTH))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            LocalDate.of(2026, 7, 1) to LocalDate.of(2026, 7, 31),
            repository.rangeRequests.last(),
        )
    }

    @Test
    fun `next current month does not request a future range`() {
        viewModel.onAction(HistoryAction.ChangeMode(HistoryMode.MONTH))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        val requestCount = repository.rangeRequests.size

        viewModel.onAction(HistoryAction.NextPeriod)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(requestCount, repository.rangeRequests.size)
    }

    @Test
    fun `search debounces and only evaluates latest normalized query`() {
        viewModel.onAction(HistoryAction.SearchQueryChanged("  First "))
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(299)
        assertTrue(repository.searchRequests.isEmpty())

        viewModel.onAction(HistoryAction.SearchQueryChanged("\u767b\u5f55"))
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(300)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("\u767b\u5f55"), repository.searchRequests.map { it.first })
    }

    @Test
    fun `new search cancels a slow older request`() {
        repository.searchDelayMillis["old"] = 1_000L
        repository.searchPages["new" to 0] = page(6)

        viewModel.onAction(HistoryAction.SearchQueryChanged("old"))
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(300)
        viewModel.onAction(HistoryAction.SearchQueryChanged("new"))
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(300)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals("new", viewModel.uiState.value.query)
        assertEquals(
            listOf(LocalDate.of(2026, 7, 6)),
            viewModel.uiState.value.items
                .map { it.date },
        )
    }

    @Test
    fun `load more appends distinct entries and resets on mode change`() {
        repository.recentPages[0] = page(5, nextOffset = 30)
        repository.recentPages[30] =
            WorkHistoryPage(listOf(summary(5), summary(4)), nextOffset = null)
        viewModel = createViewModel(repository)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAction(HistoryAction.LoadMore)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 4)),
            viewModel.uiState.value.items
                .map { it.date },
        )
        assertFalse(viewModel.uiState.value.canLoadMore)

        viewModel.onAction(HistoryAction.ChangeMode(HistoryMode.MONTH))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.canLoadMore)
    }

    @Test
    fun `future day can be selected for todo planning`() {
        val before = viewModel.uiState.value.selectedDate
        val future = before.plusDays(1)

        viewModel.onAction(HistoryAction.SelectDate(future))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(future, viewModel.uiState.value.selectedDate)
        assertEquals(future to future, repository.rangeRequests.last())
    }

    @Test
    fun `failure retains state and retry loads again`() {
        repository.failRecent = true
        viewModel = createViewModel(repository)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals("\u5386\u53f2\u8bb0\u5f55\u52a0\u8f7d\u5931\u8d25", viewModel.uiState.value.errorMessage)

        repository.failRecent = false
        repository.recentPages[0] = page(3)
        viewModel.onAction(HistoryAction.Retry)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(LocalDate.of(2026, 7, 3)),
            viewModel.uiState.value.items
                .map { it.date },
        )
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    private fun createViewModel(repository: FakeWorkHistoryRepository) =
        HistoryViewModel(
            workHistoryRepository = repository,
            workPeriodCalculator = DefaultWorkPeriodCalculator(),
            timeProvider = TimeProvider { Instant.parse("2026-07-12T08:00:00Z") },
            savedStateHandle = SavedStateHandle(),
        )

    private fun page(
        day: Int,
        nextOffset: Int? = null,
    ) = WorkHistoryPage(listOf(summary(day)), nextOffset)

    private fun summary(day: Int) =
        WorkEntrySummary(
            entryId = "entry-$day",
            date = LocalDate.of(2026, 7, day),
            previewText = "\u8bb0\u5f55$day",
            textBlockCount = 1,
            imageCount = 0,
            tableCount = 0,
            updatedAt = Instant.parse("2026-07-12T08:00:00Z"),
        )
}

private class FakeWorkHistoryRepository : WorkHistoryRepository {
    val recentPages = mutableMapOf<Int, WorkHistoryPage>()
    val searchPages = mutableMapOf<Pair<String, Int>, WorkHistoryPage>()
    val searchDelayMillis = mutableMapOf<String, Long>()
    val recentRequests = mutableListOf<Pair<Int, Int>>()
    val searchRequests = mutableListOf<Triple<String, Int, Int>>()
    val rangeRequests = mutableListOf<Pair<LocalDate, LocalDate>>()
    var failRecent = false

    override suspend fun getRecentEntries(
        limit: Int,
        offset: Int,
    ): DataResult<WorkHistoryPage> {
        recentRequests += limit to offset
        return if (failRecent) {
            DataResult.Failure(DataError.Storage)
        } else {
            DataResult.Success(
                recentPages[offset] ?: WorkHistoryPage(emptyList(), null),
            )
        }
    }

    override suspend fun getEntriesInRange(
        startDate: LocalDate,
        endDate: LocalDate,
    ): DataResult<List<WorkEntrySummary>> {
        rangeRequests += startDate to endDate
        return DataResult.Success(emptyList())
    }

    override suspend fun searchEntries(
        query: String,
        limit: Int,
        offset: Int,
    ): DataResult<WorkHistoryPage> {
        searchRequests += Triple(query, limit, offset)
        searchDelayMillis[query]?.let { millis -> delay(millis) }
        return DataResult.Success(searchPages[query to offset] ?: WorkHistoryPage(emptyList(), null))
    }
}
