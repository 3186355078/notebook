package com.worklogai.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worklogai.app.ai.model.AiResponseFormat
import com.worklogai.app.ai.model.AiSummaryRequest
import com.worklogai.app.ai.model.userMessage
import com.worklogai.app.ai.provider.AiSummaryProviderFactory
import com.worklogai.app.core.autosummary.AutoSummaryScheduleState
import com.worklogai.app.core.autosummary.AutoSummaryScheduleStateRepository
import com.worklogai.app.core.autosummary.AutoSummaryScheduler
import com.worklogai.app.core.autosummary.toAutoSummarySettings
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.datastore.AiSettingsRepository
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.repository.WorkSummaryRepository
import com.worklogai.app.core.security.SecretStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiSettingsUiState(
    val settings: AiSettings = AiSettings(),
    val apiKeyInput: String = "",
    val hasApiKey: Boolean = false,
    val scheduleState: AutoSummaryScheduleState = AutoSummaryScheduleState(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isScheduling: Boolean = false,
    val isTestingConnection: Boolean = false,
    val showAutoSummaryConsent: Boolean = false,
    val pendingAutoSummarySettings: AiSettings? = null,
    val notificationPermissionGranted: Boolean = true,
    val lastAutoFailureMessage: String? = null,
    val errorMessage: String? = null,
)

sealed interface AiSettingsAction {
    data class UseMockChanged(
        val value: Boolean,
    ) : AiSettingsAction

    data class BaseUrlChanged(
        val value: String,
    ) : AiSettingsAction

    data class ModelChanged(
        val value: String,
    ) : AiSettingsAction

    data class ApiKeyChanged(
        val value: String,
    ) : AiSettingsAction

    data class TimeoutChanged(
        val value: String,
    ) : AiSettingsAction

    data class AutoWeeklySummaryChanged(
        val value: Boolean,
    ) : AiSettingsAction

    data class AutoMonthlySummaryChanged(
        val value: Boolean,
    ) : AiSettingsAction

    data class AllowMobileNetworkChanged(
        val value: Boolean,
    ) : AiSettingsAction

    data class NotifyOnAutoSummaryCompletionChanged(
        val value: Boolean,
    ) : AiSettingsAction

    data class NotificationPermissionStateChanged(
        val granted: Boolean,
    ) : AiSettingsAction

    data object ConfirmAutoSummaryConsent : AiSettingsAction

    data object DismissAutoSummaryConsent : AiSettingsAction

    data object RunAutoSummaryCheck : AiSettingsAction

    data object Save : AiSettingsAction

    data object DeleteApiKey : AiSettingsAction

    data object TestConnection : AiSettingsAction
}

sealed interface AiSettingsUiEvent {
    data class ShowMessage(
        val message: String,
    ) : AiSettingsUiEvent

    data object RequestNotificationPermission : AiSettingsUiEvent
}

@HiltViewModel
class AiSettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: AiSettingsRepository,
        private val scheduleStateRepository: AutoSummaryScheduleStateRepository,
        private val autoSummaryScheduler: AutoSummaryScheduler,
        private val workSummaryRepository: WorkSummaryRepository,
        private val secretStore: SecretStore,
        private val providerFactory: AiSummaryProviderFactory,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AiSettingsUiState())
        private val _events = Channel<AiSettingsUiEvent>(Channel.BUFFERED)
        private var testJob: Job? = null
        private var schedulingJob: Job? = null
        private var persistedSettings = AiSettings()

        val uiState = _uiState.asStateFlow()
        val events = _events.receiveAsFlow()

        init {
            viewModelScope.launch {
                val settings = settingsRepository.getSettings()
                persistedSettings = settings
                _uiState.value =
                    AiSettingsUiState(
                        settings = settings,
                        hasApiKey = secretStore.hasApiKey(),
                        scheduleState = scheduleStateRepository.getState().getOrDefault(AutoSummaryScheduleState()),
                        isLoading = false,
                    )
            }
            viewModelScope.launch {
                scheduleStateRepository.state.collect { scheduleState ->
                    update { it.copy(scheduleState = scheduleState) }
                }
            }
            viewModelScope.launch {
                combine(
                    workSummaryRepository.observeSummaries(SummaryType.WEEKLY),
                    workSummaryRepository.observeSummaries(SummaryType.MONTHLY),
                ) { weekly, monthly -> listOf(weekly, monthly) }
                    .collect { results ->
                        val failure =
                            results
                                .filterIsInstance<DataResult.Success<List<com.worklogai.app.core.model.WorkSummary>>>()
                                .flatMap { it.value }
                                .filter { it.status == SummaryStatus.FAILED }
                                .maxByOrNull { it.updatedAt }
                                ?.errorMessage
                        update { it.copy(lastAutoFailureMessage = failure) }
                    }
            }
        }

        fun onAction(action: AiSettingsAction) {
            if (!handleAutoSummaryAction(action)) handleGeneralAction(action)
        }

        private fun handleAutoSummaryAction(action: AiSettingsAction): Boolean =
            when (action) {
                is AiSettingsAction.AutoMonthlySummaryChanged -> {
                    changeAutoSummary(monthly = action.value)
                    true
                }
                is AiSettingsAction.AutoWeeklySummaryChanged -> {
                    changeAutoSummary(weekly = action.value)
                    true
                }
                is AiSettingsAction.AllowMobileNetworkChanged -> {
                    persistAutoSettings(_uiState.value.settings.copy(allowMobileNetwork = action.value))
                    true
                }
                AiSettingsAction.ConfirmAutoSummaryConsent -> {
                    _uiState.value.pendingAutoSummarySettings?.let { candidate ->
                        persistAutoSettings(candidate.copy(autoSummaryConsentAcknowledged = true))
                    }
                    update { it.copy(showAutoSummaryConsent = false, pendingAutoSummarySettings = null) }
                    true
                }
                AiSettingsAction.DismissAutoSummaryConsent -> {
                    update { it.copy(showAutoSummaryConsent = false, pendingAutoSummarySettings = null) }
                    true
                }
                is AiSettingsAction.NotifyOnAutoSummaryCompletionChanged -> {
                    persistAutoSettings(
                        _uiState.value.settings.copy(notifyOnAutoSummaryCompletion = action.value),
                        requestPermission = action.value,
                    )
                    true
                }
                AiSettingsAction.RunAutoSummaryCheck -> {
                    runAutoSummaryCheck()
                    true
                }
                else -> false
            }

        private fun handleGeneralAction(action: AiSettingsAction) {
            when (action) {
                is AiSettingsAction.ApiKeyChanged -> update { it.copy(apiKeyInput = action.value, errorMessage = null) }
                is AiSettingsAction.BaseUrlChanged ->
                    update { state ->
                        state.copy(
                            settings = state.settings.copy(baseUrl = action.value),
                            errorMessage = null,
                        )
                    }
                AiSettingsAction.DeleteApiKey -> deleteApiKey()
                is AiSettingsAction.ModelChanged ->
                    update { state ->
                        state.copy(
                            settings = state.settings.copy(model = action.value),
                            errorMessage = null,
                        )
                    }
                is AiSettingsAction.NotificationPermissionStateChanged ->
                    update { it.copy(notificationPermissionGranted = action.granted) }
                AiSettingsAction.Save -> save()
                AiSettingsAction.TestConnection -> testConnection()
                is AiSettingsAction.TimeoutChanged ->
                    update { state ->
                        state.copy(
                            settings = state.settings.copy(timeoutSeconds = action.value.toIntOrNull() ?: 0),
                            errorMessage = null,
                        )
                    }
                is AiSettingsAction.UseMockChanged ->
                    update { state ->
                        state.copy(
                            settings = state.settings.copy(useMockProvider = action.value),
                            errorMessage = null,
                        )
                    }
                else -> Unit
            }
        }

        private fun changeAutoSummary(
            weekly: Boolean? = null,
            monthly: Boolean? = null,
        ) {
            val candidate =
                _uiState.value.settings.copy(
                    autoWeeklySummaryEnabled = weekly ?: _uiState.value.settings.autoWeeklySummaryEnabled,
                    autoMonthlySummaryEnabled = monthly ?: _uiState.value.settings.autoMonthlySummaryEnabled,
                )
            if (candidate.toAutoSummarySettings().isEnabled && !candidate.autoSummaryConsentAcknowledged) {
                update {
                    it.copy(
                        showAutoSummaryConsent = true,
                        pendingAutoSummarySettings = candidate,
                        errorMessage = null,
                    )
                }
            } else {
                persistAutoSettings(candidate)
            }
        }

        private fun persistAutoSettings(
            candidate: AiSettings,
            requestPermission: Boolean = false,
        ) {
            if (schedulingJob?.isActive == true || _uiState.value.isLoading) return
            schedulingJob =
                viewModelScope.launch {
                    if (!candidate.isProviderReadyForAutoSummary(secretStore)) {
                        update { it.copy(errorMessage = "请先完成大模型服务配置后再启用自动总结") }
                        return@launch
                    }
                    update { it.copy(isScheduling = true, errorMessage = null) }
                    val previous = persistedSettings
                    val saved = settingsRepository.saveSettings(candidate)
                    if (saved.isFailure) {
                        update { it.copy(isScheduling = false, errorMessage = "自动总结设置保存失败，请重试") }
                        return@launch
                    }
                    applyAutoSummaryDisableEffects(
                        previous,
                        candidate,
                        scheduleStateRepository,
                        autoSummaryScheduler,
                    )
                    val enqueueImmediateCheck = shouldEnqueueImmediateCheck(previous, candidate)
                    val scheduled =
                        autoSummaryScheduler.applySettings(
                            candidate,
                            enqueueImmediateCheck = enqueueImmediateCheck,
                        )
                    if (scheduled.isFailure) {
                        update { it.copy(isScheduling = false, errorMessage = "自动总结调度失败，请重试") }
                        return@launch
                    }
                    persistedSettings = candidate
                    update { it.copy(settings = candidate, isScheduling = false) }
                    if (requestPermission && candidate.notifyOnAutoSummaryCompletion) {
                        _events.trySend(AiSettingsUiEvent.RequestNotificationPermission)
                    }
                }
        }

        private fun runAutoSummaryCheck() {
            if (schedulingJob?.isActive == true) return
            schedulingJob =
                viewModelScope.launch {
                    if (!_uiState.value.settings
                            .toAutoSummarySettings()
                            .isEnabled
                    ) {
                        _events.trySend(AiSettingsUiEvent.ShowMessage("请先开启自动周报或自动月报"))
                        return@launch
                    }
                    update { it.copy(isScheduling = true, errorMessage = null) }
                    val result = autoSummaryScheduler.enqueueImmediateCheck()
                    update { it.copy(isScheduling = false) }
                    if (result.isSuccess) {
                        _events.trySend(AiSettingsUiEvent.ShowMessage("已安排自动检查"))
                    } else {
                        update { it.copy(errorMessage = "无法安排自动检查，请重试") }
                    }
                }
        }

        private fun save() {
            if (_uiState.value.isSaving) return
            viewModelScope.launch {
                update { it.copy(isSaving = true, errorMessage = null) }
                val settings = _uiState.value.settings
                val settingsResult = settingsRepository.saveSettings(settings)
                val keyResult =
                    _uiState.value.apiKeyInput.takeIf(String::isNotBlank)?.let { value ->
                        secretStore.saveApiKey(value)
                    }
                        ?: Result.success(Unit)
                if (settingsResult.isSuccess && keyResult.isSuccess) {
                    persistedSettings = settings
                    val hasApiKey = secretStore.hasApiKey()
                    update { it.copy(apiKeyInput = "", hasApiKey = hasApiKey, isSaving = false) }
                    if (
                        settings.toAutoSummarySettings().isEnabled &&
                        settings.isProviderReadyForAutoSummary(secretStore)
                    ) {
                        autoSummaryScheduler.applySettings(settings, enqueueImmediateCheck = true)
                    }
                    _events.trySend(AiSettingsUiEvent.ShowMessage("设置已保存"))
                } else {
                    update { it.copy(isSaving = false, errorMessage = "设置保存失败，请检查输入") }
                }
            }
        }

        private fun deleteApiKey() {
            viewModelScope.launch {
                if (secretStore.deleteApiKey().isSuccess) {
                    autoSummaryScheduler.cancelAutomaticGeneration()
                    update { it.copy(apiKeyInput = "", hasApiKey = false) }
                    _events.trySend(AiSettingsUiEvent.ShowMessage("API Key 已删除"))
                } else {
                    update { it.copy(errorMessage = "无法删除 API Key") }
                }
            }
        }

        private fun testConnection() {
            if (testJob?.isActive == true || _uiState.value.isSaving) return
            testJob =
                viewModelScope.launch {
                    update { it.copy(isTestingConnection = true, errorMessage = null) }
                    val result =
                        providerFactory
                            .activeProvider()
                            .generateSummary(
                                AiSummaryRequest(
                                    systemPrompt = "只返回 JSON。",
                                    userPrompt = "{\"status\":\"ok\"}",
                                    model = _uiState.value.settings.model,
                                    temperature = 0.0,
                                    responseFormat = AiResponseFormat.JsonObject,
                                ),
                            )
                    update { it.copy(isTestingConnection = false) }
                    if (result.isSuccess) {
                        _events.trySend(AiSettingsUiEvent.ShowMessage("连接测试成功"))
                    } else {
                        val message =
                            (result.exceptionOrNull() as? com.worklogai.app.ai.model.AiProviderException)
                                ?.providerError
                                ?.userMessage()
                                ?: "连接测试失败，请检查设置"
                        update { it.copy(errorMessage = message) }
                    }
                }
        }

        private fun update(transform: (AiSettingsUiState) -> AiSettingsUiState) {
            _uiState.value = transform(_uiState.value)
        }
    }

