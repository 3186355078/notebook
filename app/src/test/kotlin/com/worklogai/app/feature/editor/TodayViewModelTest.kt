package com.worklogai.app.feature.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.worklogai.app.core.attachment.AttachmentFileStore
import com.worklogai.app.core.attachment.CleanupResult
import com.worklogai.app.core.attachment.StoredImage
import com.worklogai.app.core.common.id.IdGenerator
import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.model.Attachment
import com.worklogai.app.core.model.AttachmentDraft
import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.WorkEntry
import com.worklogai.app.core.repository.WorkEntryRepository
import com.worklogai.app.core.table.DefaultTableContentEditor
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val date = LocalDate.of(2026, 7, 12)
    private lateinit var repository: FakeWorkEntryRepository
    private lateinit var fileStore: FakeAttachmentFileStore
    private lateinit var viewModel: TodayViewModel

    @Before
    fun setUp() {
        repository = FakeWorkEntryRepository()
        fileStore = FakeAttachmentFileStore()
        viewModel =
            TodayViewModel(
                workEntryRepository = repository,
                attachmentFileStore = fileStore,
                tableContentEditor = DefaultTableContentEditor(SequenceIdGenerator()),
                timeProvider = FixedTimeProvider(Instant.parse("2026-07-12T08:00:00Z")),
                savedStateHandle = SavedStateHandle(mapOf("today.selected_date" to date.toString())),
            )
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
    }

    @Test
    fun `initialization creates and observes today's entry`() {
        assertEquals(listOf(date), repository.getOrCreateDates)
        assertEquals("entry-2026-07-12", viewModel.uiState.value.entryId)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `load failure shows a recoverable state and retry reloads the entry`() {
        val failingRepository = FakeWorkEntryRepository().apply { failNextLoad = true }
        val failingViewModel =
            TodayViewModel(
                workEntryRepository = failingRepository,
                attachmentFileStore = FakeAttachmentFileStore(),
                tableContentEditor = DefaultTableContentEditor(SequenceIdGenerator()),
                timeProvider = FixedTimeProvider(Instant.parse("2026-07-12T08:00:00Z")),
                savedStateHandle = SavedStateHandle(mapOf("today.selected_date" to date.toString())),
            )
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        assertNull(failingViewModel.uiState.value.entryId)
        assertEquals("工作记录加载失败", failingViewModel.uiState.value.errorMessage)

        failingViewModel.onAction(TodayAction.RetryLoad)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals("entry-2026-07-12", failingViewModel.uiState.value.entryId)
    }

    @Test
    fun `add text block appends it and requests focus`() {
        viewModel.onAction(TodayAction.AddTextBlock)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val block =
            viewModel.uiState.value.blocks
                .single() as TextBlockUiModel
        assertEquals(block.id, viewModel.uiState.value.focusedBlockId)
        assertEquals("", block.text)
    }

    @Test
    fun `table edits keep a full draft and save the latest structure`() {
        viewModel.onAction(TodayAction.AddTableBlock)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        val table =
            viewModel.uiState.value.blocks
                .filterIsInstance<TableBlockUiModel>()
                .single()
        val row = table.content.rows.first()
        val column = table.content.columns.first()

        viewModel.onAction(TodayAction.TableCellChanged(table.id, row.id, column.id, "完成联调"))
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(600)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "完成联调",
            repository.tableUpdates
                .single()
                .second.rows
                .first()
                .cells[column.id],
        )
        assertTrue(viewModel.uiState.value.saveState is SaveState.Saved)
    }

    @Test
    fun `selected image is imported and added as an image block`() {
        fileStore.storedImage = StoredImage("images/test.jpg", "image/jpeg", 100L, 40, 20)

        viewModel.onAction(TodayAction.ImageSelected(mockk<Uri>()))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "images/test.jpg",
            viewModel.uiState.value.blocks
                .filterIsInstance<ImageBlockUiModel>()
                .single()
                .relativePath,
        )
    }

    @Test
    fun `typing updates UI immediately and debounce saves only latest content`() {
        val block = repository.addText(date, "")
        repository.emit(date)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(TodayAction.TextChanged(block.id, "第一版"))
        viewModel.onAction(TodayAction.TextChanged(block.id, "最终版本"))
        assertEquals("最终版本", textBlock(block.id).text)
        assertTrue(repository.textUpdates.isEmpty())

        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(599)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        assertTrue(repository.textUpdates.isEmpty())

        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(1)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(block.id to "最终版本"), repository.textUpdates)
        assertTrue(viewModel.uiState.value.saveState is SaveState.Saved)
    }

    @Test
    fun `repository's older emission does not replace an unsaved draft`() {
        val block = repository.addText(date, "数据库旧内容")
        repository.emit(date)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(TodayAction.TextChanged(block.id, "当前草稿"))
        repository.emit(date)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals("当前草稿", textBlock(block.id).text)
    }

    @Test
    fun `different text blocks keep independent debounce saves`() {
        val first = repository.addText(date, "")
        val second = repository.addText(date, "")
        repository.emit(date)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(TodayAction.TextChanged(first.id, "A 块"))
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(300)
        viewModel.onAction(TodayAction.TextChanged(second.id, "B 块"))
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(300)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        assertEquals(listOf(first.id to "A 块"), repository.textUpdates)

        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(300)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(first.id to "A 块", second.id to "B 块"), repository.textUpdates)
    }

    @Test
    fun `failed save keeps draft and retry saves its latest value`() {
        val block = repository.addText(date, "旧内容")
        repository.emit(date)
        repository.failNextTextUpdate = true
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(TodayAction.TextChanged(block.id, "仍要保留"))
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(600)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals("仍要保留", textBlock(block.id).text)
        assertTrue(viewModel.uiState.value.saveState is SaveState.Failed)

        viewModel.onAction(TodayAction.RetryFailedSaves)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(block.id to "仍要保留", block.id to "仍要保留"), repository.textUpdates)
        assertTrue(viewModel.uiState.value.saveState is SaveState.Saved)
    }

    @Test
    fun `flush saves pending edits without waiting for debounce`() {
        val block = repository.addText(date, "")
        repository.emit(date)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(TodayAction.TextChanged(block.id, "立即保存"))
        viewModel.onAction(TodayAction.FlushPendingEdits)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(block.id to "立即保存"), repository.textUpdates)
    }

    @Test
    fun `move down sends the complete ordered id list and updates UI`() {
        val first = repository.addText(date, "一")
        val second = repository.addText(date, "二")
        val third = repository.addText(date, "三")
        repository.emit(date)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(TodayAction.MoveBlockDown(first.id))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(second.id, first.id, third.id), repository.reorderRequests.single())
        assertEquals(
            listOf(second.id, first.id, third.id),
            viewModel.uiState.value.blocks
                .map { it.id },
        )
    }

    @Test
    fun `blank block deletes immediately while nonblank block asks for confirmation`() {
        val blank = repository.addText(date, "")
        val nonBlank = repository.addText(date, "有内容")
        repository.emit(date)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(TodayAction.RequestDeleteBlock(blank.id))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(blank.id), repository.deletedBlockIds)
        assertNull(viewModel.uiState.value.pendingDeleteBlockId)

        viewModel.onAction(TodayAction.RequestDeleteBlock(nonBlank.id))
        assertEquals(nonBlank.id, viewModel.uiState.value.pendingDeleteBlockId)
        viewModel.onAction(TodayAction.ConfirmDeleteBlock(nonBlank.id))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(blank.id, nonBlank.id), repository.deletedBlockIds)
    }

    @Test
    fun `delete failure keeps the block in the current UI state`() {
        val block = repository.addText(date, "保留我")
        repository.emit(date)
        repository.failNextDelete = true
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(TodayAction.RequestDeleteBlock(block.id))
        viewModel.onAction(TodayAction.ConfirmDeleteBlock(block.id))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            viewModel.uiState.value.blocks
                .any { it.id == block.id },
        )
        assertTrue(repository.deletedBlockIds.isEmpty())
    }

    @Test
    fun `date change flushes prior draft and enters future planning without creating work entry`() {
        val block = repository.addText(date, "")
        val nextDate = date.plusDays(1)
        repository.emit(date)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        viewModel.onAction(TodayAction.TextChanged(block.id, "昨日草稿"))
        viewModel.onAction(TodayAction.DateChanged(nextDate))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(block.id to "昨日草稿"), repository.textUpdates)
        assertEquals(nextDate, viewModel.uiState.value.date)
        assertTrue(
            viewModel.uiState.value.blocks
                .isEmpty(),
        )
        assertTrue(viewModel.uiState.value.isFuturePlanning)
        assertEquals(listOf(date), repository.getOrCreateDates)
    }

    @Test
    fun `database failure after image import compensates by deleting stored file`() {
        fileStore.storedImage = StoredImage("images/failure.jpg", "image/jpeg", 100L, 40, 20)
        repository.failNextAddImage = true

        viewModel.onAction(TodayAction.ImageSelected(mockk<Uri>()))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("images/failure.jpg"), fileStore.deletedPaths)
        assertTrue(
            viewModel.uiState.value.blocks
                .filterIsInstance<ImageBlockUiModel>()
                .isEmpty(),
        )
    }

    @Test
    fun `image caption draft debounces and older repository data does not overwrite it`() {
        val image = addImageForEditing()

        viewModel.onAction(TodayAction.ImageCaptionChanged(image.id, "current caption"))
        repository.emit(date)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals("current caption", imageBlock(image.id).caption)
        assertTrue(repository.imageCaptionUpdates.isEmpty())
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(600)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(image.attachmentId!! to "current caption"), repository.imageCaptionUpdates)
        assertTrue(viewModel.uiState.value.saveState is SaveState.Saved)
    }

    @Test
    fun `failed image caption keeps newest draft and retry saves newest value`() {
        val image = addImageForEditing()
        repository.failNextCaptionUpdate = true

        viewModel.onAction(TodayAction.ImageCaptionChanged(image.id, "first"))
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(600)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(TodayAction.ImageCaptionChanged(image.id, "latest"))
        viewModel.onAction(TodayAction.RetryFailedSaves)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals("latest", imageBlock(image.id).caption)
        assertEquals(
            listOf(image.attachmentId!! to "first", image.attachmentId to "latest"),
            repository.imageCaptionUpdates,
        )
        assertTrue(viewModel.uiState.value.saveState is SaveState.Saved)
    }

    @Test
    fun `date change flushes image caption and isolates the following date`() {
        val image = addImageForEditing()
        val nextDate = date.plusDays(1)

        viewModel.onAction(TodayAction.ImageCaptionChanged(image.id, "flush before switch"))
        viewModel.onAction(TodayAction.DateChanged(nextDate))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(image.attachmentId!! to "flush before switch"), repository.imageCaptionUpdates)
        assertEquals(nextDate, viewModel.uiState.value.date)
        assertTrue(
            viewModel.uiState.value.blocks
                .isEmpty(),
        )
    }

    @Test
    fun `deleting image cancels caption draft and deletes its controlled file`() {
        val image = addImageForEditing()
        viewModel.onAction(TodayAction.ImageCaptionChanged(image.id, "unsaved caption"))

        viewModel.onAction(TodayAction.RequestDeleteBlock(image.id))
        viewModel.onAction(TodayAction.ConfirmDeleteBlock(image.id))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            viewModel.uiState.value.blocks
                .none { it.id == image.id },
        )
        assertEquals(listOf("images/test.jpg"), fileStore.deletedPaths)
        assertTrue(repository.imageCaptionUpdates.isEmpty())
    }

    @Test
    fun `table draft wins over older repository emission and saves the final full content`() {
        val table = addTableForEditing()
        val firstColumn = table.content.columns.first()
        val firstRow = table.content.rows.first()

        viewModel.onAction(TodayAction.TableTitleChanged(table.id, "release"))
        viewModel.onAction(TodayAction.TableCellChanged(table.id, firstRow.id, firstColumn.id, "done"))
        repository.emit(date)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals("release", tableBlock(table.id).content.title)
        assertEquals(
            "done",
            tableBlock(table.id)
                .content.rows
                .first()
                .cells[firstColumn.id],
        )
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(600)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        val saved = repository.tableUpdates.single().second
        assertEquals("release", saved.title)
        assertEquals("done", saved.rows.first().cells[firstColumn.id])
    }

    @Test
    fun `table save failure retains complete draft and retry saves latest table`() {
        val table = addTableForEditing()
        val column = table.content.columns.first()
        val row = table.content.rows.first()
        repository.failNextTableUpdate = true

        viewModel.onAction(TodayAction.TableTitleChanged(table.id, "first"))
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(600)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(TodayAction.TableCellChanged(table.id, row.id, column.id, "latest cell"))
        viewModel.onAction(TodayAction.RetryFailedSaves)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals("first", tableBlock(table.id).content.title)
        assertEquals(
            "latest cell",
            tableBlock(table.id)
                .content.rows
                .first()
                .cells[column.id],
        )
        assertEquals(
            "latest cell",
            repository.tableUpdates
                .last()
                .second.rows
                .first()
                .cells[column.id],
        )
        assertTrue(viewModel.uiState.value.saveState is SaveState.Saved)
    }

    @Test
    fun `date change flushes whole table draft without leaving it in new date`() {
        val table = addTableForEditing()
        val nextDate = date.plusDays(1)
        val column = table.content.columns.first()
        val row = table.content.rows.first()

        viewModel.onAction(TodayAction.TableCellChanged(table.id, row.id, column.id, "flush table"))
        viewModel.onAction(TodayAction.DateChanged(nextDate))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "flush table",
            repository.tableUpdates
                .single()
                .second.rows
                .first()
                .cells[column.id],
        )
        assertEquals(nextDate, viewModel.uiState.value.date)
        assertTrue(
            viewModel.uiState.value.blocks
                .isEmpty(),
        )
    }

    @Test
    fun `table is flushed before sorting and continues to have same content`() {
        val text = repository.addText(date, "text")
        repository.emit(date)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        val table = addTableForEditing()
        val column = table.content.columns.first()
        val row = table.content.rows.first()

        viewModel.onAction(TodayAction.TableCellChanged(table.id, row.id, column.id, "keep"))
        viewModel.onAction(TodayAction.MoveBlockUp(table.id))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(table.id, text.id), repository.reorderRequests.last())
        assertEquals(
            "keep",
            tableBlock(table.id)
                .content.rows
                .first()
                .cells[column.id],
        )
        assertEquals(
            "keep",
            repository.tableUpdates
                .last()
                .second.rows
                .first()
                .cells[column.id],
        )
    }

    @Test
    fun `blank image caption is normalized to null after its debounce`() {
        val image = addImageForEditing()

        viewModel.onAction(TodayAction.ImageCaptionChanged(image.id, "   "))
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(600)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(image.attachmentId!! to null), repository.imageCaptionUpdates)
        assertEquals("", imageBlock(image.id).caption)
    }

    @Test
    fun `flush pending edits saves caption and table drafts without debounce delay`() {
        val image = addImageForEditing()
        val table = addTableForEditing()
        val row = table.content.rows.first()
        val column = table.content.columns.first()

        viewModel.onAction(TodayAction.ImageCaptionChanged(image.id, "flush caption"))
        viewModel.onAction(TodayAction.TableCellChanged(table.id, row.id, column.id, "flush cell"))
        viewModel.onAction(TodayAction.FlushPendingEdits)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(image.attachmentId!! to "flush caption"), repository.imageCaptionUpdates)
        assertEquals(
            "flush cell",
            repository.tableUpdates
                .single()
                .second.rows
                .first()
                .cells[column.id],
        )
    }

    @Test
    fun `image caption is flushed before image sorting`() {
        val text = repository.addText(date, "text")
        repository.emit(date)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        val image = addImageForEditing()

        viewModel.onAction(TodayAction.ImageCaptionChanged(image.id, "caption before sort"))
        viewModel.onAction(TodayAction.MoveBlockUp(image.id))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(image.attachmentId!! to "caption before sort"), repository.imageCaptionUpdates)
        assertEquals(listOf(image.id, text.id), repository.reorderRequests.last())
        assertEquals("caption before sort", imageBlock(image.id).caption)
    }

    @Test
    fun `table structural draft survives stale repository emission`() {
        val table = addTableForEditing()

        viewModel.onAction(TodayAction.AddTableRow(table.id))
        viewModel.onAction(TodayAction.AddTableColumn(table.id))
        repository.emit(date)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        val draft = tableBlock(table.id).content
        assertEquals(3, draft.rows.size)
        assertEquals(3, draft.columns.size)
        assertTrue(draft.rows.all { draft.columns.last().id in it.cells })
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(600)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(
            3,
            repository.tableUpdates
                .last()
                .second.rows.size,
        )
        assertEquals(
            3,
            repository.tableUpdates
                .last()
                .second.columns.size,
        )
    }

    @Test
    fun `deleting table cancels its pending save and removes the draft`() {
        val table = addTableForEditing()
        val row = table.content.rows.first()
        val column = table.content.columns.first()

        viewModel.onAction(TodayAction.TableCellChanged(table.id, row.id, column.id, "unsaved"))
        viewModel.onAction(TodayAction.RequestDeleteBlock(table.id))
        viewModel.onAction(TodayAction.ConfirmDeleteBlock(table.id))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            viewModel.uiState.value.blocks
                .none { it.id == table.id },
        )
        assertTrue(repository.tableUpdates.isEmpty())
    }

    private fun addImageForEditing(): ImageBlockUiModel {
        fileStore.storedImage = StoredImage("images/test.jpg", "image/jpeg", 100L, 40, 20)
        viewModel.onAction(TodayAction.ImageSelected(mockk<Uri>()))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        return viewModel.uiState.value.blocks
            .filterIsInstance<ImageBlockUiModel>()
            .single()
    }

    private fun addTableForEditing(): TableBlockUiModel {
        viewModel.onAction(TodayAction.AddTableBlock)
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        return viewModel.uiState.value.blocks
            .filterIsInstance<TableBlockUiModel>()
            .single()
    }

    private fun imageBlock(id: String): ImageBlockUiModel =
        viewModel.uiState.value.blocks
            .filterIsInstance<ImageBlockUiModel>()
            .first { it.id == id }

    private fun tableBlock(id: String): TableBlockUiModel =
        viewModel.uiState.value.blocks
            .filterIsInstance<TableBlockUiModel>()
            .first { it.id == id }

    private fun textBlock(id: String): TextBlockUiModel {
        val block =
            viewModel.uiState.value.blocks
                .first { it.id == id }
        assertTrue(block is TextBlockUiModel)
        return block as TextBlockUiModel
    }

    @Test
    fun `fixed date editor remains on selected history date when current date changes`() {
        val historyDate = LocalDate.of(2026, 7, 8)
        val fixedDateViewModel =
            TodayViewModel(
                workEntryRepository = repository,
                attachmentFileStore = fileStore,
                tableContentEditor = DefaultTableContentEditor(SequenceIdGenerator()),
                timeProvider = FixedTimeProvider(Instant.parse("2026-07-12T08:00:00Z")),
                savedStateHandle = SavedStateHandle(mapOf("entryDate" to historyDate.toString())),
            )
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        fixedDateViewModel.onAction(TodayAction.DateChanged(date))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(historyDate, fixedDateViewModel.uiState.value.date)
        assertFalse(fixedDateViewModel.uiState.value.followsCurrentDate)
    }
}

