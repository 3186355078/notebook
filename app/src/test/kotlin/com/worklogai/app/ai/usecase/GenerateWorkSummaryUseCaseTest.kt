package com.worklogai.app.ai.usecase

import com.worklogai.app.ai.model.AiProviderError
import com.worklogai.app.ai.model.AiProviderException
import com.worklogai.app.ai.model.AiSummaryResponse
import com.worklogai.app.ai.provider.AiSummaryProvider
import com.worklogai.app.ai.provider.AiSummaryProviderFactory
import com.worklogai.app.ai.provider.MockAiSummaryProvider
import com.worklogai.app.ai.skill.worksummary.DefaultSummaryInputLimiter
import com.worklogai.app.ai.skill.worksummary.DefaultWorkSummaryInputBuilder
import com.worklogai.app.ai.skill.worksummary.DefaultWorkSummaryOutputParser
import com.worklogai.app.ai.skill.worksummary.DefaultWorkSummarySkill
import com.worklogai.app.ai.skill.worksummary.MarkdownWorkSummaryFormatter
import com.worklogai.app.core.common.id.IdGenerator
import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.database.hash.Sha256SummarySourceHasher
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.datastore.AiSettingsRepository
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.GeneratedSummaryContent
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.WorkEntry
import com.worklogai.app.core.model.WorkSummary
import com.worklogai.app.core.repository.WorkEntryRepository
import com.worklogai.app.core.repository.WorkSummaryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class GenerateWorkSummaryUseCaseTest {
    @Test
    fun `generates a weekly summary with source hash original json and editable text`() =
        runBlocking {
            val entryRepository = FakeEntryRepository(listOf(entry(allowAi = true)))
            val summaryRepository = FakeSummaryRepository()
            val useCase = createUseCase(entryRepository, summaryRepository, MockAiSummaryProvider())

            val result = useCase(SummaryType.WEEKLY, range())

            assertTrue(result is GenerateWorkSummaryResult.Success)
            val summary = (result as GenerateWorkSummaryResult.Success).summary
            assertEquals(SummaryStatus.SUCCESS, summary.status)
            assertTrue(summary.sourceHash!!.isNotBlank())
            assertTrue(summary.originalContent!!.contains("completedItems"))
            assertTrue(summary.editedContent!!.startsWith("#"))
            assertEquals(1, summaryRepository.values.size)
        }

    @Test
    fun `does not call provider when every record is disallowed`() =
        runBlocking {
            val provider = CountingProvider()
            val result =
                createUseCase(
                    FakeEntryRepository(listOf(entry(allowAi = false))),
                    FakeSummaryRepository(),
                    provider,
                )(SummaryType.WEEKLY, range())

            assertEquals(0, provider.calls)
            assertEquals(GenerateWorkSummaryResult.NoEligibleContent(allEntriesBlocked = true), result)
        }

    @Test
    fun `generation failure keeps prior content while marking same period failed`() =
        runBlocking {
            val summaryRepository = FakeSummaryRepository()
            val existing = summary("existing", SummaryStatus.SUCCESS, "old-json", "old text")
            summaryRepository.values[key(existing.summaryType, existing.periodStart, existing.periodEnd)] = existing
            val result =
                createUseCase(
                    FakeEntryRepository(listOf(entry(allowAi = true))),
                    summaryRepository,
                    CountingProvider(fail = true),
                )(SummaryType.WEEKLY, range())

            assertTrue(result is GenerateWorkSummaryResult.Failure)
            val persisted = summaryRepository.values.values.single()
            assertEquals(SummaryStatus.FAILED, persisted.status)
            assertEquals("old-json", persisted.originalContent)
            assertEquals("old text", persisted.editedContent)
        }

    @Test
    fun `automatic generation skips an existing successful summary without calling provider`() =
        runBlocking {
            val provider = CountingProvider()
            val summaryRepository = FakeSummaryRepository()
            val existing = summary("existing", SummaryStatus.SUCCESS, "old-json", "manual text")
            summaryRepository.values[key(existing.summaryType, existing.periodStart, existing.periodEnd)] = existing

            val result =
                createUseCase(
                    FakeEntryRepository(listOf(entry(allowAi = true))),
                    summaryRepository,
                    provider,
                )(SummaryType.WEEKLY, range(), SummaryGenerationMode.AUTOMATIC)

            assertEquals(
                GenerateWorkSummaryResult.Skipped(AutomaticGenerationSkipReason.EXISTING_SUCCESS),
                result,
            )
            assertEquals(0, provider.calls)
            assertEquals(
                "manual text",
                summaryRepository.values.values
                    .single()
                    .editedContent,
            )
        }

    @Test
    fun `automatic generation retries only transient provider failures`() =
        runBlocking {
            val cases =
                listOf(
                    AiProviderError.RateLimited to true,
                    AiProviderError.Timeout to true,
                    AiProviderError.ServerError(500) to true,
                    AiProviderError.Unauthorized to false,
                    AiProviderError.Forbidden to false,
                    AiProviderError.ModelNotFound to false,
                )

            cases.forEach { (providerError, expectedRetryable) ->
                val result =
                    createUseCase(
                        FakeEntryRepository(listOf(entry(allowAi = true))),
                        FakeSummaryRepository(),
                        FailingProvider(AiProviderException(providerError)),
                    )(SummaryType.WEEKLY, range(), SummaryGenerationMode.AUTOMATIC)

                assertTrue(result is GenerateWorkSummaryResult.Failure)
                assertEquals(expectedRetryable, (result as GenerateWorkSummaryResult.Failure).retryable)
            }
        }

    @Test
    fun `automatic work retry regenerates an existing transient failure`() =
        runBlocking {
            val provider = FailOnceProvider()
            val summaryRepository = FakeSummaryRepository()
            val useCase =
                createUseCase(
                    FakeEntryRepository(listOf(entry(allowAi = true))),
                    summaryRepository,
                    provider,
                )

            val first = useCase(SummaryType.WEEKLY, range(), SummaryGenerationMode.AUTOMATIC)
            val second = useCase(SummaryType.WEEKLY, range(), SummaryGenerationMode.AUTOMATIC)

            assertTrue(first is GenerateWorkSummaryResult.Failure)
            assertEquals(true, (first as GenerateWorkSummaryResult.Failure).retryable)
            assertTrue(second is GenerateWorkSummaryResult.Success)
            assertEquals(2, provider.calls)
            assertEquals(
                SummaryStatus.SUCCESS,
                summaryRepository.values.values
                    .single()
                    .status,
            )
        }

    @Test
    fun `invalid model json is repaired once and never retried indefinitely`() =
        runBlocking {
            val provider = InvalidJsonProvider()
            val result =
                createUseCase(
                    FakeEntryRepository(listOf(entry(allowAi = true))),
                    FakeSummaryRepository(),
                    provider,
                )(SummaryType.WEEKLY, range(), SummaryGenerationMode.AUTOMATIC)

            assertTrue(result is GenerateWorkSummaryResult.Failure)
            assertEquals(false, (result as GenerateWorkSummaryResult.Failure).retryable)
            assertEquals(2, provider.calls)
        }

    private fun createUseCase(
        entryRepository: WorkEntryRepository,
        summaryRepository: WorkSummaryRepository,
        provider: AiSummaryProvider,
    ): GenerateWorkSummaryUseCase =
        GenerateWorkSummaryUseCase(
            workSummaryRepository = summaryRepository,
            summaryGenerationPreparer =
                SummaryGenerationPreparer(
                    workEntryRepository = entryRepository,
                    aiSettingsRepository = FakeSettingsRepository(),
                    workSummarySkill = createSkill(),
                ),
            modelSummaryGenerator =
                ModelSummaryGenerator(
                    providerFactory =
                        object : AiSummaryProviderFactory {
                            override suspend fun activeProvider(): AiSummaryProvider = provider
                        },
                    workSummarySkill = createSkill(),
                ),
            summarySourceHasher = Sha256SummarySourceHasher(),
            idGenerator = IdGenerator { "generated" },
            timeProvider = TimeProvider { Instant.parse("2026-07-13T00:00:00Z") },
        )

    private fun createSkill() =
        DefaultWorkSummarySkill(
            DefaultWorkSummaryInputBuilder(),
            DefaultSummaryInputLimiter(),
            DefaultWorkSummaryOutputParser(),
            MarkdownWorkSummaryFormatter(),
        )

    private fun range() = DateRange(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19))

    private fun entry(allowAi: Boolean): WorkEntry {
        val now = Instant.parse("2026-07-13T00:00:00Z")
        return WorkEntry(
            "entry",
            LocalDate.of(2026, 7, 13),
            null,
            allowAi,
            false,
            now,
            now,
            listOf(ContentBlock.Text("text", "entry", 0, "完成登录", now, now)),
        )
    }
}

