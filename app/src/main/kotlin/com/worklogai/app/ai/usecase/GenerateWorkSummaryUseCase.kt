package com.worklogai.app.ai.usecase

import com.worklogai.app.ai.model.AiProviderException
import com.worklogai.app.ai.model.AiResponseFormat
import com.worklogai.app.ai.model.AiSummaryRequest
import com.worklogai.app.ai.model.AiSummaryResponse
import com.worklogai.app.ai.model.userMessage
import com.worklogai.app.ai.provider.AiSummaryProvider
import com.worklogai.app.ai.provider.AiSummaryProviderFactory
import com.worklogai.app.ai.skill.worksummary.PreparedWorkSummaryInput
import com.worklogai.app.ai.skill.worksummary.WorkSummaryResult
import com.worklogai.app.ai.skill.worksummary.WorkSummarySkill
import com.worklogai.app.ai.skill.worksummary.WorkSummarySkillRequest
import com.worklogai.app.core.common.id.IdGenerator
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.database.hash.SummarySourceHasher
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.datastore.AiSettingsRepository
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.GeneratedSummaryContent
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.WorkEntry
import com.worklogai.app.core.model.WorkSummary
import com.worklogai.app.core.repository.WorkEntryRepository
import com.worklogai.app.core.repository.WorkSummaryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

sealed interface GenerateWorkSummaryResult {
    data class Success(
        val summary: WorkSummary,
        val wasInputTruncated: Boolean,
    ) : GenerateWorkSummaryResult

    data class NoEligibleContent(
        val allEntriesBlocked: Boolean,
    ) : GenerateWorkSummaryResult

    data class Failure(
        val message: String,
        val hasPreviousContent: Boolean,
        val retryable: Boolean = false,
    ) : GenerateWorkSummaryResult

    data class Skipped(
        val reason: AutomaticGenerationSkipReason,
    ) : GenerateWorkSummaryResult
}

enum class SummaryGenerationMode {
    MANUAL,
    AUTOMATIC,
}

enum class AutomaticGenerationSkipReason {
    EXISTING_SUCCESS,
    EXISTING_GENERATING,
}

