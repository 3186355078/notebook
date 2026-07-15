package com.worklogai.app.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worklogai.app.app.navigation.ENTRY_DATE_ARGUMENT
import com.worklogai.app.core.attachment.AttachmentFileStore
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.time.TimeProvider
import com.worklogai.app.core.model.AttachmentDraft
import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.WorkEntry
import com.worklogai.app.core.repository.WorkEntryRepository
import com.worklogai.app.core.table.TableContentEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

private const val SELECTED_DATE_KEY = "today.selected_date"
private const val TEXT_SAVE_DEBOUNCE_MILLIS = 600L
private const val SAVE_FAILED_MESSAGE = "保存失败，内容仍保留在当前页面"

// One date-scoped editor owns its drafts, serial saves, and lifecycle flush boundary.
@Suppress("TooManyFunctions", "LargeClass")
@HiltViewModel
class TodayViewModel
    @Inject
    constructor(
        private val workEntryRepository: WorkEntryRepository,
        private val attachmentFileStore: AttachmentFileStore,
        private val tableContentEditor: TableContentEditor,
        private val timeProvider: TimeProvider,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val fixedEntryDate = savedStateHandle.get<String>(ENTRY_DATE_ARGUMENT)?.toLocalDateOrNull()
        private val followsCurrentDate = fixedEntryDate == null
        private val initialDate =
            fixedEntryDate ?: savedStateHandle.get<String>(SELECTED_DATE_KEY)?.toLocalDateOrNull()
                ?: timeProvider.today()
        private val _uiState =
            MutableStateFlow(TodayUiState(date = initialDate, followsCurrentDate = followsCurrentDate))
        private val _events = Channel<TodayUiEvent>(Channel.BUFFERED)
        private val entriesByDate = mutableMapOf<LocalDate, WorkEntry?>()
        private val drafts = mutableMapOf<DraftKey, TextDraft>()
        private val tableDrafts = mutableMapOf<DraftKey, TableDraft>()
        private val captionDrafts = mutableMapOf<DraftKey, CaptionDraft>()
        private val saveJobs = mutableMapOf<DraftKey, Job>()
        private val waitingSaveKeys = mutableSetOf<DraftKey>()
        private val savingKeys = mutableSetOf<DraftKey>()
        private val failedSaveKeys = mutableSetOf<DraftKey>()
        private val lastSavedAtByDate = mutableMapOf<LocalDate, Instant>()
        private var observationJob: Job? = null
        private var dateSwitchJob: Job? = null

        val uiState = _uiState.asStateFlow()
        val events = _events.receiveAsFlow()

        init {
            loadDate(initialDate)
        }

        @Suppress("CyclomaticComplexMethod") // This is a direct, exhaustive UI action dispatcher.
        fun onAction(action: TodayAction) {
            when (action) {
                TodayAction.AddTableBlock -> addTableBlock()
                TodayAction.AddTextBlock -> addTextBlock()
                is TodayAction.AddTableColumn -> editTable(action.blockId) { tableContentEditor.addColumn(it) }
                is TodayAction.AddTableRow -> editTable(action.blockId) { tableContentEditor.addRow(it) }
                TodayAction.CancelDeleteBlock -> updateState { copy(pendingDeleteBlockId = null) }
                is TodayAction.ConfirmDeleteBlock -> deleteBlock(action.blockId)
                is TodayAction.DateChanged -> if (followsCurrentDate) switchDate(action.date)
                is TodayAction.DeleteTableColumn ->
                    editTable(
                        action.blockId,
                    ) { tableContentEditor.deleteColumn(it, action.columnId) }
                is TodayAction.DeleteTableRow ->
                    editTable(
                        action.blockId,
                    ) { tableContentEditor.deleteRow(it, action.rowId) }
                TodayAction.FlushPendingEdits -> flushCurrentDate()
                TodayAction.FocusRequestConsumed -> updateState { copy(focusedBlockId = null) }
                is TodayAction.ImageCaptionChanged -> changeImageCaption(action.blockId, action.caption)
                is TodayAction.ImageSelected -> importImage(action.uri)
                is TodayAction.MoveBlockDown -> moveBlock(action.blockId, direction = 1)
                is TodayAction.MoveBlockUp -> moveBlock(action.blockId, direction = -1)
                is TodayAction.RequestDeleteBlock -> requestDelete(action.blockId)
                TodayAction.RetryFailedSaves -> retryFailedSaves()
                TodayAction.RetryLoad -> loadDate(_uiState.value.date)
                is TodayAction.TextChanged -> changeText(action.blockId, action.text)
                is TodayAction.TextFocusChanged -> {
                    if (!action.isFocused) flushBlock(DraftKey(_uiState.value.date, action.blockId))
                }
                is TodayAction.TableCellChanged ->
                    editTable(action.blockId) {
                        tableContentEditor.updateCell(it, action.rowId, action.columnId, action.value)
                    }
                is TodayAction.TableColumnNameChanged ->
                    editTable(action.blockId) {
                        tableContentEditor.updateColumnName(it, action.columnId, action.name)
                    }
                is TodayAction.TableTitleChanged ->
                    editTable(
                        action.blockId,
                    ) { tableContentEditor.updateTitle(it, action.title) }
            }
        }

        private fun loadDate(date: LocalDate) {
            observationJob?.cancel()
            savedStateHandle[SELECTED_DATE_KEY] = date.toString()
            _uiState.value = TodayUiState(date = date, followsCurrentDate = followsCurrentDate)
            observationJob =
                viewModelScope.launch {
                    when (val result = workEntryRepository.getOrCreateEntry(date)) {
                        is DataResult.Failure -> showLoadFailure(date)
                        is DataResult.Success -> {
                            entriesByDate[date] = result.value
                            refreshUi(date, isLoading = false)
                            workEntryRepository.observeEntry(date).collect { observed ->
                                if (_uiState.value.date != date) return@collect
                                when (observed) {
                                    is DataResult.Failure -> showLoadFailure(date)
                                    is DataResult.Success -> {
                                        entriesByDate[date] = observed.value
                                        refreshUi(date, isLoading = false)
                                    }
                                }
                            }
                        }
                    }
                }
        }

        private fun showLoadFailure(date: LocalDate) {
            if (_uiState.value.date != date) return
            updateState {
                copy(
                    isLoading = false,
                    errorMessage = "工作记录加载失败",
                )
            }
        }

        private fun addTextBlock() {
            val date = _uiState.value.date
            val entryId = _uiState.value.entryId ?: return
            if (_uiState.value.isStructureOperationInProgress) return

            viewModelScope.launch {
                updateStateForDate(date) { copy(isStructureOperationInProgress = true, errorMessage = null) }
                when (val result = workEntryRepository.addTextBlock(entryId)) {
                    is DataResult.Failure -> emitMessage("无法添加记录，请重试")
                    is DataResult.Success -> {
                        updateEntry(date) { entry ->
                            entry.copy(blocks = entry.blocks + result.value)
                        }
                        updateStateForDate(date) { copy(focusedBlockId = result.value.id) }
                    }
                }
                updateStateForDate(date) { copy(isStructureOperationInProgress = false) }
                refreshUi(date)
            }
        }

        private fun addTableBlock() {
            val date = _uiState.value.date
            val entryId = _uiState.value.entryId ?: return
            if (_uiState.value.isStructureOperationInProgress) return
            viewModelScope.launch {
                updateStateForDate(date) { copy(isStructureOperationInProgress = true, errorMessage = null) }
                when (val result = workEntryRepository.addTableBlock(entryId, tableContentEditor.createDefault())) {
                    is DataResult.Failure -> emitMessage("无法创建表格")
                    is DataResult.Success ->
                        updateEntry(date) { entry ->
                            entry.copy(
                                blocks =
                                    entry.blocks + result.value,
                            )
                        }
                }
                updateStateForDate(date) { copy(isStructureOperationInProgress = false) }
                refreshUi(date)
            }
        }

        private fun importImage(uri: android.net.Uri) {
            val date = _uiState.value.date
            val entryId = _uiState.value.entryId ?: return
            if (_uiState.value.isImageImporting) return
            viewModelScope.launch {
                updateStateForDate(date) { copy(isImageImporting = true, errorMessage = null) }
                val stored = attachmentFileStore.importImage(uri)
                stored.fold(
                    onSuccess = { image ->
                        when (
                            val result =
                                workEntryRepository.addImageBlock(
                                    entryId,
                                    AttachmentDraft(
                                        localPath = image.relativePath,
                                        mimeType = image.mimeType,
                                        fileSize = image.fileSize,
                                        width = image.width,
                                        height = image.height,
                                    ),
                                )
                        ) {
                            is DataResult.Failure -> {
                                attachmentFileStore.delete(image.relativePath)
                                emitMessage("图片未能保存")
                            }
                            is DataResult.Success ->
                                updateEntry(date) { entry ->
                                    entry.copy(
                                        blocks =
                                            entry.blocks + result.value,
                                    )
                                }
                        }
                    },
                    onFailure = { emitMessage("图片处理失败，请重试") },
                )
                updateStateForDate(date) { copy(isImageImporting = false) }
                refreshUi(date)
            }
        }

        private fun changeImageCaption(
            blockId: String,
            caption: String,
        ) {
            val date = _uiState.value.date
            val attachment =
                (
                    entryFor(
                        date,
                    )?.blocks?.firstOrNull { it.id == blockId } as? ContentBlock.Image
                )?.attachments?.firstOrNull()
                    ?: return
            val key = DraftKey(date, blockId)
            captionDrafts[key] =
                CaptionDraft(
                    attachmentId = attachment.id,
                    caption = caption.trim().takeIf(String::isNotEmpty),
                    version = (captionDrafts[key]?.version ?: 0) + 1,
                )
            failedSaveKeys.remove(key)
            refreshUi(date)
            scheduleDebouncedSave(key)
        }

        private fun editTable(
            blockId: String,
            edit: (TableContent) -> TableContent?,
        ) {
            val date = _uiState.value.date
            val persisted = entryFor(date)?.blocks?.firstOrNull { it.id == blockId } as? ContentBlock.Table ?: return
            val key = DraftKey(date, blockId)
            val content = tableDrafts[key]?.content ?: persisted.content
            val updated = edit(content)
            if (updated == null) {
                emitMessage("已达到表格行列限制")
                return
            }
            tableDrafts[key] = TableDraft(updated, (tableDrafts[key]?.version ?: 0) + 1)
            failedSaveKeys.remove(key)
            refreshUi(date)
            scheduleDebouncedSave(key)
        }

        private fun changeText(
            blockId: String,
            text: String,
        ) {
            val date = _uiState.value.date
            if (entryFor(date)?.blocks?.any { it.id == blockId && it is ContentBlock.Text } != true) return

            val key = DraftKey(date, blockId)
            drafts[key] = TextDraft(text = text, version = (drafts[key]?.version ?: 0) + 1)
            failedSaveKeys.remove(key)
            refreshUi(date)
            scheduleDebouncedSave(key)
        }

        private fun scheduleDebouncedSave(key: DraftKey) {
            val existing = saveJobs[key]
            when {
                existing == null -> startSaveWorker(key, withDebounce = true)
                key in waitingSaveKeys -> {
                    existing.cancel()
                    startSaveWorker(key, withDebounce = true)
                }
            }
        }

        private fun startSaveWorker(
            key: DraftKey,
            withDebounce: Boolean,
        ) {
            val job =
                viewModelScope.launch {
                    try {
                        if (withDebounce) {
                            waitingSaveKeys += key
                            refreshUi(key.date)
                            delay(TEXT_SAVE_DEBOUNCE_MILLIS)
                            waitingSaveKeys -= key
                        }
                        saveLatestDraftUntilCurrent(key)
                    } finally {
                        waitingSaveKeys -= key
                        if (saveJobs[key] === coroutineContext[Job]) saveJobs.remove(key)
                        refreshUi(key.date)
                    }
                }
            saveJobs[key] = job
        }

        private suspend fun saveLatestDraftUntilCurrent(key: DraftKey) {
            var shouldContinue = true
            while (coroutineContext.isActive && shouldContinue) {
                shouldContinue =
                    when {
                        drafts[key] != null -> saveTextDraftVersion(key, drafts.getValue(key))
                        tableDrafts[key] != null -> saveTableDraftVersion(key, tableDrafts.getValue(key))
                        captionDrafts[key] != null -> saveCaptionDraftVersion(key, captionDrafts.getValue(key))
                        else -> false
                    }
            }
        }

        private suspend fun saveTextDraftVersion(
            key: DraftKey,
            draft: TextDraft,
        ): Boolean {
            savingKeys += key
            refreshUi(key.date)
            return when (val result = workEntryRepository.updateTextBlock(key.blockId, draft.text)) {
                is DataResult.Failure -> {
                    savingKeys -= key
                    failedSaveKeys += key
                    refreshUi(key.date)
                    emitMessage(SAVE_FAILED_MESSAGE)
                    false
                }

                is DataResult.Success -> {
                    savingKeys -= key
                    updatePersistedTextBlock(key.date, result.value)
                    if (drafts[key]?.version == draft.version) {
                        drafts.remove(key)
                        failedSaveKeys.remove(key)
                        lastSavedAtByDate[key.date] = timeProvider.now()
                        refreshUi(key.date)
                        false
                    } else {
                        true
                    }
                }
            }
        }

        private suspend fun saveTableDraftVersion(
            key: DraftKey,
            draft: TableDraft,
        ): Boolean =
            saveDraft(
                key = key,
                version = draft.version,
                source = tableDrafts,
                persist = { workEntryRepository.updateTableBlock(key.blockId, draft.content) },
                updatePersisted = { updated: ContentBlock.Table -> updatePersistedTableBlock(key.date, updated) },
            )

        private suspend fun saveCaptionDraftVersion(
            key: DraftKey,
            draft: CaptionDraft,
        ): Boolean =
            saveDraft(
                key = key,
                version = draft.version,
                source = captionDrafts,
                persist = { workEntryRepository.updateImageCaption(draft.attachmentId, draft.caption) },
                updatePersisted = { updated: com.worklogai.app.core.model.Attachment ->
                    updatePersistedAttachment(key.date, updated)
                },
            )

        private suspend fun <T, D : VersionedDraft> saveDraft(
            key: DraftKey,
            version: Int,
            source: MutableMap<DraftKey, D>,
            persist: suspend () -> DataResult<T>,
            updatePersisted: (T) -> Unit,
        ): Boolean {
            savingKeys += key
            refreshUi(key.date)
            return when (val result = persist()) {
                is DataResult.Failure -> {
                    savingKeys -= key
                    failedSaveKeys += key
                    refreshUi(key.date)
                    emitMessage(SAVE_FAILED_MESSAGE)
                    false
                }
                is DataResult.Success -> {
                    savingKeys -= key
                    updatePersisted(result.value)
                    if (source[key]?.version == version) {
                        source.remove(key)
                        failedSaveKeys.remove(key)
                        lastSavedAtByDate[key.date] = timeProvider.now()
                        refreshUi(key.date)
                        false
                    } else {
                        true
                    }
                }
            }
        }

        private fun updatePersistedTextBlock(
            date: LocalDate,
            updatedBlock: ContentBlock.Text,
        ) {
            updateEntry(date) { entry ->
                entry.copy(
                    blocks =
                        entry.blocks.map { block ->
                            if (block.id == updatedBlock.id) updatedBlock else block
                        },
                )
            }
        }

        private fun updatePersistedTableBlock(
            date: LocalDate,
            updatedBlock: ContentBlock.Table,
        ) {
            updateEntry(date) { entry ->
                entry.copy(
                    blocks =
                        entry.blocks.map {
                            if (it.id ==
                                updatedBlock.id
                            ) {
                                updatedBlock
                            } else {
                                it
                            }
                        },
                )
            }
        }

        private fun updatePersistedAttachment(
            date: LocalDate,
            updatedAttachment: com.worklogai.app.core.model.Attachment,
        ) {
            updateEntry(date) { entry ->
                entry.copy(
                    blocks =
                        entry.blocks.map { block ->
                            if (block is ContentBlock.Image &&
                                block.attachments.any { it.id == updatedAttachment.id }
                            ) {
                                block.copy(
                                    attachments =
                                        block.attachments.map {
                                            if (it.id ==
                                                updatedAttachment.id
                                            ) {
                                                updatedAttachment
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
            }
        }

        private fun flushCurrentDate() {
            viewModelScope.launch { flushDate(_uiState.value.date) }
        }

        private fun flushBlock(key: DraftKey) {
            if (!hasDraft(key)) return
            viewModelScope.launch { flushKeys(listOf(key)) }
        }

        private suspend fun flushDate(
            date: LocalDate,
            excludedBlockId: String? = null,
        ): Boolean {
            val pendingKeys =
                draftKeys().filter { key ->
                    key.date == date && key.blockId != excludedBlockId
                }
            flushKeys(pendingKeys)
            return pendingKeys.none(::hasDraft)
        }

        private suspend fun flushKeys(keys: List<DraftKey>) {
            keys.forEach { key ->
                if (key in waitingSaveKeys) {
                    saveJobs[key]?.cancel()
                    saveJobs[key]?.join()
                    startSaveWorker(key, withDebounce = false)
                }
            }
            keys.mapNotNull(saveJobs::get).distinct().joinAll()
        }

        private fun retryFailedSaves() {
            failedSaveKeys
                .filter { it.date == _uiState.value.date }
                .forEach { key ->
                    failedSaveKeys -= key
                    if (saveJobs[key] == null) startSaveWorker(key, withDebounce = false)
                }
            refreshUi(_uiState.value.date)
        }

        private fun moveBlock(
            blockId: String,
            direction: Int,
        ) {
            val date = _uiState.value.date
            val entry = entryFor(date)
            if (entry != null) {
                val sortedBlocks = entry.blocks.sortedBy(ContentBlock::order)
                val currentIndex = sortedBlocks.indexOfFirst { it.id == blockId }
                val targetIndex = currentIndex + direction
                val canMove =
                    !_uiState.value.isStructureOperationInProgress &&
                        currentIndex >= 0 &&
                        targetIndex in sortedBlocks.indices
                if (canMove) {
                    viewModelScope.launch {
                        movePersistedBlock(date, entry, currentIndex, targetIndex)
                    }
                }
            }
        }

        private suspend fun movePersistedBlock(
            date: LocalDate,
            entry: WorkEntry,
            currentIndex: Int,
            targetIndex: Int,
        ) {
            val hasFlushedDrafts = flushDate(date)
            val ordered = entryFor(date)?.blocks?.sortedBy(ContentBlock::order)?.toMutableList()
            if (!hasFlushedDrafts) {
                emitMessage("请先重试保存未保存的内容")
            } else if (ordered != null) {
                ordered.swap(currentIndex, targetIndex)
                updateState { copy(isStructureOperationInProgress = true, errorMessage = null) }
                when (workEntryRepository.reorderBlocks(entry.id, ordered.map(ContentBlock::id))) {
                    is DataResult.Failure -> emitMessage("调整顺序失败")
                    is DataResult.Success -> updateBlockOrder(date, ordered)
                }
                updateState { copy(isStructureOperationInProgress = false) }
                refreshUi(date)
            }
        }

        private fun updateBlockOrder(
            date: LocalDate,
            ordered: List<ContentBlock>,
        ) {
            updateEntry(date) { current ->
                current.copy(blocks = ordered.mapIndexed { index, block -> block.withOrder(index) })
            }
        }

        private fun requestDelete(blockId: String) {
            val block = _uiState.value.blocks.firstOrNull { it.id == blockId } ?: return
            if (block is TextBlockUiModel && block.text.isBlank()) {
                deleteBlock(blockId)
            } else {
                updateState { copy(pendingDeleteBlockId = blockId) }
            }
        }

        private fun deleteBlock(blockId: String) {
            val date = _uiState.value.date
            val entry = entryFor(date) ?: return
            if (_uiState.value.isStructureOperationInProgress) return

            viewModelScope.launch {
                updateState { copy(pendingDeleteBlockId = null, isStructureOperationInProgress = true) }
                cancelSaveForDeletion(DraftKey(date, blockId))
                if (!flushDate(date, excludedBlockId = blockId)) {
                    updateState { copy(isStructureOperationInProgress = false) }
                    emitMessage("请先重试保存未保存的内容")
                    return@launch
                }
                when (val deleteResult = workEntryRepository.deleteBlock(blockId)) {
                    is DataResult.Failure -> emitMessage("删除失败，请重试")
                    is DataResult.Success -> {
                        deleteResult.value.forEach { path -> attachmentFileStore.delete(path) }
                        val remaining = entryFor(date)?.blocks?.filterNot { it.id == blockId }.orEmpty()
                        val key = DraftKey(date, blockId)
                        drafts.remove(key)
                        tableDrafts.remove(key)
                        captionDrafts.remove(key)
                        failedSaveKeys.remove(key)
                        updateEntry(date) { current ->
                            current.copy(
                                blocks =
                                    remaining.mapIndexed {
                                        index,
                                        block,
                                        ->
                                        block.withOrder(index)
                                    },
                            )
                        }
                        if (remaining.isNotEmpty()) {
                            when (workEntryRepository.reorderBlocks(entry.id, remaining.map(ContentBlock::id))) {
                                is DataResult.Failure -> emitMessage("删除成功，但顺序未更新")
                                is DataResult.Success -> Unit
                            }
                        }
                    }
                }
                updateState { copy(isStructureOperationInProgress = false) }
                refreshUi(date)
            }
        }

        private suspend fun cancelSaveForDeletion(key: DraftKey) {
            if (key in waitingSaveKeys) saveJobs[key]?.cancel()
            saveJobs[key]?.join()
            saveJobs.remove(key)
            waitingSaveKeys -= key
            savingKeys -= key
        }

        private fun switchDate(date: LocalDate) {
            if (date == _uiState.value.date) return
            if (dateSwitchJob?.isActive == true) return
            dateSwitchJob =
                viewModelScope.launch {
                    val previousDate = _uiState.value.date
                    flushDate(previousDate)
                    if (isActive) loadDate(date)
                }
        }

        private fun refreshUi(
            date: LocalDate,
            isLoading: Boolean = _uiState.value.isLoading,
        ) {
            if (_uiState.value.date != date) return
            val entry = entryFor(date)
            val blocks =
                entry
                    ?.blocks
                    ?.sortedBy(ContentBlock::order)
                    ?.map { it.toUiModel(date) }
                    .orEmpty()
            val currentDraftKeys = draftKeys().filter { it.date == date }
            val saveState =
                when {
                    failedSaveKeys.any { it.date == date } -> SaveState.Failed(SAVE_FAILED_MESSAGE)
                    currentDraftKeys.isNotEmpty() ||
                        savingKeys.any { it.date == date } ||
                        waitingSaveKeys.any { it.date == date } -> SaveState.Saving
                    lastSavedAtByDate[date] != null -> SaveState.Saved(lastSavedAtByDate.getValue(date))
                    else -> SaveState.Idle
                }
            updateState {
                copy(
                    isLoading = isLoading,
                    entryId = entry?.id,
                    blocks = blocks,
                    saveState = saveState,
                    errorMessage = if (entry != null) null else errorMessage,
                )
            }
        }

        private fun ContentBlock.toUiModel(date: LocalDate): EditorBlockUiModel =
            when (this) {
                is ContentBlock.Text -> {
                    val key = DraftKey(date, id)
                    TextBlockUiModel(
                        id = id,
                        order = order,
                        text = drafts[key]?.text ?: content,
                        isSaving = key in savingKeys || key in waitingSaveKeys,
                        hasSaveError = key in failedSaveKeys,
                    )
                }

                is ContentBlock.Image -> {
                    val key = DraftKey(date, id)
                    val attachment = attachments.firstOrNull()
                    ImageBlockUiModel(
                        id = id,
                        order = order,
                        attachmentId = attachment?.id,
                        relativePath = attachment?.localPath,
                        caption = captionDrafts[key]?.let { it.caption.orEmpty() } ?: attachment?.caption.orEmpty(),
                        isSaving = key in savingKeys || key in waitingSaveKeys,
                        hasSaveError = key in failedSaveKeys,
                    )
                }
                is ContentBlock.Table -> {
                    val key = DraftKey(date, id)
                    TableBlockUiModel(
                        id = id,
                        order = order,
                        content = tableDrafts[key]?.content ?: content,
                        isSaving = key in savingKeys || key in waitingSaveKeys,
                        hasSaveError = key in failedSaveKeys,
                    )
                }
            }

        private fun draftKeys(): Set<DraftKey> = drafts.keys + tableDrafts.keys + captionDrafts.keys

        private fun hasDraft(key: DraftKey): Boolean = key in drafts || key in tableDrafts || key in captionDrafts

        private fun updateEntry(
            date: LocalDate,
            transform: (WorkEntry) -> WorkEntry,
        ) {
            entriesByDate[date]?.let { entriesByDate[date] = transform(it) }
        }

        private fun entryFor(date: LocalDate): WorkEntry? = entriesByDate[date]

        private fun updateState(transform: TodayUiState.() -> TodayUiState) {
            _uiState.update(transform)
        }

        private fun updateStateForDate(
            date: LocalDate,
            transform: TodayUiState.() -> TodayUiState,
        ) {
            if (_uiState.value.date == date) updateState(transform)
        }

        private fun emitMessage(message: String) {
            _events.trySend(TodayUiEvent.ShowMessage(message))
        }
    }

private data class DraftKey(
    val date: LocalDate,
    val blockId: String,
)

private sealed interface VersionedDraft {
    val version: Int
}

private data class TextDraft(
    val text: String,
    override val version: Int,
) : VersionedDraft

private data class TableDraft(
    val content: TableContent,
    override val version: Int,
) : VersionedDraft

private data class CaptionDraft(
    val attachmentId: String,
    val caption: String?,
    override val version: Int,
) : VersionedDraft

private fun String.toLocalDateOrNull(): LocalDate? = runCatching(LocalDate::parse).getOrNull()

private fun TimeProvider.today(): LocalDate = now().atZone(ZoneId.systemDefault()).toLocalDate()

private fun ContentBlock.withOrder(order: Int): ContentBlock =
    when (this) {
        is ContentBlock.Image -> copy(order = order)
        is ContentBlock.Table -> copy(order = order)
        is ContentBlock.Text -> copy(order = order)
    }

private fun MutableList<ContentBlock>.swap(
    firstIndex: Int,
    secondIndex: Int,
) {
    val first = this[firstIndex]
    this[firstIndex] = this[secondIndex]
    this[secondIndex] = first
}
