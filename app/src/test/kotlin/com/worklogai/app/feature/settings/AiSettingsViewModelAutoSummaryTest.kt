package com.worklogai.app.feature.settings

import com.worklogai.app.ai.provider.AiSummaryProviderFactory
import com.worklogai.app.core.autosummary.AutoSummaryPeriod
import com.worklogai.app.core.autosummary.AutoSummaryScheduleState
import com.worklogai.app.core.autosummary.AutoSummaryScheduleStateRepository
import com.worklogai.app.core.autosummary.AutoSummaryScheduler
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.datastore.AiSettingsRepository
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.WorkSummary
import com.worklogai.app.core.repository.WorkSummaryRepository
import com.worklogai.app.core.security.SecretStore
import com.worklogai.app.feature.editor.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiSettingsViewModelAutoSummaryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var scheduler: FakeAutoSummaryScheduler
    private lateinit var viewModel: AiSettingsViewModel

    @Before
    fun setUp() {
        settingsRepository = FakeSettingsRepository()
        scheduler = FakeAutoSummaryScheduler()
        viewModel = createViewModel()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
    }

    @Test
    fun `automatic weekly and monthly are disabled by default`() {
        assertFalse(viewModel.uiState.value.settings.autoWeeklySummaryEnabled)
        assertFalse(viewModel.uiState.value.settings.autoMonthlySummaryEnabled)
    }

    @Test
    fun `first automatic enable requires privacy and cost confirmation`() {
        viewModel.onAction(AiSettingsAction.AutoWeeklySummaryChanged(true))

        assertTrue(viewModel.uiState.value.showAutoSummaryConsent)
        assertFalse(settingsRepository.value.autoWeeklySummaryEnabled)
    }

    @Test
    fun `confirming mock automatic summary persists and schedules immediate check`() {
        viewModel.onAction(AiSettingsAction.AutoWeeklySummaryChanged(true))
        viewModel.onAction(AiSettingsAction.ConfirmAutoSummaryConsent)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(settingsRepository.value.autoWeeklySummaryEnabled)
        assertTrue(settingsRepository.value.autoSummaryConsentAcknowledged)
        assertTrue(scheduler.applyRequests.any { it.second })
    }

    @Test
    fun `missing real provider configuration refuses automatic enable`() {
        settingsRepository.value = AiSettings(useMockProvider = false)
        viewModel = createViewModel()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(AiSettingsAction.AutoWeeklySummaryChanged(true))
        viewModel.onAction(AiSettingsAction.ConfirmAutoSummaryConsent)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertFalse(settingsRepository.value.autoWeeklySummaryEnabled)
        assertTrue(
            viewModel.uiState.value.errorMessage
                ?.contains("完成大模型服务配置") == true,
        )
    }

    @Test
    fun `turning off the last type cancels automatic generation and periodic work`() {
        settingsRepository.value = AiSettings(autoWeeklySummaryEnabled = true, autoSummaryConsentAcknowledged = true)
        viewModel = createViewModel()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(AiSettingsAction.AutoWeeklySummaryChanged(false))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertFalse(settingsRepository.value.autoWeeklySummaryEnabled)
        assertTrue(scheduler.cancelledTypes.contains(SummaryType.WEEKLY))
        assertTrue(
            scheduler.applyRequests.any {
                !it.first.autoWeeklySummaryEnabled &&
                    !it.first.autoMonthlySummaryEnabled
            },
        )
    }

    private fun createViewModel(): AiSettingsViewModel =
        AiSettingsViewModel(
            settingsRepository = settingsRepository,
            scheduleStateRepository = FakeScheduleStateRepository(),
            autoSummaryScheduler = scheduler,
            workSummaryRepository = emptySummaryRepository(),
            secretStore = FakeSecretStore(),
            providerFactory = mockk<AiSummaryProviderFactory>(),
        )
}

private class FakeSettingsRepository : AiSettingsRepository {
    var value = AiSettings()
    private val flow = MutableStateFlow(value)
    override val settings: Flow<AiSettings> = flow

    override suspend fun getSettings(): AiSettings = value

    override suspend fun saveSettings(settings: AiSettings): Result<Unit> {
        value = settings
        flow.value = settings
        return Result.success(Unit)
    }
}

private class FakeScheduleStateRepository : AutoSummaryScheduleStateRepository {
    override val state = MutableStateFlow(AutoSummaryScheduleState())

    override suspend fun getState(): Result<AutoSummaryScheduleState> = Result.success(state.value)

    override suspend fun updateLastCheck(time: java.time.Instant): Result<Unit> = Result.success(Unit)

    override suspend fun markEvaluated(
        type: SummaryType,
        periodEnd: java.time.LocalDate,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun resetBaseline(type: SummaryType): Result<Unit> = Result.success(Unit)

    override suspend fun updateSchedulerVersion(version: Int): Result<Unit> = Result.success(Unit)
}

private class FakeAutoSummaryScheduler : AutoSummaryScheduler {
    val applyRequests = mutableListOf<Pair<AiSettings, Boolean>>()
    val cancelledTypes = mutableListOf<SummaryType?>()

    override suspend fun applySettings(
        settings: AiSettings,
        enqueueImmediateCheck: Boolean,
    ): Result<Unit> {
        applyRequests += settings to enqueueImmediateCheck
        return Result.success(Unit)
    }

    override suspend fun enqueueImmediateCheck(): Result<Unit> = Result.success(Unit)

    override suspend fun enqueueGeneration(
        period: AutoSummaryPeriod,
        settings: AiSettings,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun cancelAutomaticGeneration(type: SummaryType?): Result<Unit> {
        cancelledTypes += type
        return Result.success(Unit)
    }
}

private class FakeSecretStore : SecretStore {
    override suspend fun saveApiKey(value: String): Result<Unit> = Result.success(Unit)

    override suspend fun getApiKey(): Result<String?> = Result.success(null)

    override suspend fun deleteApiKey(): Result<Unit> = Result.success(Unit)

    override suspend fun hasApiKey(): Boolean = false
}

private fun emptySummaryRepository(): WorkSummaryRepository {
    val repository = mockk<WorkSummaryRepository>()
    every { repository.observeSummaries(any()) } returns flowOf(DataResult.Success(emptyList<WorkSummary>()))
    return repository
}