class GenerateWorkSummaryUseCase
    @Inject
    constructor(
        private val workSummaryRepository: WorkSummaryRepository,
        private val summaryGenerationPreparer: SummaryGenerationPreparer,
        private val modelSummaryGenerator: ModelSummaryGenerator,
        private val summarySourceHasher: SummarySourceHasher,
        private val idGenerator: IdGenerator,
        private val timeProvider: TimeProvider,
    ) {
        suspend operator fun invoke(
            summaryType: SummaryType,
            period: DateRange,
            mode: SummaryGenerationMode = SummaryGenerationMode.MANUAL,
        ): GenerateWorkSummaryResult {
            require(period.start <= period.end) { "总结周期无效" }
            val key = "${summaryType.name}:${period.start}:${period.end}"
            return periodLocks.getOrPut(key) { Mutex() }.withLock {
                generate(summaryType, period, mode)
            }
        }

        private suspend fun generate(
            summaryType: SummaryType,
            period: DateRange,
            mode: SummaryGenerationMode,
        ): GenerateWorkSummaryResult {
            if (mode == SummaryGenerationMode.AUTOMATIC) {
                automaticSkipResult(summaryType, period)?.let { return it }
            }
            return when (val preparation = summaryGenerationPreparer.prepare(summaryType, period)) {
                is GenerationPreparation.Failure -> preparation.result
                is GenerationPreparation.NoEligibleContent -> preparation.result
                is GenerationPreparation.Ready -> beginGeneration(preparation.context)
            }
        }

        private suspend fun automaticSkipResult(
            summaryType: SummaryType,
            period: DateRange,
        ): GenerateWorkSummaryResult? =
            when (val result = workSummaryRepository.getSummary(summaryType, period.start, period.end)) {
                is DataResult.Failure -> GenerateWorkSummaryResult.Failure("无法读取已有总结", false)
                is DataResult.Success ->
                    result.value
                        ?.status
                        ?.toAutomaticSkipReason()
                        ?.let(GenerateWorkSummaryResult::Skipped)
            }

        private suspend fun beginGeneration(context: GenerationContext): GenerateWorkSummaryResult =
            when (
                val existingResult =
                    workSummaryRepository.getSummary(
                        context.summaryType,
                        context.period.start,
                        context.period.end,
                    )
            ) {
                is DataResult.Failure -> GenerateWorkSummaryResult.Failure("无法读取已有总结", false)
                is DataResult.Success -> markGenerating(context, existingResult.value)
            }

        private suspend fun markGenerating(
            context: GenerationContext,
            existing: WorkSummary?,
        ): GenerateWorkSummaryResult {
            val pending = existing ?: newSummary(context.summaryType, context.period)
            val pendingResult =
                workSummaryRepository.saveSummary(
                    pending.copy(status = SummaryStatus.PENDING, errorMessage = null),
                )
            return when (pendingResult) {
                is DataResult.Failure -> GenerateWorkSummaryResult.Failure("无法保存总结状态", existing.hasContent())
                is DataResult.Success -> updateGeneratingStatus(context, existing, pending)
            }
        }

        private suspend fun updateGeneratingStatus(
            context: GenerationContext,
            existing: WorkSummary?,
            pending: WorkSummary,
        ): GenerateWorkSummaryResult =
            when (
                workSummaryRepository.updateStatus(
                    context.summaryType,
                    context.period.start,
                    context.period.end,
                    SummaryStatus.GENERATING,
                )
            ) {
                is DataResult.Failure -> GenerateWorkSummaryResult.Failure("无法生成总结", existing.hasContent())
                is DataResult.Success -> generateAndPersist(context, existing, pending)
            }

        private suspend fun generateAndPersist(
            context: GenerationContext,
            existing: WorkSummary?,
            pending: WorkSummary,
        ): GenerateWorkSummaryResult =
            try {
                modelSummaryGenerator.generate(context).foldGeneration(
                    onFailure = { error ->
                        markFailed(
                            context,
                            existing,
                            error.toSafeUserMessage(),
                            error.isAutoRetryable(),
                        )
                    },
                    onSuccess = { generated -> saveSuccessfulGeneration(context, pending, existing, generated) },
                )
            } catch (error: CancellationException) {
                restoreAfterCancellation(workSummaryRepository, context, existing)
                throw error
            } catch (error: IllegalArgumentException) {
                markFailed(context, existing, error.toSafeUserMessage())
            } catch (error: IllegalStateException) {
                markFailed(context, existing, error.toSafeUserMessage())
            }

        private suspend fun saveSuccessfulGeneration(
            context: GenerationContext,
            pending: WorkSummary,
            existing: WorkSummary?,
            generated: ModelSummary,
        ): GenerateWorkSummaryResult {
            val now = timeProvider.now()
            val originalJson =
                workSummarySkillJson.encodeToString(
                    WorkSummaryResult.serializer(),
                    generated.result,
                )
            val editableContent = generated.editableContent
            val content =
                GeneratedSummaryContent(
                    summaryType = context.summaryType,
                    periodStart = context.period.start,
                    periodEnd = context.period.end,
                    sourceHash = summarySourceHasher.hash(context.preparedInput.eligibleEntries),
                    aiProvider = generated.response.providerId,
                    modelName = generated.response.model,
                    originalContent = originalJson,
                    editedContent = editableContent,
                    generatedAt = now,
                )
            return when (workSummaryRepository.saveGeneratedContent(content)) {
                is DataResult.Failure -> GenerateWorkSummaryResult.Failure("无法保存生成的总结", existing.hasContent())
                is DataResult.Success ->
                    GenerateWorkSummaryResult.Success(
                        summary =
                            pending.copy(
                                status = SummaryStatus.SUCCESS,
                                sourceHash = content.sourceHash,
                                aiProvider = content.aiProvider,
                                modelName = content.modelName,
                                originalContent = originalJson,
                                editedContent = editableContent,
                                errorMessage = null,
                                generatedAt = now,
                                updatedAt = now,
                            ),
                        wasInputTruncated = context.preparedInput.wasTruncated,
                    )
            }
        }

        private suspend fun markFailed(
            context: GenerationContext,
            existing: WorkSummary?,
            message: String,
            retryable: Boolean = false,
        ): GenerateWorkSummaryResult {
            workSummaryRepository.updateStatus(
                context.summaryType,
                context.period.start,
                context.period.end,
                SummaryStatus.FAILED,
                message,
            )
            return GenerateWorkSummaryResult.Failure(message, existing.hasContent(), retryable)
        }

        private fun newSummary(
            summaryType: SummaryType,
            period: DateRange,
        ): WorkSummary {
            val now = timeProvider.now()
            return WorkSummary(
                id = idGenerator.generate(),
                summaryType = summaryType,
                periodStart = period.start,
                periodEnd = period.end,
                status = SummaryStatus.PENDING,
                sourceHash = null,
                aiProvider = null,
                modelName = null,
                originalContent = null,
                editedContent = null,
                errorMessage = null,
                createdAt = now,
                updatedAt = now,
                generatedAt = null,
            )
        }
    }

