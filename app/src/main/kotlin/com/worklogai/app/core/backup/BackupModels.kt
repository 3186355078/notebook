package com.worklogai.app.core.backup

import com.worklogai.app.core.database.entity.AttachmentEntity
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.database.entity.WorkSummaryEntity
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

const val BACKUP_FORMAT_NAME = "worklog-ai-backup"
const val BACKUP_FORMAT_VERSION = 1

@Serializable
data class BackupManifest(
    val formatName: String,
    val formatVersion: Int,
    val appId: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val createdAt: String,
    val databaseVersion: Int,
    val entryCount: Int,
    val blockCount: Int,
    val attachmentCount: Int,
    val summaryCount: Int,
    val files: List<BackupFileManifest>,
)

@Serializable
data class BackupFileManifest(
    val path: String,
    val size: Long,
    val sha256: String,
)

@Serializable
data class BackupWorkEntry(
    val id: String,
    val entryDate: String,
    val title: String? = null,
    val allowAiProcessing: Boolean,
    val isDeleted: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BackupContentBlock(
    val id: String,
    val entryId: String,
    val blockType: String,
    val blockOrder: Int,
    val textContent: String? = null,
    val structuredContent: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BackupAttachment(
    val id: String,
    val blockId: String,
    val localPath: String,
    val mimeType: String,
    val fileSize: Long,
    val width: Int? = null,
    val height: Int? = null,
    val caption: String? = null,
    val createdAt: String,
    val fileIncluded: Boolean,
)

@Serializable
data class BackupWorkSummary(
    val id: String,
    val summaryType: String,
    val periodStart: String,
    val periodEnd: String,
    val status: String,
    val sourceHash: String? = null,
    val aiProvider: String? = null,
    val modelName: String? = null,
    val originalContent: String? = null,
    val editedContent: String? = null,
    val errorMessage: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val generatedAt: String? = null,
)

@Serializable
data class BackupSettings(
    val providerType: String,
    val baseUrl: String,
    val model: String,
    val timeoutSeconds: Int,
    val useMockProvider: Boolean,
    val summaryLanguage: String,
    val temperature: Double,
    val allowMobileNetwork: Boolean,
    val autoWeeklySummaryEnabled: Boolean,
    val autoMonthlySummaryEnabled: Boolean,
    val notifyOnAutoSummaryCompletion: Boolean,
    val autoSummaryConsentAcknowledged: Boolean,
)

internal data class BackupDatabaseSnapshot(
    val entries: List<WorkEntryEntity>,
    val blocks: List<ContentBlockEntity>,
    val attachments: List<AttachmentEntity>,
    val summaries: List<WorkSummaryEntity>,
)

internal data class BackupPayload(
    val entries: List<BackupWorkEntry>,
    val blocks: List<BackupContentBlock>,
    val attachments: List<BackupAttachment>,
    val summaries: List<BackupWorkSummary>,
    val settings: BackupSettings,
)

internal fun WorkEntryEntity.toBackup(): BackupWorkEntry =
    BackupWorkEntry(
        id,
        entryDate.toString(),
        title,
        allowAiProcessing,
        isDeleted,
        createdAt.toString(),
        updatedAt.toString(),
    )

internal fun ContentBlockEntity.toBackup(): BackupContentBlock =
    BackupContentBlock(
        id,
        entryId,
        blockType.name,
        blockOrder,
        textContent,
        structuredContent,
        createdAt.toString(),
        updatedAt.toString(),
    )

internal fun AttachmentEntity.toBackup(fileIncluded: Boolean): BackupAttachment =
    BackupAttachment(
        id,
        blockId,
        localPath,
        mimeType,
        fileSize,
        width,
        height,
        caption,
        createdAt.toString(),
        fileIncluded,
    )

internal fun WorkSummaryEntity.toBackup(): BackupWorkSummary =
    BackupWorkSummary(
        id,
        summaryType.name,
        periodStart.toString(),
        periodEnd.toString(),
        status.name,
        sourceHash,
        aiProvider,
        modelName,
        originalContent,
        editedContent,
        errorMessage,
        createdAt.toString(),
        updatedAt.toString(),
        generatedAt?.toString(),
    )

internal fun AiSettings.toBackup(): BackupSettings =
    BackupSettings(
        providerType.name,
        baseUrl,
        model,
        timeoutSeconds,
        useMockProvider,
        summaryLanguage,
        temperature,
        allowMobileNetwork,
        autoWeeklySummaryEnabled,
        autoMonthlySummaryEnabled,
        notifyOnAutoSummaryCompletion,
        autoSummaryConsentAcknowledged,
    )

internal fun BackupWorkEntry.toEntity(): WorkEntryEntity =
    WorkEntryEntity(
        id,
        LocalDate.parse(entryDate),
        title,
        allowAiProcessing,
        isDeleted,
        Instant.parse(createdAt),
        Instant.parse(updatedAt),
    )

internal fun BackupContentBlock.toEntity(): ContentBlockEntity =
    ContentBlockEntity(
        id,
        entryId,
        ContentBlockType.valueOf(blockType),
        blockOrder,
        textContent,
        structuredContent,
        Instant.parse(createdAt),
        Instant.parse(updatedAt),
    )

internal fun BackupAttachment.toEntity(): AttachmentEntity =
    AttachmentEntity(id, blockId, localPath, mimeType, fileSize, width, height, caption, Instant.parse(createdAt))

internal fun BackupWorkSummary.toEntity(): WorkSummaryEntity =
    WorkSummaryEntity(
        id,
        SummaryType.valueOf(summaryType),
        LocalDate.parse(periodStart),
        LocalDate.parse(periodEnd),
        SummaryStatus.valueOf(status),
        sourceHash,
        aiProvider,
        modelName,
        originalContent,
        editedContent,
        errorMessage,
        Instant.parse(createdAt),
        Instant.parse(updatedAt),
        generatedAt?.let(Instant::parse),
    )

internal fun BackupSettings.toAiSettings(): AiSettings =
    AiSettings(
        providerType =
            com.worklogai.app.ai.model.AiProviderType
                .valueOf(providerType),
        baseUrl = baseUrl,
        model = model,
        timeoutSeconds = timeoutSeconds,
        useMockProvider = useMockProvider,
        summaryLanguage = summaryLanguage,
        temperature = temperature,
        allowMobileNetwork = allowMobileNetwork,
        autoWeeklySummaryEnabled = autoWeeklySummaryEnabled,
        autoMonthlySummaryEnabled = autoMonthlySummaryEnabled,
        notifyOnAutoSummaryCompletion = notifyOnAutoSummaryCompletion,
        autoSummaryConsentAcknowledged = autoSummaryConsentAcknowledged,
    )
