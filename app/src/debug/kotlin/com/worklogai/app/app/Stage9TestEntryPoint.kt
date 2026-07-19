package com.worklogai.app.app

import com.worklogai.app.ai.skill.worksummary.WorkSummarySkill
import com.worklogai.app.ai.usecase.GenerateWorkSummaryUseCase
import com.worklogai.app.core.attachment.AttachmentFileStore
import com.worklogai.app.core.autosummary.AutoSummaryScheduler
import com.worklogai.app.core.backup.BackupArchiveService
import com.worklogai.app.core.backup.RestoreJournalStore
import com.worklogai.app.core.backup.RestoreStartupRecoveryCoordinator
import com.worklogai.app.core.database.WorkLogDatabase
import com.worklogai.app.core.datastore.AiSettingsRepository
import com.worklogai.app.core.history.WorkHistoryRepository
import com.worklogai.app.core.repository.WorkEntryRepository
import com.worklogai.app.core.repository.WorkSummaryRepository
import com.worklogai.app.core.security.SecretStore
import com.worklogai.app.core.table.TableContentEditor
import com.worklogai.app.feature.summary.SummaryPeriodLoader
import com.worklogai.app.worker.AutoSummaryNotificationManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface Stage9TestEntryPoint {
    fun database(): WorkLogDatabase

    fun backupArchiveService(): BackupArchiveService

    fun restoreJournalStore(): RestoreJournalStore

    fun restoreStartupRecoveryCoordinator(): RestoreStartupRecoveryCoordinator

    fun historyRepository(): WorkHistoryRepository

    fun workEntryRepository(): WorkEntryRepository

    fun summaryRepository(): WorkSummaryRepository

    fun generateWorkSummary(): GenerateWorkSummaryUseCase

    fun summaryPeriodLoader(): SummaryPeriodLoader

    fun settingsRepository(): AiSettingsRepository

    fun attachmentFileStore(): AttachmentFileStore

    fun tableContentEditor(): TableContentEditor

    fun autoSummaryScheduler(): AutoSummaryScheduler

    fun secretStore(): SecretStore

    fun notificationManager(): AutoSummaryNotificationManager

    fun workSummarySkill(): WorkSummarySkill
}