class SummaryGenerationPreparer
    @Inject
    constructor(
        private val workEntryRepository: WorkEntryRepository,
        private val aiSettingsRepository: AiSettingsRepository,
        private val workSummarySkill: WorkSummarySkill,
    ) {
        internal suspend fun prepare(
            summaryType: SummaryType,
            period: DateRange,
        ): GenerationPreparation =
            when (val entryResult = workEntryRepository.getEntries(period.start, period.end)) {
                is DataResult.Failure -> GenerationPreparation.Failure("无法读取工作记录", false)
                is DataResult.Success -> prepareInput(summaryType, period, entryResult.value)
            }

        private suspend fun prepareInput(
            summaryType: SummaryType,
            period: DateRange,
            entries: List<WorkEntry>,
        ): GenerationPreparation {
            val settings = aiSettingsRepository.getSettings()
            val request = WorkSummarySkillRequest(summaryType, period.start, period.end, settings.summaryLanguage)
            return workSummarySkill.prepare(entries, request).fold(
                onSuccess = { prepared ->
                    if (prepared.input.entries.isEmpty()) {
                        GenerationPreparation.NoEligibleContent(
                            entries.isNotEmpty() && entries.all { entry -> !entry.allowAiProcessing },
                        )
                    } else {
                        GenerationPreparation.Ready(
                            GenerationContext(
                                summaryType = summaryType,
                                period = period,
                                settings = settings,
                                request =
                                    request.copy(
                                        sourceDates =
                                            prepared.input.entries
                                                .map { entry -> LocalDate.parse(entry.date) }
                                                .toSet(),
                                    ),
                                preparedInput = prepared,
                            ),
                        )
                    }
                },
                onFailure = { GenerationPreparation.Failure("无法整理工作记录", false) },
            )
        }
    }

class ModelSummaryGenerator
    @Inject
    constructor(
        private val providerFactory: AiSummaryProviderFactory,
        private val workSummarySkill: WorkSummarySkill,
    ) {
        internal suspend fun generate(context: GenerationContext): Result<ModelSummary> {
            val provider = providerFactory.activeProvider()
            val request =
                AiSummaryRequest(
                    systemPrompt = workSummarySkill.systemPrompt,
                    userPrompt = context.preparedInput.userPrompt,
                    model = context.settings.model,
                    temperature = context.settings.temperature,
                    responseFormat = AiResponseFormat.JsonObject,
                )
            return provider.generateSummary(request).flatMapSuspend(
                onFailure = { error -> Result.failure(error) },
                onSuccess = { response -> parseResponse(provider, request, context.request, response) },
            )
        }

        private suspend fun parseResponse(
            provider: AiSummaryProvider,
            request: AiSummaryRequest,
            skillRequest: WorkSummarySkillRequest,
            response: AiSummaryResponse,
        ): Result<ModelSummary> =
            workSummarySkill.parse(response.content, skillRequest).flatMapSuspend(
                onSuccess = { parsed ->
                    Result.success(
                        ModelSummary(
                            response = response,
                            result = parsed,
                            editableContent = workSummarySkill.format(parsed),
                        ),
                    )
                },
                onFailure = { requestRepair(provider, request, skillRequest, response.content) },
            )

        private suspend fun requestRepair(
            provider: AiSummaryProvider,
            request: AiSummaryRequest,
            skillRequest: WorkSummarySkillRequest,
            invalidContent: String,
        ): Result<ModelSummary> {
            val repairRequest =
                request.copy(
                    userPrompt = "仅修复以下内容为符合约定结构的 JSON。不要添加输入中没有的内容：\n$invalidContent",
                )
            return provider.generateSummary(repairRequest).flatMapSuspend(
                onFailure = { error -> Result.failure(error) },
                onSuccess = { repaired ->
                    workSummarySkill.parse(repaired.content, skillRequest).map { parsed ->
                        ModelSummary(
                            response = repaired,
                            result = parsed,
                            editableContent = workSummarySkill.format(parsed),
                        )
                    }
                },
            )
        }
    }