private class FailOnceProvider : AiSummaryProvider {
    private val success = MockAiSummaryProvider()
    var calls: Int = 0
        private set

    override val providerId: String = "fail-once"

    override suspend fun generateSummary(
        request: com.worklogai.app.ai.model.AiSummaryRequest,
    ): Result<AiSummaryResponse> {
        calls += 1
        return if (calls == 1) {
            Result.failure(AiProviderException(AiProviderError.RateLimited))
        } else {
            success.generateSummary(request)
        }
    }
}

private class FakeSettingsRepository : AiSettingsRepository {
    private val value = AiSettings(useMockProvider = true, model = "mock")
    override val settings: Flow<AiSettings> = flowOf(value)

    override suspend fun getSettings(): AiSettings = value

    override suspend fun saveSettings(settings: AiSettings): Result<Unit> = Result.success(Unit)
}

private class FakeEntryRepository(
    private val entries: List<WorkEntry>,
) : WorkEntryRepository {
    override fun observeEntry(date: LocalDate): Flow<DataResult<WorkEntry?>> =
        flowOf(
            DataResult.Success(
                entries.firstOrNull {
                    it.entryDate ==
                        date
                },
            ),
        )

    override suspend fun getEntry(date: LocalDate): DataResult<WorkEntry?> =
        DataResult.Success(
            entries.firstOrNull {
                it.entryDate ==
                    date
            },
        )

    override suspend fun getEntries(
        startDate: LocalDate,
        endDate: LocalDate,
    ): DataResult<List<WorkEntry>> =
        DataResult.Success(
            entries.filter {
                it.entryDate in
                    startDate..endDate
            },
        )

    override suspend fun getOrCreateEntry(date: LocalDate): DataResult<WorkEntry> =
        DataResult.Failure(DataError.NotFound)

    override suspend fun updateEntryTitle(
        entryId: String,
        title: String?,
    ): DataResult<Unit> = DataResult.Success(Unit)

    override suspend fun updateAllowAiProcessing(
        entryId: String,
        allowAiProcessing: Boolean,
    ): DataResult<Unit> = DataResult.Success(Unit)

    override suspend fun addTextBlock(
        entryId: String,
        text: String,
    ): DataResult<ContentBlock.Text> = DataResult.Failure(DataError.NotFound)

    override suspend fun addTableBlock(
        entryId: String,
        tableContent: com.worklogai.app.core.model.TableContent,
    ): DataResult<ContentBlock.Table> = DataResult.Failure(DataError.NotFound)

    override suspend fun addImageBlock(entryId: String): DataResult<ContentBlock.Image> =
        DataResult.Failure(DataError.NotFound)

    override suspend fun addImageBlock(
        entryId: String,
        attachment: com.worklogai.app.core.model.AttachmentDraft,
    ): DataResult<ContentBlock.Image> = DataResult.Failure(DataError.NotFound)

    override suspend fun updateTextBlock(
        blockId: String,
        text: String,
    ): DataResult<ContentBlock.Text> = DataResult.Failure(DataError.NotFound)

    override suspend fun updateTableBlock(
        blockId: String,
        tableContent: com.worklogai.app.core.model.TableContent,
    ): DataResult<ContentBlock.Table> = DataResult.Failure(DataError.NotFound)

    override suspend fun reorderBlocks(
        entryId: String,
        orderedBlockIds: List<String>,
    ): DataResult<Unit> = DataResult.Success(Unit)

    override suspend fun deleteBlock(blockId: String): DataResult<List<String>> = DataResult.Success(emptyList())

    override suspend fun addAttachment(
        blockId: String,
        draft: com.worklogai.app.core.model.AttachmentDraft,
    ): DataResult<com.worklogai.app.core.model.Attachment> = DataResult.Failure(DataError.NotFound)

    override suspend fun deleteAttachment(attachmentId: String): DataResult<String> =
        DataResult.Failure(DataError.NotFound)

    override suspend fun updateImageCaption(
        attachmentId: String,
        caption: String?,
    ): DataResult<com.worklogai.app.core.model.Attachment> = DataResult.Failure(DataError.NotFound)

    override suspend fun softDeleteEntry(entryId: String): DataResult<Unit> = DataResult.Success(Unit)

    override suspend fun restoreEntry(entryId: String): DataResult<Unit> = DataResult.Success(Unit)

    override suspend fun purgeEntry(entryId: String): DataResult<List<String>> = DataResult.Success(emptyList())
}

