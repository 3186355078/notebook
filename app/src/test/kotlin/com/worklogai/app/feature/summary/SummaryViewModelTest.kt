package com.worklogai.app.feature.summary

import androidx.lifecycle.SavedStateHandle
import com.worklogai.app.ai.skill.worksummary.WorkSummaryResult
import com.worklogai.app.ai.skill.worksummary.WorkSummarySkill
import com.worklogai.app.ai.usecase.GenerateWorkSummaryResult
import com.worklogai.app.ai.usecase.GenerateWorkSummaryUseCase
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.history.DefaultWorkPeriodCalculator
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.WorkSummary
import com.worklogai.app.core.repository.WorkSummaryRepository
import com.worklogai.app.feature.editor.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val summaryRepository = mockk<WorkSummaryRepository>()
    private val generator = mockk<GenerateWorkSummaryUseCase>()
    private val loader = mockk<SummaryPeriodLoader>()
    private val skill = mockk<WorkSummarySkill>()
    private val periodCalculator = DefaultWorkPeriodCalculator()
    private val existing = summary()

    init {
        coEvery { loader.load(any(), any()) } returns
            SummaryPeriodLoadResult.Success(1, 1, existing, existing.sourceHash)
        every { skill.parse(any(), any()) } returns Result.success(WorkSummaryResult())
        every { skill.format(any()) } returns ORIGINAL_FORMATTED
        coEvery { summaryRepository.updateEditedContent(any(), any(), any(), any()) } returns DataResult.Success(Unit)
    }

    @Test
    fun `confirm restore original persists the formatted AI version immediately`() {
        val viewModel = viewModel()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(SummaryAction.ConfirmRestoreOriginal)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        coVerify {
            summaryRepository.updateEditedContent(
                SummaryType.WEEKLY,
                PERIOD_START,
                PERIOD_END,
                ORIGINAL_FORMATTED,
            )
        }
        assertFalse(viewModel.uiState.value.isEditing)
        assertEquals(
            ORIGINAL_FORMATTED,
            viewModel.uiState.value.summary
                ?.editedContent,
        )
    }

    @Test
    fun `copy emits clipboard content and a visible acknowledgement`() {
        val viewModel = viewModel()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(SummaryAction.CopySummary)

        val first = runBlocking { withTimeout(1_000) { viewModel.events.first() } }
        val second = runBlocking { withTimeout(1_000) { viewModel.events.first() } }
        assertEquals(SummaryUiEvent.CopyText(MANUAL_EDIT), first)
        assertEquals(SummaryUiEvent.ShowMessage("已复制"), second)
    }

    @Test
    fun `cancelled generation keeps the previous successful content`() {
        coEvery { generator.invoke(any(), any(), any()) } coAnswers {
            kotlinx.coroutines.awaitCancellation()
        }
        val viewModel = viewModel()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(SummaryAction.ConfirmRegenerate)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.generationState is SummaryGenerationState.Generating)

        viewModel.onAction(SummaryAction.CancelGeneration)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(SummaryGenerationState.Idle, viewModel.uiState.value.generationState)
        assertEquals(
            MANUAL_EDIT,
            viewModel.uiState.value.summary
                ?.editedContent,
        )
    }

    @Test
    fun `stale persisted generation becomes a retryable failure`() {
        val stale =
            existing.copy(
                status = SummaryStatus.GENERATING,
                updatedAt = Instant.parse("2026-07-17T23:57:49Z"),
            )
        coEvery { loader.load(any(), any()) } returns
            SummaryPeriodLoadResult.Success(1, 1, stale, stale.sourceHash)

        val viewModel = viewModel()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        val generationState = viewModel.uiState.value.generationState
        assertTrue(generationState is SummaryGenerationState.Failed)
        generationState as SummaryGenerationState.Failed
        assertEquals("上次生成已中断，请重试", generationState.message)
        assertTrue(generationState.hasPreviousContent)
    }

    @Test
    fun `recent persisted generation remains generating`() {
        val recent =
            existing.copy(
                status = SummaryStatus.GENERATING,
                updatedAt = Instant.parse("2026-07-17T23:58:00Z"),
            )
        coEvery { loader.load(any(), any()) } returns
            SummaryPeriodLoadResult.Success(1, 1, recent, recent.sourceHash)

        val viewModel = viewModel()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(SummaryGenerationState.Generating, viewModel.uiState.value.generationState)
    }

    @Test
    fun `retry replaces a controlled failure with the successful result`() {
        val successful = existing.copy(editedContent = "retry-success")
        coEvery { generator.invoke(any(), any(), any()) } returnsMany
            listOf(
                GenerateWorkSummaryResult.Failure("受控失败", hasPreviousContent = true),
                GenerateWorkSummaryResult.Success(successful, wasInputTruncated = false),
            )
        val viewModel = viewModel()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(SummaryAction.ConfirmRegenerate)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.value.generationState is SummaryGenerationState.Failed)

        viewModel.onAction(SummaryAction.RetryGeneration)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(SummaryGenerationState.Success, viewModel.uiState.value.generationState)
        assertEquals(
            "retry-success",
            viewModel.uiState.value.summary
                ?.editedContent,
        )
    }

    private fun viewModel() =
        SummaryViewModel(
            SummaryViewModelDependencies(
                generator,
                loader,
                summaryRepository,
                periodCalculator,
                skill,
                TimeProvider { Instant.parse("2026-07-18T00:00:00Z") },
            ),
            SavedStateHandle(),
        )

    private fun summary(): WorkSummary {
        val now = Instant.parse("2026-07-13T00:00:00Z")
        return WorkSummary(
            id = "summary-id",
            summaryType = SummaryType.WEEKLY,
            periodStart = PERIOD_START,
            periodEnd = PERIOD_END,
            status = SummaryStatus.SUCCESS,
            sourceHash = "source-hash",
            aiProvider = "mock",
            modelName = "mock",
            originalContent = "{}",
            editedContent = MANUAL_EDIT,
            errorMessage = null,
            createdAt = now,
            updatedAt = now,
            generatedAt = now,
        )
    }

    private companion object {
        const val MANUAL_EDIT = "manual edit"
        const val ORIGINAL_FORMATTED = "formatted AI original"
        val PERIOD_START: LocalDate = LocalDate.of(2026, 7, 6)
        val PERIOD_END: LocalDate = LocalDate.of(2026, 7, 12)
    }
}
