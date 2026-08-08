package com.worklogai.app.feature.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worklogai.app.R
import com.worklogai.app.core.designsystem.component.EmptyState
import com.worklogai.app.core.designsystem.component.WorkLogPageHeader
import com.worklogai.app.core.designsystem.component.WorkLogSectionHeader
import com.worklogai.app.core.designsystem.theme.WorkLogSpacing
import com.worklogai.app.feature.editor.component.BlockControls
import com.worklogai.app.feature.editor.component.DeleteBlockDialog
import com.worklogai.app.feature.editor.component.ImageBlockEditor
import com.worklogai.app.feature.editor.component.TableBlockCallbacks
import com.worklogai.app.feature.editor.component.TableBlockEditor
import com.worklogai.app.feature.editor.component.TextBlockCallbacks
import com.worklogai.app.feature.editor.component.TextBlockEditor
import com.worklogai.app.feature.editor.component.UnsupportedBlockCard
import com.worklogai.app.feature.todo.TodayTodoAction
import com.worklogai.app.feature.todo.TodayTodoSection
import com.worklogai.app.feature.todo.TodayTodoUiEvent
import com.worklogai.app.feature.todo.TodayTodoUiState
import com.worklogai.app.feature.todo.TodayTodoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MIN_DATE_REFRESH_DELAY_MS = 1_000L
private const val LINKED_BLOCK_HIGHLIGHT_MILLIS = 1_500L
private const val EDITOR_BLOCK_LIST_OFFSET = 3
private val TODAY_WIDE_CONTENT_MAX_WIDTH = 640.dp

data class TodayScreenNavigation(
    val openEntry: (LocalDate) -> Unit = {},
    val openLinkedEntry: (LocalDate, String) -> Boolean = { date, _ ->
        openEntry(date)
        true
    },
)