internal sealed interface GenerationPreparation {
    data class Ready(
        val context: GenerationContext,
    ) : GenerationPreparation

    data class Failure(
        val message: String,
        val hasPreviousContent: Boolean,
    ) : GenerationPreparation {
        val result: GenerateWorkSummaryResult.Failure
            get() = GenerateWorkSummaryResult.Failure(message, hasPreviousContent)
    }

    data class NoEligibleContent(
        val allEntriesBlocked: Boolean,
    ) : GenerationPreparation {
        val result: GenerateWorkSummaryResult.NoEligibleContent
            get() = GenerateWorkSummaryResult.NoEligibleContent(allEntriesBlocked)
    }
}

internal data class GenerationContext(
    val summaryType: SummaryType,
    val period: DateRange,
    val settings: AiSettings,
    val request: WorkSummarySkillRequest,
    val preparedInput: PreparedWorkSummaryInput,
)

internal data class ModelSummary(
    val response: AiSummaryResponse,
    val result: WorkSummaryResult,
    val editableContent: String,
)

private suspend fun restoreAfterCancellation(
    workSummaryRepository: WorkSummaryRepository,
    context: GenerationContext,
    existing: WorkSummary?,
) {
    withContext(NonCancellable) {
        if (existing == null) {
            workSummaryRepository.updateStatus(
                context.summaryType,
                context.period.start,
                context.period.end,
                SummaryStatus.PENDING,
            )
        } else {
            workSummaryRepository.saveSummary(existing)
        }
    }
}

private suspend fun <T, R> Result<T>.flatMapSuspend(
    onFailure: suspend (Throwable) -> Result<R>,
    onSuccess: suspend (T) -> Result<R>,
): Result<R> = exceptionOrNull()?.let { error -> onFailure(error) } ?: onSuccess(getOrThrow())

private suspend fun <T> Result<T>.foldGeneration(
    onFailure: suspend (Throwable) -> GenerateWorkSummaryResult,
    onSuccess: suspend (T) -> GenerateWorkSummaryResult,
): GenerateWorkSummaryResult = exceptionOrNull()?.let { error -> onFailure(error) } ?: onSuccess(getOrThrow())

private fun WorkSummary?.hasContent(): Boolean = this?.displayContent?.isNotBlank() == true

private fun Throwable.toSafeUserMessage(): String =
    when (this) {
        is AiProviderException -> providerError.userMessage()
        else -> "大模型返回格式无法解析"
    }

private fun Throwable.isAutoRetryable(): Boolean =
    (this as? AiProviderException)
        ?.providerError
        .let { error ->
            error is com.worklogai.app.ai.model.AiProviderError.Timeout ||
                error is com.worklogai.app.ai.model.AiProviderError.NetworkUnavailable ||
                error is com.worklogai.app.ai.model.AiProviderError.RateLimited ||
                error is com.worklogai.app.ai.model.AiProviderError.ServerError
        }

private fun SummaryStatus.toAutomaticSkipReason(): AutomaticGenerationSkipReason? =
    when (this) {
        SummaryStatus.SUCCESS -> AutomaticGenerationSkipReason.EXISTING_SUCCESS
        SummaryStatus.GENERATING -> AutomaticGenerationSkipReason.EXISTING_GENERATING
        SummaryStatus.FAILED,
        SummaryStatus.PENDING,
        -> null
    }

private val periodLocks = ConcurrentHashMap<String, Mutex>()

private val workSummarySkillJson =
    kotlinx.serialization.json.Json {
        explicitNulls = false
        encodeDefaults = true
    }
