package com.worklogai.app.core.database.di

import com.worklogai.app.core.attachment.AndroidAttachmentFileStore
import com.worklogai.app.core.attachment.AttachmentFileStore
import com.worklogai.app.core.attachment.ImageImportConfig
import com.worklogai.app.core.common.di.IoDispatcher
import com.worklogai.app.core.common.id.IdGenerator
import com.worklogai.app.core.common.id.UuidIdGenerator
import com.worklogai.app.core.common.time.LocalDateProvider
import com.worklogai.app.core.common.time.SystemLocalDateProvider
import com.worklogai.app.core.common.time.SystemTimeProvider
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.database.codec.KotlinxTableContentCodec
import com.worklogai.app.core.database.codec.TableContentCodec
import com.worklogai.app.core.database.hash.Sha256SummarySourceHasher
import com.worklogai.app.core.database.hash.SummarySourceHasher
import com.worklogai.app.core.database.repository.OfflineTodoRepository
import com.worklogai.app.core.database.repository.OfflineWorkEntryRepository
import com.worklogai.app.core.database.repository.OfflineWorkHistoryRepository
import com.worklogai.app.core.database.repository.OfflineWorkSummaryRepository
import com.worklogai.app.core.history.DefaultWorkEntrySummaryBuilder
import com.worklogai.app.core.history.DefaultWorkEntryVisibilityPolicy
import com.worklogai.app.core.history.DefaultWorkPeriodCalculator
import com.worklogai.app.core.history.WorkEntrySummaryBuilder
import com.worklogai.app.core.history.WorkEntryVisibilityPolicy
import com.worklogai.app.core.history.WorkHistoryRepository
import com.worklogai.app.core.history.WorkPeriodCalculator
import com.worklogai.app.core.repository.TodoRepository
import com.worklogai.app.core.repository.WorkEntryRepository
import com.worklogai.app.core.repository.WorkSummaryRepository
import com.worklogai.app.core.table.DefaultTableContentEditor
import com.worklogai.app.core.table.TableContentEditor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindingsModule {
    @Binds
    @Singleton
    abstract fun bindIdGenerator(implementation: UuidIdGenerator): IdGenerator

    @Binds
    @Singleton
    abstract fun bindTimeProvider(implementation: SystemTimeProvider): TimeProvider

    @Binds
    @Singleton
    abstract fun bindLocalDateProvider(implementation: SystemLocalDateProvider): LocalDateProvider

    @Binds
    @Singleton
    abstract fun bindTableContentCodec(implementation: KotlinxTableContentCodec): TableContentCodec

    @Binds
    @Singleton
    abstract fun bindSummarySourceHasher(implementation: Sha256SummarySourceHasher): SummarySourceHasher

    @Binds
    @Singleton
    abstract fun bindWorkEntryRepository(implementation: OfflineWorkEntryRepository): WorkEntryRepository

    @Binds
    @Singleton
    abstract fun bindWorkSummaryRepository(implementation: OfflineWorkSummaryRepository): WorkSummaryRepository

    @Binds
    @Singleton
    abstract fun bindTodoRepository(implementation: OfflineTodoRepository): TodoRepository

    @Binds
    @Singleton
    abstract fun bindAttachmentFileStore(implementation: AndroidAttachmentFileStore): AttachmentFileStore

    @Binds
    @Singleton
    abstract fun bindTableContentEditor(implementation: DefaultTableContentEditor): TableContentEditor
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataManagementBindingsModule {
    @Binds
    @Singleton
    abstract fun bindMarkdownExportService(
        implementation: com.worklogai.app.core.export.DefaultMarkdownExportService,
    ): com.worklogai.app.core.export.MarkdownExportService

    @Binds
    @Singleton
    abstract fun bindSafDocumentCoordinator(
        implementation: com.worklogai.app.core.export.AndroidSafDocumentCoordinator,
    ): com.worklogai.app.core.export.SafDocumentCoordinator

    @Binds
    @Singleton
    internal abstract fun bindBackupDataGateway(
        implementation: com.worklogai.app.core.backup.RoomBackupDataGateway,
    ): com.worklogai.app.core.backup.BackupDataGateway

    @Binds
    @Singleton
    internal abstract fun bindRestoreDirectoryOperations(
        implementation: com.worklogai.app.core.backup.DefaultRestoreDirectoryOperations,
    ): com.worklogai.app.core.backup.RestoreDirectoryOperations

    @Binds
    @Singleton
    internal abstract fun bindRestoreDatabaseFailureInjector(
        implementation: com.worklogai.app.core.backup.NoOpRestoreDatabaseFailureInjector,
    ): com.worklogai.app.core.backup.RestoreDatabaseFailureInjector

    @Binds
    @Singleton
    internal abstract fun bindRestoreJournalStore(
        implementation: com.worklogai.app.core.backup.FileRestoreJournalStore,
    ): com.worklogai.app.core.backup.RestoreJournalStore

    @Binds
    @Singleton
    abstract fun bindBackupArchiveService(
        implementation: com.worklogai.app.core.backup.DefaultBackupArchiveService,
    ): com.worklogai.app.core.backup.BackupArchiveService
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryBindingsModule {
    @Binds
    @Singleton
    abstract fun bindWorkHistoryRepository(implementation: OfflineWorkHistoryRepository): WorkHistoryRepository

    @Binds
    @Singleton
    abstract fun bindWorkEntryVisibilityPolicy(
        implementation: DefaultWorkEntryVisibilityPolicy,
    ): WorkEntryVisibilityPolicy

    @Binds
    @Singleton
    abstract fun bindWorkEntrySummaryBuilder(implementation: DefaultWorkEntrySummaryBuilder): WorkEntrySummaryBuilder

    @Binds
    @Singleton
    abstract fun bindWorkPeriodCalculator(implementation: DefaultWorkPeriodCalculator): WorkPeriodCalculator
}

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    fun provideImageImportConfig(): ImageImportConfig = ImageImportConfig()
}