private class FixedTimeProvider(
    private val instant: Instant,
) : TimeProvider {
    override fun now(): Instant = instant
}

private class SequenceIdGenerator : IdGenerator {
    private var nextId = 1

    override fun generate(): String = "table-id-${nextId++}"
}

private class FakeAttachmentFileStore : AttachmentFileStore {
    var storedImage: StoredImage? = null
    val deletedPaths = mutableListOf<String>()

    override suspend fun importImage(sourceUri: Uri): Result<StoredImage> =
        storedImage?.let { Result.success(it) } ?: Result.failure(IllegalStateException("not configured"))

    override suspend fun delete(relativePath: String): Result<Unit> {
        deletedPaths += relativePath
        return Result.success(Unit)
    }

    override suspend fun exists(relativePath: String): Boolean = false

    override suspend fun cleanupOrphans(referencedPaths: Set<String>): CleanupResult = CleanupResult(0, 0)

    override fun fileFor(relativePath: String): java.io.File? = null
}

@Suppress("TooManyFunctions")
private class FakeWorkEntryRepository : WorkEntryRepository {
    private val entries = mutableMapOf<LocalDate, WorkEntry>()
    private val entryFlows = mutableMapOf<LocalDate, MutableStateFlow<DataResult<WorkEntry?>>>()
    private var nextBlockId = 1