@Composable
fun TodayScreen(
    followCurrentDate: Boolean = true,
    navigation: TodayScreenNavigation = TodayScreenNavigation(),
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
    todoViewModel: TodayTodoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val todoState by todoViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { viewModel.onAction(TodayAction.ImageSelected(it)) }
        }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        todoViewModel.onAction(TodayTodoAction.LinkedRecordNavigationReturned)
        if (followCurrentDate) {
            val date = LocalDate.now()
            viewModel.onAction(TodayAction.DateChanged(date))
            todoViewModel.onAction(TodayTodoAction.DateChanged(date))
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onAction(TodayAction.FlushPendingEdits)
    }
    DisposableEffect(viewModel) {
        onDispose { viewModel.onAction(TodayAction.FlushPendingEdits) }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is TodayUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
    LaunchedEffect(todoViewModel) {
        todoViewModel.events.collectLatest { event ->
            when (event) {
                is TodayTodoUiEvent.OpenWorkEntry -> {
                    if (!followCurrentDate && event.date == viewModel.uiState.value.date) {
                        viewModel.onAction(TodayAction.ShowLinkedBlock(event.linkedContentBlockId))
                        if (viewModel.uiState.value.highlightedBlockId == null) {
                            todoViewModel.onAction(TodayTodoAction.LinkedRecordNavigationReturned)
                        }
                    } else if (!navigation.openLinkedEntry(event.date, event.linkedContentBlockId)) {
                        todoViewModel.onAction(TodayTodoAction.LinkedRecordNavigationFailed)
                    }
                }
                is TodayTodoUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
    LaunchedEffect(followCurrentDate) {
        while (followCurrentDate) {
            val now = ZonedDateTime.now()
            val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(now.zone)
            val delayMillis =
                Duration
                    .between(now, nextMidnight)
                    .toMillis()
                    .coerceAtLeast(MIN_DATE_REFRESH_DELAY_MS)
            delay(delayMillis)
            val date = LocalDate.now()
            viewModel.onAction(TodayAction.DateChanged(date))
            todoViewModel.onAction(TodayTodoAction.DateChanged(date))
        }
    }

    TodayScreenContent(
        state = state,
        todoState = todoState,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        onTodoAction = todoViewModel::onAction,
        onLinkedBlockHighlightFinished = {
            todoViewModel.onAction(TodayTodoAction.LinkedRecordNavigationReturned)
        },
        onPickImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        modifier = modifier,
    )
}

@Composable
@Suppress("LongParameterList")
internal fun TodayScreenContent(
    state: TodayUiState,
    todoState: TodayTodoUiState = TodayTodoUiState(date = state.date, isLoading = false),
    snackbarHostState: SnackbarHostState,
    onAction: (TodayAction) -> Unit,
    onTodoAction: (TodayTodoAction) -> Unit = {},
    onLinkedBlockHighlightFinished: () -> Unit = {},
    onPickImage: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val dateFormatter =
        remember {
            DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
        }
    val focusedIndex = state.blocks.indexOfFirst { it.id == state.focusedBlockId }
    val highlightedIndex = state.blocks.indexOfFirst { it.id == state.highlightedBlockId }

    LaunchedEffect(state.focusedBlockId, focusedIndex) {
        if (focusedIndex >= 0) listState.animateScrollToItem(focusedIndex + EDITOR_BLOCK_LIST_OFFSET)
    }
    LaunchedEffect(state.highlightedBlockId, highlightedIndex) {
        if (highlightedIndex >= 0) {
            listState.animateScrollToItem(highlightedIndex + EDITOR_BLOCK_LIST_OFFSET)
            delay(LINKED_BLOCK_HIGHLIGHT_MILLIS)
            onLinkedBlockHighlightFinished()
            onAction(TodayAction.LinkedBlockHighlightConsumed)
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val contentModifier =
            if (maxWidth > TODAY_WIDE_CONTENT_MAX_WIDTH) {
                Modifier
                    .fillMaxHeight()
                    .widthIn(max = TODAY_WIDE_CONTENT_MAX_WIDTH)
            } else {
                Modifier.fillMaxSize()
            }
        Scaffold(
            modifier = contentModifier.testTag("today_content_container"),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            TodayScreenBody(
                state = state,
                listState = listState,
                dateFormatter = dateFormatter,
                paddingValues = paddingValues,
                onAction = onAction,
                todoState = todoState,
                onTodoAction = onTodoAction,
                onPickImage = onPickImage,
            )
        }
    }

    state.pendingDeleteBlockId?.let { blockId ->
        DeleteBlockDialog(
            onDismiss = { onAction(TodayAction.CancelDeleteBlock) },
            onConfirm = { onAction(TodayAction.ConfirmDeleteBlock(blockId)) },
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun TodayScreenBody(
    state: TodayUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    dateFormatter: DateTimeFormatter,
    paddingValues: PaddingValues,
    onAction: (TodayAction) -> Unit,
    todoState: TodayTodoUiState,
    onTodoAction: (TodayTodoAction) -> Unit,
    onPickImage: () -> Unit,
) {
    when {
        state.isLoading -> LoadingContent(modifier = Modifier.padding(paddingValues))
        state.isFuturePlanning ->
            FuturePlanningContent(
                state = state,
                todoState = todoState,
                dateFormatter = dateFormatter,
                paddingValues = paddingValues,
                onTodoAction = onTodoAction,
            )
        state.entryId == null && state.errorMessage != null ->
            LoadErrorContent(
                state = state,
                onAction = onAction,
                modifier = Modifier.padding(paddingValues),
            )
        else ->
            TodayEntryList(
                state = state,
                listState = listState,
                dateFormatter = dateFormatter,
                paddingValues = paddingValues,
                onAction = onAction,
                todoState = todoState,
                onTodoAction = onTodoAction,
                onPickImage = onPickImage,
            )
    }
}

@Composable
private fun FuturePlanningContent(
    state: TodayUiState,
    todoState: TodayTodoUiState,
    dateFormatter: DateTimeFormatter,
    paddingValues: PaddingValues,
    onTodoAction: (TodayTodoAction) -> Unit,
) {
    LazyColumn(
        contentPadding =
            PaddingValues(
                start = WorkLogSpacing.largePlus,
                top = WorkLogSpacing.large,
                end = WorkLogSpacing.largePlus,
                bottom = WorkLogSpacing.extraLarge + paddingValues.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.large),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "future_header") {
            WorkLogPageHeader(
                eyebrow = "未来计划",
                title = state.date.format(dateFormatter),
                subtitle = "未来日期仅用于规划待办，不能提前创建工作记录。",
            )
        }
        item(key = "future_todos") {
            TodayTodoSection(todoState, onTodoAction)
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun TodayEntryList(
    state: TodayUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    dateFormatter: DateTimeFormatter,
    paddingValues: PaddingValues,
    onAction: (TodayAction) -> Unit,
    todoState: TodayTodoUiState,
    onTodoAction: (TodayTodoAction) -> Unit,
    onPickImage: () -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding =
            PaddingValues(
                start = WorkLogSpacing.largePlus,
                top = WorkLogSpacing.large,
                end = WorkLogSpacing.largePlus,
                bottom = WorkLogSpacing.extraLarge + paddingValues.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.large),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "today_header") {
            TodayHeader(
                presentation =
                    TodayHeaderPresentation(
                        date = state.date,
                        title =
                            if (state.followsCurrentDate) {
                                null
                            } else {
                                state.date.format(dateFormatter)
                            },
                        dateText = state.date.format(dateFormatter),
                        todoCount = todoState.todos.size,
                        doneCount = todoState.doneCount,
                        inProgressCount = todoState.inProgressCount,
                        blockCount = state.blocks.size,
                    ),
                saveState = state.saveState,
                onRetrySave = { onAction(TodayAction.RetryFailedSaves) },
            )
        }
        item(key = "today_todos") {
            TodayTodoSection(
                state = todoState,
                onAction = onTodoAction,
            )
        }
        item(key = "quick_record_toolbar") {
            QuickRecordToolbar(
                isHistorical = !state.followsCurrentDate,
                isImageImporting = state.isImageImporting,
                onAddText = { onAction(TodayAction.AddTextBlock) },
                onAddImage = onPickImage,
                onAddTable = { onAction(TodayAction.AddTableBlock) },
            )
        }
        editorBlocks(state, onAction, onTodoAction)
    }
}

private fun LazyListScope.editorBlocks(
    state: TodayUiState,
    onAction: (TodayAction) -> Unit,
    onTodoAction: (TodayTodoAction) -> Unit,
) {
    if (state.blocks.isEmpty()) {
        item(key = "today_empty") {
            EmptyState(
                title =
                    if (state.followsCurrentDate) {
                        stringResource(R.string.today_empty_title)
                    } else {
                        "这一天还没有工作记录"
                    },
                body =
                    if (state.followsCurrentDate) {
                        stringResource(R.string.today_empty_body)
                    } else {
                        "补充当天做过的事情吧"
                    },
                action = {
                    Button(onClick = { onAction(TodayAction.AddTextBlock) }) {
                        Text(stringResource(R.string.today_add_text))
                    }
                },
                modifier = Modifier.fillParentMaxSize(),
            )
        }
    } else {
        itemsIndexed(
            items = state.blocks,
            key = { _, block -> block.id },
        ) { index, block ->
            val isPendingText = block is TextBlockUiModel && block.isPending
            EditorBlockItem(
                presentation =
                    EditorBlockPresentation(
                        block = block,
                        canMoveUp = !isPendingText && index > 0 && !state.isStructureOperationInProgress,
                        canMoveDown =
                            !isPendingText && index < state.blocks.lastIndex && !state.isStructureOperationInProgress,
                        canConvertToTodo = !isPendingText,
                        requestsFocus = block.id == state.focusedBlockId,
                        isHighlighted = block.id == state.highlightedBlockId,
                    ),
                onAction = onAction,
                onConvertTextBlock = { blockId ->
                    onTodoAction(TodayTodoAction.RequestTextConversion(blockId))
                },
            )
        }
    }
}

@Composable
private fun QuickRecordToolbar(
    isHistorical: Boolean,
    isImageImporting: Boolean,
    onAddText: () -> Unit,
    onAddImage: () -> Unit,
    onAddTable: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.medium)) {
        WorkLogSectionHeader(
            title = "快速记录",
            description =
                if (isHistorical) {
                    "从文字、图片或表格补充当天的工作"
                } else {
                    "从文字、图片或表格开始记录今天的工作"
                },
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(WorkLogSpacing.small),
        ) {
            OutlinedButton(onClick = onAddText) {
                androidx.compose.material3.Icon(Icons.Outlined.Add, contentDescription = "添加文字")
                Text("文字", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onAddImage, enabled = !isImageImporting) {
                androidx.compose.material3.Icon(Icons.Outlined.Image, contentDescription = "添加图片")
                Text(if (isImageImporting) "导入中" else "图片", modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onAddTable) {
                androidx.compose.material3.Icon(Icons.Outlined.TableChart, contentDescription = "添加表格")
                Text("表格", modifier = Modifier.padding(start = 6.dp))
            }
        }
        WorkLogSectionHeader(
            title = if (isHistorical) "当日工作记录" else "今日工作记录",
            description = if (isHistorical) "内容会自动保存在所选日期" else "内容会自动保存在当前日期",
        )
    }
}

@Composable
private fun EditorBlockItem(
    presentation: EditorBlockPresentation,
    onAction: (TodayAction) -> Unit,
    onConvertTextBlock: (String) -> Unit,
) {
    val block = presentation.block
    val blockModifier = editorBlockModifier(presentation.isHighlighted)
    val controls =
        BlockControls(
            canMoveUp = presentation.canMoveUp,
            canMoveDown = presentation.canMoveDown,
            onMoveUp = { onAction(TodayAction.MoveBlockUp(block.id)) },
            onMoveDown = { onAction(TodayAction.MoveBlockDown(block.id)) },
            onDelete = { onAction(TodayAction.RequestDeleteBlock(block.id)) },
            onConvertToTodo =
                if (block is TextBlockUiModel && presentation.canConvertToTodo) {
                    { onConvertTextBlock(block.id) }
                } else {
                    null
                },
        )
    when (block) {
        is TextBlockUiModel ->
            TextBlockEditor(
                block = block,
                requestFocus = presentation.requestsFocus,
                controls = controls,
                callbacks =
                    TextBlockCallbacks(
                        onTextChanged = { text -> onAction(TodayAction.TextChanged(block.id, text)) },
                        onFocusChanged = { focused ->
                            onAction(TodayAction.TextFocusChanged(block.id, focused))
                        },
                        onFocusRequestHandled = { onAction(TodayAction.FocusRequestConsumed) },
                    ),
                modifier = blockModifier,
            )

        is UnsupportedBlockUiModel ->
            UnsupportedBlockCard(
                block = block,
                controls = controls,
                modifier = blockModifier,
            )
        is ImageBlockUiModel ->
            ImageBlockEditor(
                block = block,
                controls = controls,
                onCaptionChanged = { onAction(TodayAction.ImageCaptionChanged(block.id, it)) },
                modifier = blockModifier,
            )

        is TableBlockUiModel ->
            TableBlockEditor(
                block = block,
                controls = controls,
                callbacks =
                    TableBlockCallbacks(
                        onTitleChanged = { onAction(TodayAction.TableTitleChanged(block.id, it)) },
                        onColumnNameChanged = { columnId, name ->
                            onAction(TodayAction.TableColumnNameChanged(block.id, columnId, name))
                        },
                        onCellChanged = { rowId, columnId, value ->
                            onAction(TodayAction.TableCellChanged(block.id, rowId, columnId, value))
                        },
                        onAddRow = { onAction(TodayAction.AddTableRow(block.id)) },
                        onDeleteRow = { rowId -> onAction(TodayAction.DeleteTableRow(block.id, rowId)) },
                        onAddColumn = { onAction(TodayAction.AddTableColumn(block.id)) },
                        onDeleteColumn = { columnId ->
                            onAction(TodayAction.DeleteTableColumn(block.id, columnId))
                        },
                    ),
                modifier = blockModifier,
            )
    }
}

@Composable
private fun editorBlockModifier(isHighlighted: Boolean): Modifier =
    if (isHighlighted) {
        Modifier
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.large,
            ).padding(2.dp)
    } else {
        Modifier
    }

private data class EditorBlockPresentation(
    val block: EditorBlockUiModel,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val canConvertToTodo: Boolean,
    val requestsFocus: Boolean,
    val isHighlighted: Boolean,
)
