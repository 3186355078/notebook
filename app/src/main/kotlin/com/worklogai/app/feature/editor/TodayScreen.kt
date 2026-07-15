package com.worklogai.app.feature.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worklogai.app.R
import com.worklogai.app.core.designsystem.component.EmptyState
import com.worklogai.app.feature.editor.component.BlockControls
import com.worklogai.app.feature.editor.component.DeleteBlockDialog
import com.worklogai.app.feature.editor.component.ImageBlockEditor
import com.worklogai.app.feature.editor.component.SaveStatusIndicator
import com.worklogai.app.feature.editor.component.TableBlockCallbacks
import com.worklogai.app.feature.editor.component.TableBlockEditor
import com.worklogai.app.feature.editor.component.TextBlockCallbacks
import com.worklogai.app.feature.editor.component.TextBlockEditor
import com.worklogai.app.feature.editor.component.UnsupportedBlockCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MIN_DATE_REFRESH_DELAY_MS = 1_000L

@Composable
fun TodayScreen(
    followCurrentDate: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { viewModel.onAction(TodayAction.ImageSelected(it)) }
        }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (followCurrentDate) viewModel.onAction(TodayAction.DateChanged(LocalDate.now()))
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
            viewModel.onAction(TodayAction.DateChanged(LocalDate.now()))
        }
    }

    TodayScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        onPickImage = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        modifier = modifier,
    )
}

@Composable
internal fun TodayScreenContent(
    state: TodayUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (TodayAction) -> Unit,
    onPickImage: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val dateFormatter =
        remember {
            DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
        }
    val focusedIndex = state.blocks.indexOfFirst { it.id == state.focusedBlockId }

    LaunchedEffect(state.focusedBlockId, focusedIndex) {
        if (focusedIndex >= 0) listState.animateScrollToItem(focusedIndex + 1)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.canEdit && state.blocks.isNotEmpty()) {
                Row {
                    ExtendedFloatingActionButton(onClick = {
                        onAction(TodayAction.AddTextBlock)
                    }, icon = {
                        androidx.compose.material3.Icon(
                            Icons.Outlined.Add,
                            contentDescription = "添加文字",
                        )
                    }, text = { Text("文字") })
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (!state.isImageImporting) onPickImage()
                        },
                        icon = {
                            androidx.compose.material3.Icon(Icons.Outlined.Image, contentDescription = "添加图片")
                        },
                        text = { Text(if (state.isImageImporting) "导入中" else "图片") },
                        modifier =
                            Modifier.padding(start = 8.dp).semantics {
                                if (state.isImageImporting) disabled()
                            },
                        expanded = false,
                    )
                    ExtendedFloatingActionButton(onClick = {
                        onAction(TodayAction.AddTableBlock)
                    }, icon = {
                        androidx.compose.material3.Icon(Icons.Outlined.TableChart, contentDescription = "添加表格")
                    }, text = { Text("表格") }, modifier = Modifier.padding(start = 8.dp), expanded = false)
                }
            }
        },
    ) { paddingValues ->
        TodayScreenBody(
            state = state,
            listState = listState,
            dateFormatter = dateFormatter,
            paddingValues = paddingValues,
            onAction = onAction,
        )
    }

    state.pendingDeleteBlockId?.let { blockId ->
        DeleteBlockDialog(
            onDismiss = { onAction(TodayAction.CancelDeleteBlock) },
            onConfirm = { onAction(TodayAction.ConfirmDeleteBlock(blockId)) },
        )
    }
}

@Composable
private fun TodayScreenBody(
    state: TodayUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    dateFormatter: DateTimeFormatter,
    paddingValues: PaddingValues,
    onAction: (TodayAction) -> Unit,
) {
    when {
        state.isLoading -> LoadingContent(modifier = Modifier.padding(paddingValues))
        state.entryId == null ->
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
            )
    }
}

@Composable
private fun TodayEntryList(
    state: TodayUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    dateFormatter: DateTimeFormatter,
    paddingValues: PaddingValues,
    onAction: (TodayAction) -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding =
            PaddingValues(
                start = 20.dp,
                top = 16.dp,
                end = 20.dp,
                bottom = 96.dp + paddingValues.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "today_header") {
            TodayHeader(
                title =
                    if (state.followsCurrentDate) {
                        stringResource(
                            R.string.nav_today,
                        )
                    } else {
                        state.date.format(dateFormatter)
                    },
                dateText = state.date.format(dateFormatter),
                saveState = state.saveState,
                onRetrySave = { onAction(TodayAction.RetryFailedSaves) },
            )
        }
        if (state.blocks.isEmpty()) {
            item(key = "today_empty") {
                EmptyState(
                    title = stringResource(R.string.today_empty_title),
                    body = stringResource(R.string.today_empty_body),
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
                EditorBlockItem(
                    presentation =
                        EditorBlockPresentation(
                            block = block,
                            canMoveUp = index > 0 && !state.isStructureOperationInProgress,
                            canMoveDown = index < state.blocks.lastIndex && !state.isStructureOperationInProgress,
                            requestsFocus = block.id == state.focusedBlockId,
                        ),
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun EditorBlockItem(
    presentation: EditorBlockPresentation,
    onAction: (TodayAction) -> Unit,
) {
    val block = presentation.block
    val controls =
        BlockControls(
            canMoveUp = presentation.canMoveUp,
            canMoveDown = presentation.canMoveDown,
            onMoveUp = { onAction(TodayAction.MoveBlockUp(block.id)) },
            onMoveDown = { onAction(TodayAction.MoveBlockDown(block.id)) },
            onDelete = { onAction(TodayAction.RequestDeleteBlock(block.id)) },
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
            )

        is UnsupportedBlockUiModel -> UnsupportedBlockCard(block = block, controls = controls)
        is ImageBlockUiModel ->
            ImageBlockEditor(
                block = block,
                controls = controls,
                onCaptionChanged = { onAction(TodayAction.ImageCaptionChanged(block.id, it)) },
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
            )
    }
}

private data class EditorBlockPresentation(
    val block: EditorBlockUiModel,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val requestsFocus: Boolean,
)

@Composable
private fun TodayHeader(
    title: String,
    dateText: String,
    saveState: SaveState,
    onRetrySave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = dateText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        SaveStatusIndicator(saveState = saveState, onRetry = onRetrySave)
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Text(stringResource(R.string.today_loading), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LoadErrorContent(
    state: TodayUiState,
    onAction: (TodayAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        title = state.errorMessage ?: "工作记录加载失败",
        body = stringResource(R.string.today_empty_body),
        action = {
            Button(onClick = { onAction(TodayAction.RetryLoad) }) {
                Text(stringResource(R.string.today_retry))
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