    val getOrCreateDates = mutableListOf<LocalDate>()
    val textUpdates = mutableListOf<Pair<String, String>>()
    val tableUpdates = mutableListOf<Pair<String, TableContent>>()
    val imageCaptionUpdates = mutableListOf<Pair<String, String?>>()
    val reorderRequests = mutableListOf<List<String>>()
    val deletedBlockIds = mutableListOf<String>()
    var failNextDelete = false
    var failNextLoad = false
    var failNextTextUpdate = false
    var failNextTableUpdate = false
    var failNextCaptionUpdate = false
    var failNextAddImage = false

    override fun observeEntry(date: LocalDate): Flow<DataResult<WorkEntry?>> = flowFor(date).asStateFlow()

    override suspend fun getEntry(date: LocalDate): DataResult<WorkEntry?> = DataResult.Success(entries[date])

    override suspend fun getEntries(
        startDate: LocalDate,
        endDate: LocalDate,
    ): DataResult<List<WorkEntry>> =
        DataResult.Success(entries.filterKeys { it in startDate..endDate }.values.sortedBy(WorkEntry::entryDate))

    override suspend fun getOrCreateEntry(date: LocalDate): DataResult<WorkEntry> {
        getOrCreateDates += date
        if (failNextLoad) {
            failNextLoad = false
            return DataResult.Failure(DataError.Storage)
        }
        val entry = entries.getOrPut(date) { emptyEntry(date) }
        flowFor(date).value = DataResult.Success(entry)
        return DataResult.Success(entry)
    }

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
    ): DataResult<ContentBlock.Text> {
        val date = entries.entries.firstOrNull { it.value.id == entryId }?.key ?: return missing()
        val current = entries.getValue(date)
        val block =
            ContentBlock.Text(
                id = "block-${nextBlockId++}",
                entryId = entryId,
                order = current.blocks.size,
                content = text,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
        entries[date] = current.copy(blocks = current.blocks + block)
        flowFor(date).value = DataResult.Success(entries.getValue(date))
        return DataResult.Success(block)
    }

    override suspend fun addTableBlock(
        entryId: String,
        tableContent: TableContent,
    ): DataResult<ContentBlock.Table> {
        val date = entries.entries.firstOrNull { it.value.id == entryId }?.key ?: return missing()
        val entry = entries.getValue(date)
        val block =
            ContentBlock.Table(
                id = "block-${nextBlockId++}",
                entryId = entryId,
                order = entry.blocks.size,
                content = tableContent,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
        entries[date] = entry.copy(blocks = entry.blocks + block)
        flowFor(date).value = DataResult.Success(entries.getValue(date))
        return DataResult.Success(block)
    }

    override suspend fun addImageBlock(entryId: String): DataResult<ContentBlock.Image> = missing()

    override suspend fun addImageBlock(
        entryId: String,
        attachment: AttachmentDraft,
    ): DataResult<ContentBlock.Image> =
        if (failNextAddImage) {
            failImageSave()
        } else {
            addPersistedImageBlock(entryId, attachment)
        }

    private fun failImageSave(): DataResult<ContentBlock.Image> {
        failNextAddImage = false
        return DataResult.Failure(DataError.Storage)
    }

    private fun addPersistedImageBlock(
        entryId: String,
        attachment: AttachmentDraft,
    ): DataResult<ContentBlock.Image> {
        val date = entries.entries.firstOrNull { it.value.id == entryId }?.key ?: return missing()
        val entry = entries.getValue(date)
        val blockId = "block-${nextBlockId++}"
        val block =
            ContentBlock.Image(
                id = blockId,
                entryId = entryId,
                order = entry.blocks.size,
                attachments =
                    listOf(
                        Attachment(
                            id = "attachment-$blockId",
                            blockId = blockId,
                            localPath = attachment.localPath,
                            mimeType = attachment.mimeType,
                            fileSize = attachment.fileSize,
                            width = attachment.width,
                            height = attachment.height,
                            caption = attachment.caption,
                            createdAt = Instant.EPOCH,
                        ),
                    ),
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
        entries[date] = entry.copy(blocks = entry.blocks + block)
        flowFor(date).value = DataResult.Success(entries.getValue(date))
        return DataResult.Success(block)
    }

    override suspend fun updateTextBlock(
        blockId: String,
        text: String,
    ): DataResult<ContentBlock.Text> {
        textUpdates += blockId to text
        return if (failNextTextUpdate) {
            failNextTextUpdate = false
            DataResult.Failure(DataError.Storage)
        } else {
            updatePersistedTextBlock(blockId, text)
        }
    }

    private fun updatePersistedTextBlock(
        blockId: String,
        text: String,
    ): DataResult<ContentBlock.Text> {
        val entryWithBlock =
            entries.entries.firstOrNull { (_, entry) ->
                entry.blocks.any { it.id == blockId }
            } ?: return missing()
        val updated =
            entryWithBlock.value.blocks.map { block ->
                if (block.id == blockId && block is ContentBlock.Text) block.copy(content = text) else block
            }
        entries[entryWithBlock.key] = entryWithBlock.value.copy(blocks = updated)
        flowFor(entryWithBlock.key).value = DataResult.Success(entries.getValue(entryWithBlock.key))
        return DataResult.Success(
            updated
                .filterIsInstance<ContentBlock.Text>()
                .first { it.id == blockId },
        )
    }

    override suspend fun updateTableBlock(
        blockId: String,
        tableContent: TableContent,
    ): DataResult<ContentBlock.Table> {
        tableUpdates += blockId to tableContent
        return if (failNextTableUpdate) {
            failTableSave()
        } else {
            updatePersistedTableBlock(blockId, tableContent)
        }
    }

    private fun failTableSave(): DataResult<ContentBlock.Table> {
        failNextTableUpdate = false
        return DataResult.Failure(DataError.Storage)
    }

    private fun updatePersistedTableBlock(
        blockId: String,
        tableContent: TableContent,
    ): DataResult<ContentBlock.Table> {
        val date =
            entries.entries.firstOrNull { (_, entry) -> entry.blocks.any { it.id == blockId } }?.key ?: return missing()
        val current = entries.getValue(date)
        val updated =
            current.blocks.map { block ->
                if (block is ContentBlock.Table &&
                    block.id == blockId
                ) {
                    block.copy(content = tableContent)
                } else {
                    block
                }
            }
        val table = updated.filterIsInstance<ContentBlock.Table>().first { it.id == blockId }
        entries[date] = current.copy(blocks = updated)
        flowFor(date).value = DataResult.Success(entries.getValue(date))
        return DataResult.Success(table)
    }

    override suspend fun reorderBlocks(
        entryId: String,
        orderedBlockIds: List<String>,
    ): DataResult<Unit> {
        reorderRequests += orderedBlockIds
        val date = entries.entries.firstOrNull { it.value.id == entryId }?.key ?: return missing()
        val current = entries.getValue(date)
        val order = orderedBlockIds.withIndex().associate { (index, id) -> id to index }
        entries[date] =
            current.copy(
                blocks =
                    current.blocks
                        .sortedBy { order.getValue(it.id) }
                        .mapIndexed { index, block -> block.withOrder(index) },
            )
        flowFor(date).value = DataResult.Success(entries.getValue(date))
        return DataResult.Success(Unit)
    }

    override suspend fun deleteBlock(blockId: String): DataResult<List<String>> =
        if (failNextDelete) {
            failNextDelete = false
            DataResult.Failure(DataError.Storage)
        } else {
            deletePersistedBlock(blockId)
        }

    private fun deletePersistedBlock(blockId: String): DataResult<List<String>> {
        val entryWithBlock =
            entries.entries.firstOrNull { (_, entry) -> entry.blocks.any { it.id == blockId } } ?: return missing()
        deletedBlockIds += blockId
        val block = entryWithBlock.value.blocks.first { it.id == blockId }
        val paths = (block as? ContentBlock.Image)?.attachments?.map(Attachment::localPath).orEmpty()
        entries[entryWithBlock.key] =
            entryWithBlock.value.copy(blocks = entryWithBlock.value.blocks.filterNot { it.id == blockId })
        flowFor(entryWithBlock.key).value = DataResult.Success(entries.getValue(entryWithBlock.key))
        return DataResult.Success(paths)
    }

    override suspend fun addAttachment(
        blockId: String,
        draft: AttachmentDraft,
    ): DataResult<Attachment> = missing()

    override suspend fun deleteAttachment(attachmentId: String): DataResult<String> = missing()

    override suspend fun updateImageCaption(
        attachmentId: String,
        caption: String?,
    ): DataResult<Attachment> {
        imageCaptionUpdates += attachmentId to caption
        return if (failNextCaptionUpdate) {
            failCaptionSave()
        } else {
            updatePersistedCaption(attachmentId, caption)
        }
    }

    private fun failCaptionSave(): DataResult<Attachment> {
        failNextCaptionUpdate = false
        return DataResult.Failure(DataError.Storage)
    }

    private fun updatePersistedCaption(
        attachmentId: String,
        caption: String?,
    ): DataResult<Attachment> {
        val entryWithImage =
            entries.entries.firstOrNull { (_, entry) ->
                entry.blocks.filterIsInstance<ContentBlock.Image>().any { image ->
                    image.attachments.any { it.id == attachmentId }
                }
            } ?: return missing()
        val attachment =
            entryWithImage.value.blocks
                .filterIsInstance<ContentBlock.Image>()
                .flatMap(ContentBlock.Image::attachments)
                .first { it.id == attachmentId }
                .copy(caption = caption)
        entries[entryWithImage.key] =
            entryWithImage.value.copy(
                blocks =
                    entryWithImage.value.blocks.map { block ->
                        if (block is ContentBlock.Image && block.attachments.any { it.id == attachmentId }) {
                            block.copy(
                                attachments =
                                    block.attachments.map {
                                        if (it.id ==
                                            attachmentId
                                        ) {
                                            attachment
                                        } else {
                                            it
                                        }
                                    },
                            )
                        } else {
                            block
                        }
                    },
            )
        flowFor(entryWithImage.key).value = DataResult.Success(entries.getValue(entryWithImage.key))
        return DataResult.Success(attachment)
    }

    override suspend fun softDeleteEntry(entryId: String): DataResult<Unit> = DataResult.Success(Unit)

    override suspend fun restoreEntry(entryId: String): DataResult<Unit> = DataResult.Success(Unit)

    override suspend fun purgeEntry(entryId: String): DataResult<List<String>> = DataResult.Success(emptyList())

    fun addText(
        date: LocalDate,
        text: String,
    ): ContentBlock.Text {
        val entry = entries.getOrPut(date) { emptyEntry(date) }
        val block =
            ContentBlock.Text(
                id = "block-${nextBlockId++}",
                entryId = entry.id,
                order = entry.blocks.size,
                content = text,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )
        entries[date] = entry.copy(blocks = entry.blocks + block)
        return block
    }

    fun emit(date: LocalDate) {
        flowFor(date).value = DataResult.Success(entries[date])
    }

    private fun flowFor(date: LocalDate): MutableStateFlow<DataResult<WorkEntry?>> =
        entryFlows.getOrPut(date) { MutableStateFlow(DataResult.Success(entries[date])) }

    private fun ContentBlock.withOrder(order: Int): ContentBlock =
        when (this) {
            is ContentBlock.Image -> copy(order = order)
            is ContentBlock.Table -> copy(order = order)
            is ContentBlock.Text -> copy(order = order)
        }

    private fun emptyEntry(date: LocalDate) =
        WorkEntry(
            id = "entry-$date",
            entryDate = date,
            title = null,
            allowAiProcessing = true,
            isDeleted = false,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

    private fun <T> missing(): DataResult<T> = DataResult.Failure(DataError.NotFound)
}