private class FakeSummaryRepository : WorkSummaryRepository {
    val values = linkedMapOf<String, WorkSummary>()

    override fun observeSummary(
        summaryType: SummaryType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): Flow<DataResult<WorkSummary?>> = flowOf(DataResult.Success(values[key(summaryType, periodStart, periodEnd)]))

    override fun observeSummaries(summaryType: SummaryType): Flow<DataResult<List<WorkSummary>>> =
        flowOf(
            DataResult.Success(
                values.values.filter {
                    it.summaryType ==
                        summaryType
                },
            ),
        )

    override suspend fun getSummary(
        summaryType: SummaryType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): DataResult<WorkSummary?> = DataResult.Success(values[key(summaryType, periodStart, periodEnd)])

    override suspend fun saveSummary(summary: WorkSummary): DataResult<WorkSummary> =
        DataResult.Success(
            summary
                .copy(
                    id =
                        values[key(summary.summaryType, summary.periodStart, summary.periodEnd)]?.id ?: summary.id,
                ).also {
                    values[key(it.summaryType, it.periodStart, it.periodEnd)] =
                        it
                },
        )

    override suspend fun updateStatus(
        summaryType: SummaryType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        status: SummaryStatus,
        errorMessage: String?,
    ): DataResult<Unit> {
        val current =
            values[key(summaryType, periodStart, periodEnd)] ?: return DataResult.Failure(DataError.NotFound)
        values[key(summaryType, periodStart, periodEnd)] =
            current.copy(status = status, errorMessage = errorMessage)
        return DataResult.Success(Unit)
    }