private suspend fun AiSettings.isProviderReadyForAutoSummary(secretStore: SecretStore): Boolean =
    !toAutoSummarySettings().isEnabled ||
        useMockProvider ||
        (baseUrl.isNotBlank() && model.isNotBlank() && secretStore.hasApiKey())

private suspend fun applyAutoSummaryDisableEffects(
    previous: AiSettings,
    candidate: AiSettings,
    scheduleStateRepository: AutoSummaryScheduleStateRepository,
    scheduler: AutoSummaryScheduler,
) {
    val weeklyDisabled = previous.autoWeeklySummaryEnabled && !candidate.autoWeeklySummaryEnabled
    val monthlyDisabled = previous.autoMonthlySummaryEnabled && !candidate.autoMonthlySummaryEnabled
    if (weeklyDisabled) {
        scheduleStateRepository.resetBaseline(SummaryType.WEEKLY)
        scheduler.cancelAutomaticGeneration(SummaryType.WEEKLY)
    }
    if (monthlyDisabled) {
        scheduleStateRepository.resetBaseline(SummaryType.MONTHLY)
        scheduler.cancelAutomaticGeneration(SummaryType.MONTHLY)
    }
    if (previous.allowMobileNetwork != candidate.allowMobileNetwork) {
        scheduler.cancelAutomaticGeneration()
    }
}

private fun shouldEnqueueImmediateCheck(
    previous: AiSettings,
    candidate: AiSettings,
): Boolean =
    previous.allowMobileNetwork != candidate.allowMobileNetwork ||
        previous.autoWeeklySummaryEnabled != candidate.autoWeeklySummaryEnabled ||
        previous.autoMonthlySummaryEnabled != candidate.autoMonthlySummaryEnabled
