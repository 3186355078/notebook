package com.worklogai.app.core.database.di

import android.content.Context
import androidx.room.Room
import com.worklogai.app.core.database.MIGRATION_1_2
import com.worklogai.app.core.database.WorkLogDatabase
import com.worklogai.app.core.database.dao.AttachmentDao
import com.worklogai.app.core.database.dao.ContentBlockDao
import com.worklogai.app.core.database.dao.TodoDao
import com.worklogai.app.core.database.dao.WorkEntryDao
import com.worklogai.app.core.database.dao.WorkSummaryBackupDao
import com.worklogai.app.core.database.dao.WorkSummaryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): WorkLogDatabase =
        Room
            .databaseBuilder(
                context,
                WorkLogDatabase::class.java,
                WorkLogDatabase.DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideWorkEntryDao(database: WorkLogDatabase): WorkEntryDao = database.workEntryDao()

    @Provides
    fun provideContentBlockDao(database: WorkLogDatabase): ContentBlockDao = database.contentBlockDao()

    @Provides
    fun provideAttachmentDao(database: WorkLogDatabase): AttachmentDao = database.attachmentDao()

    @Provides
    fun provideWorkSummaryDao(database: WorkLogDatabase): WorkSummaryDao = database.workSummaryDao()

    @Provides
    fun provideWorkSummaryBackupDao(database: WorkLogDatabase): WorkSummaryBackupDao = database.workSummaryBackupDao()

    @Provides
    fun provideTodoDao(database: WorkLogDatabase): TodoDao = database.todoDao()
}
