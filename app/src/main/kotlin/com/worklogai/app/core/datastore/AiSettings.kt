package com.worklogai.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.worklogai.app.ai.model.AiProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

data class AiSettings(
    val providerType: AiProviderType = AiProviderType.OPENAI_COMPATIBLE,
    val baseUrl: String = "",
    val model: String = "",
    val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
    val useMockProvider: Boolean = true,
    val summaryLanguage: String = "zh-CN",
    val temperature: Double = DEFAULT_TEMPERATURE,
    val allowMobileNetwork: Boolean = false,
    val autoWeeklySummaryEnabled: Boolean = false,
    val autoMonthlySummaryEnabled: Boolean = false,
    val notifyOnAutoSummaryCompletion: Boolean = false,
    val autoSummaryConsentAcknowledged: Boolean = false,
)

interface AiSettingsRepository {
    val settings: Flow<AiSettings>

    suspend fun getSettings(): AiSettings

    suspend fun saveSettings(settings: AiSettings): Result<Unit>
}

class PreferencesAiSettingsRepository
    @Inject
    constructor(
        @AiSettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : AiSettingsRepository {
        override val settings: Flow<AiSettings> =
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        emit(
                            androidx.datastore.preferences.core
                                .emptyPreferences(),
                        )
                    } else {
                        throw error
                    }
                }.map(::toAiSettings)

        override suspend fun getSettings(): AiSettings = settings.first()

        override suspend fun saveSettings(settings: AiSettings): Result<Unit> =
            runCatching {
                validateAiSettings(settings)
                dataStore.edit { preferences ->
                    preferences[PROVIDER_TYPE] = settings.providerType.name
                    preferences[BASE_URL] = settings.baseUrl.trim()
                    preferences[MODEL] = settings.model.trim()
                    preferences[TIMEOUT_SECONDS] = settings.timeoutSeconds
                    preferences[USE_MOCK_PROVIDER] = settings.useMockProvider
                    preferences[SUMMARY_LANGUAGE] = settings.summaryLanguage.trim().ifBlank { "zh-CN" }
                    preferences[TEMPERATURE] = settings.temperature
                    preferences[ALLOW_MOBILE_NETWORK] = settings.allowMobileNetwork
                    preferences[AUTO_WEEKLY_SUMMARY_ENABLED] = settings.autoWeeklySummaryEnabled
                    preferences[AUTO_MONTHLY_SUMMARY_ENABLED] = settings.autoMonthlySummaryEnabled
                    preferences[NOTIFY_ON_AUTO_SUMMARY_COMPLETION] = settings.notifyOnAutoSummaryCompletion
                    preferences[AUTO_SUMMARY_CONSENT_ACKNOWLEDGED] = settings.autoSummaryConsentAcknowledged
                }
            }
    }

internal class AiSettingsValidationException : IllegalArgumentException("AI 设置不符合要求")

internal fun validateAiSettings(settings: AiSettings) {
    require(settings.timeoutSeconds in MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS) { "AI 设置不符合要求" }
    require(settings.temperature in MIN_TEMPERATURE..MAX_TEMPERATURE) { "AI 设置不符合要求" }
    if (!settings.useMockProvider) {
        require(settings.model.isNotBlank()) { "AI 设置不符合要求" }
        require(settings.baseUrl.isNotBlank()) { "AI 设置不符合要求" }
    }
}

private fun toAiSettings(preferences: Preferences): AiSettings =
    AiSettings(
        providerType =
            preferences[PROVIDER_TYPE]
                ?.let { value -> runCatching { AiProviderType.valueOf(value) }.getOrNull() }
                ?: AiProviderType.OPENAI_COMPATIBLE,
        baseUrl = preferences[BASE_URL].orEmpty(),
        model = preferences[MODEL].orEmpty(),
        timeoutSeconds = preferences[TIMEOUT_SECONDS] ?: DEFAULT_TIMEOUT_SECONDS,
        useMockProvider = preferences[USE_MOCK_PROVIDER] ?: true,
        summaryLanguage = preferences[SUMMARY_LANGUAGE] ?: "zh-CN",
        temperature = preferences[TEMPERATURE] ?: DEFAULT_TEMPERATURE,
        allowMobileNetwork = preferences[ALLOW_MOBILE_NETWORK] ?: false,
        autoWeeklySummaryEnabled = preferences[AUTO_WEEKLY_SUMMARY_ENABLED] ?: false,
        autoMonthlySummaryEnabled = preferences[AUTO_MONTHLY_SUMMARY_ENABLED] ?: false,
        notifyOnAutoSummaryCompletion = preferences[NOTIFY_ON_AUTO_SUMMARY_COMPLETION] ?: false,
        autoSummaryConsentAcknowledged = preferences[AUTO_SUMMARY_CONSENT_ACKNOWLEDGED] ?: false,
    )

private const val DEFAULT_TIMEOUT_SECONDS = 60
private const val DEFAULT_TEMPERATURE = 0.2
private const val MIN_TIMEOUT_SECONDS = 10
internal const val MAX_TIMEOUT_SECONDS = 120
private const val MIN_TEMPERATURE = 0.0
private const val MAX_TEMPERATURE = 1.0

private val PROVIDER_TYPE = stringPreferencesKey("ai_provider_type")
private val BASE_URL = stringPreferencesKey("ai_base_url")
private val MODEL = stringPreferencesKey("ai_model")
private val TIMEOUT_SECONDS = intPreferencesKey("ai_timeout_seconds")
private val USE_MOCK_PROVIDER = booleanPreferencesKey("ai_use_mock_provider")
private val SUMMARY_LANGUAGE = stringPreferencesKey("ai_summary_language")
private val TEMPERATURE = doublePreferencesKey("ai_temperature")
private val ALLOW_MOBILE_NETWORK = booleanPreferencesKey("ai_allow_mobile_network")
private val AUTO_WEEKLY_SUMMARY_ENABLED = booleanPreferencesKey("auto_summary_weekly_enabled")
private val AUTO_MONTHLY_SUMMARY_ENABLED = booleanPreferencesKey("auto_summary_monthly_enabled")
private val NOTIFY_ON_AUTO_SUMMARY_COMPLETION = booleanPreferencesKey("auto_summary_notify_on_completion")
private val AUTO_SUMMARY_CONSENT_ACKNOWLEDGED = booleanPreferencesKey("auto_summary_consent_acknowledged")
