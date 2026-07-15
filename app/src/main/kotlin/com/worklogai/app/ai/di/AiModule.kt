package com.worklogai.app.ai.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.worklogai.app.ai.provider.AiHttpClientFactory
import com.worklogai.app.ai.provider.AiSummaryProviderFactory
import com.worklogai.app.ai.provider.DefaultAiHttpClientFactory
import com.worklogai.app.ai.provider.DefaultAiSummaryProviderFactory
import com.worklogai.app.ai.skill.worksummary.DefaultSummaryInputLimiter
import com.worklogai.app.ai.skill.worksummary.DefaultWorkSummaryInputBuilder
import com.worklogai.app.ai.skill.worksummary.DefaultWorkSummaryOutputParser
import com.worklogai.app.ai.skill.worksummary.DefaultWorkSummarySkill
import com.worklogai.app.ai.skill.worksummary.MarkdownWorkSummaryFormatter
import com.worklogai.app.ai.skill.worksummary.SummaryInputLimiter
import com.worklogai.app.ai.skill.worksummary.WorkSummaryFormatter
import com.worklogai.app.ai.skill.worksummary.WorkSummaryInputBuilder
import com.worklogai.app.ai.skill.worksummary.WorkSummaryOutputParser
import com.worklogai.app.ai.skill.worksummary.WorkSummarySkill
import com.worklogai.app.core.datastore.AiSettingsDataStore
import com.worklogai.app.core.datastore.AiSettingsRepository
import com.worklogai.app.core.datastore.PreferencesAiSettingsRepository
import com.worklogai.app.core.security.AndroidKeystoreSecretCipher
import com.worklogai.app.core.security.DataStoreEncryptedSecretPayloadStore
import com.worklogai.app.core.security.EncryptedPreferencesSecretStore
import com.worklogai.app.core.security.EncryptedSecretPayloadStore
import com.worklogai.app.core.security.SecretCipher
import com.worklogai.app.core.security.SecretStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiBindingsModule {
    @Binds
    @Singleton
    abstract fun bindAiSettingsRepository(implementation: PreferencesAiSettingsRepository): AiSettingsRepository

    @Binds
    @Singleton
    abstract fun bindAiHttpClientFactory(implementation: DefaultAiHttpClientFactory): AiHttpClientFactory

    @Binds
    @Singleton
    abstract fun bindAiSummaryProviderFactory(implementation: DefaultAiSummaryProviderFactory): AiSummaryProviderFactory
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiSecurityBindingsModule {
    @Binds
    @Singleton
    abstract fun bindSecretCipher(implementation: AndroidKeystoreSecretCipher): SecretCipher

    @Binds
    @Singleton
    abstract fun bindSecretStore(implementation: EncryptedPreferencesSecretStore): SecretStore

    @Binds
    @Singleton
    abstract fun bindEncryptedSecretPayloadStore(
        implementation: DataStoreEncryptedSecretPayloadStore,
    ): EncryptedSecretPayloadStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkSummarySkillBindingsModule {
    @Binds
    @Singleton
    abstract fun bindWorkSummarySkill(implementation: DefaultWorkSummarySkill): WorkSummarySkill

    @Binds
    @Singleton
    abstract fun bindWorkSummaryInputBuilder(implementation: DefaultWorkSummaryInputBuilder): WorkSummaryInputBuilder

    @Binds
    @Singleton
    abstract fun bindSummaryInputLimiter(implementation: DefaultSummaryInputLimiter): SummaryInputLimiter

    @Binds
    @Singleton
    abstract fun bindWorkSummaryOutputParser(implementation: DefaultWorkSummaryOutputParser): WorkSummaryOutputParser

    @Binds
    @Singleton
    abstract fun bindWorkSummaryFormatter(implementation: MarkdownWorkSummaryFormatter): WorkSummaryFormatter
}

@Module
@InstallIn(SingletonComponent::class)
object AiDataStoreModule {
    @Provides
    @Singleton
    @AiSettingsDataStore
    fun provideAiSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("ai_settings.preferences_pb") },
        )
}
