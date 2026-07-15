package com.worklogai.app.ai.provider

import com.worklogai.app.core.datastore.AiSettingsRepository
import javax.inject.Inject

interface AiSummaryProviderFactory {
    suspend fun activeProvider(): AiSummaryProvider
}

class DefaultAiSummaryProviderFactory
    @Inject
    constructor(
        private val settingsRepository: AiSettingsRepository,
        private val mockProvider: MockAiSummaryProvider,
        private val openAiCompatibleProvider: OpenAiCompatibleSummaryProvider,
    ) : AiSummaryProviderFactory {
        override suspend fun activeProvider(): AiSummaryProvider =
            if (settingsRepository.getSettings().useMockProvider) mockProvider else openAiCompatibleProvider
    }