    override suspend fun saveGeneratedContent(content: GeneratedSummaryContent): DataResult<Unit> {
        val current =
            values[key(content.summaryType, content.periodStart, content.periodEnd)]
                ?: return DataResult.Failure(DataError.NotFound)
        values[key(content.summaryType, content.periodStart, content.periodEnd)] =
            current.copy(
                status = SummaryStatus.SUCCESS,
                sourceHash = content.sourceHash,
                aiProvider = content.aiProvider,
                modelName = content.modelName,
                originalContent = content.originalContent,
                editedContent = content.editedContent,
                generatedAt = content.generatedAt,
            )
        return DataResult.Success(Unit)
    }

    override suspend fun updateEditedContent(
        summaryType: SummaryType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        editedContent: String?,
    ): DataResult<Unit> = DataResult.Success(Unit)

    override suspend fun getNotSuccessfulSummaries(summaryType: SummaryType): DataResult<List<WorkSummary>> =
        DataResult.Success(emptyList())

    override suspend fun deleteSummary(
        summaryType: SummaryType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): DataResult<Unit> = DataResult.Success(Unit)
}

private class CountingProvider(
    private val fail: Boolean = false,
) : AiSummaryProvider {
    var calls = 0
    override val providerId: String = "counting"

    override suspend fun generateSummary(
        request: com.worklogai.app.ai.model.AiSummaryRequest,
    ): Result<com.worklogai.app.ai.model.AiSummaryResponse> {
        calls++
        return if (fail) Result.failure(IllegalStateException()) else MockAiSummaryProvider().generateSummary(request)
    }
}

private class FailingProvider(
    private val error: Throwable,
) : AiSummaryProvider {
    override val providerId: String = "failing"

    override suspend fun generateSummary(
        request: com.worklogai.app.ai.model.AiSummaryRequest,
    ): Result<AiSummaryResponse> = Result.failure(error)
}

private class InvalidJsonProvider : AiSummaryProvider {
    var calls = 0
    override val providerId: String = "invalid-json"

    override suspend fun generateSummary(
        request: com.worklogai.app.ai.model.AiSummaryRequest,
    ): Result<AiSummaryResponse> {
        calls++
        return Result.success(
            AiSummaryResponse(
                content = "not-json",
                providerId = providerId,
                model = request.model,
            ),
        )
    }
}

private fun summary(
    id: String,
    status: SummaryStatus,
    original: String?,
    edited: String?,
): WorkSummary {
    val now = Instant.parse("2026-07-13T00:00:00Z")
    return WorkSummary(
        id,
        SummaryType.WEEKLY,
        LocalDate.of(2026, 7, 13),
        LocalDate.of(2026, 7, 19),
        status,
        "hash",
        "mock",
        "mock",
        original,
        edited,
        null,
        now,
        now,
        now,
    )
}

private fun key(
    type: SummaryType,
    start: LocalDate,
    end: LocalDate,
): String = "$type:$start:$end"
