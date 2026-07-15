package com.worklogai.app.core.autosummary.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import com.worklogai.app.core.autosummary.AutoSummaryDuePeriodResolver
import com.worklogai.app.core.autosummary.AutoSummaryScheduleStateRepository
import com.worklogai.app.core.autosummary.AutoSummaryScheduler
import com.worklogai.app.core.autosummary.AutoSummaryWorkGateway
import com.worklogai.app.core.autosummary.DefaultAutoSummaryDuePeriodResolver
import com.worklogai.app.core.autosummary.DefaultAutoSummaryScheduler
import com.worklogai.app.core.autosummary.PreferencesAutoSummaryScheduleStateRepository
import com.worklogai.app.core.autosummary.WorkManagerAutoSummaryWorkGateway
import com.worklogai.app.core.datastore.AutoSummaryScheduleDataStore
import com.worklogai.app.worker.AndroidAutoSummaryNotificationManager
import com.worklogai.app.worker.AutoSummaryNotificationManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AutoSummaryBindingsModule {
    @Binds
    @Singleton
    abstract fun bindDuePeriodResolver(
        implementation: DefaultAutoSummaryDuePeriodResolver,
    ): AutoSummaryDuePeriodResolver

    @Binds
    @Singleton
    abstract fun bindScheduleStateRepository(
        implementation: PreferencesAutoSummaryScheduleStateRepository,
    ): AutoSummaryScheduleStateRepository

    @Binds
    @Singleton
    abstract fun bindAutoSummaryScheduler(implementation: DefaultAutoSummaryScheduler): AutoSummaryScheduler

    @Binds
    @Singleton
    abstract fun bindAutoSummaryWorkGateway(implementation: WorkManagerAutoSummaryWorkGateway): AutoSummaryWorkGateway

    @Binds
    @Singleton
    abstract fun bindAutoSummaryNotificationManager(
        implementation: AndroidAutoSummaryNotificationManager,
    ): AutoSummaryNotificationManager
}

@Module
@InstallIn(SingletonComponent::class)
object AutoSummaryDataModule {
    @Provides
    @Singleton
    @AutoSummaryScheduleDataStore
    fun provideScheduleStateDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("auto_summary_schedule.preferences_pb") },
        )

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context,
    ): WorkManager = WorkManager.getInstance(context)
}
