package com.worklogai.app.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.worklogai.app.core.designsystem.component.EmptyState
import com.worklogai.app.core.repository.TodoDateStats
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate

@Composable
fun HistoryScreen(
    onOpenEntry: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
    todoStatsViewModel: HistoryTodoStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val todoStats by todoStatsViewModel.stats.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onAction(HistoryAction.Refresh)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is HistoryUiEvent.OpenEditor -> onOpenEntry(event.date)
                is HistoryUiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }
    LaunchedEffect(state.items.map(HistoryItemUiModel::date)) {
        todoStatsViewModel.load(state.items.map(HistoryItemUiModel::date).toSet())
    }

    HistoryScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        todoStats = todoStats,
        modifier = modifier,
    )
}

@Composable
internal fun HistoryScreenContent(
    state: HistoryUiState,
    snackbarHostState: SnackbarHostState,
    onAction: (HistoryAction) -> Unit,
    todoStats: Map<LocalDate, TodoDateStats> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            HistorySearchBar(
                query = state.query,
                isSearching = state.isSearching,
                onQueryChanged = { onAction(HistoryAction.SearchQueryChanged(it)) },
                onClear = { onAction(HistoryAction.ClearSearch) },
            )
            HistoryModeSelector(
                selectedMode = state.mode,
                onModeSelected = { onAction(HistoryAction.ChangeMode(it)) },
            )
            HistoryPeriodNavigator(
                state = state,
                onAction = onAction,
            )
            HorizontalDivider()
            HistoryList(
                state = state,
                listState = listState,
                onAction = onAction,
                todoStats = todoStats,
            )
        }
    }
}

@Composable
private fun HistoryList(
    state: HistoryUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onAction: (HistoryAction) -> Unit,
    todoStats: Map<LocalDate, TodoDateStats>,
) {
    when {
        state.isLoading && state.items.isEmpty() -> HistoryLoadingContent()
        state.errorMessage != null && state.items.isEmpty() ->
            HistoryErrorContent(message = state.errorMessage, onRetry = { onAction(HistoryAction.Retry) })
        else ->
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.items.isEmpty()) {
                    item(key = "history_empty") {
                        HistoryEmptyContent(
                            state = state,
                            onAction = onAction,
                        )
                    }
                } else {
                    items(state.items, key = HistoryItemUiModel::entryId) { item ->
                        HistoryItemCard(
                            item = item,
                            todoStats = todoStats[item.date],
                            onOpen = { onAction(HistoryAction.OpenEntry(item.date)) },
                        )
                    }
                }
                state.errorMessage?.takeIf { state.items.isNotEmpty() }?.let { message ->
                    item(key = "history_error") {
                        HistoryErrorContent(message = message, onRetry = { onAction(HistoryAction.Retry) })
                    }
                }
                if (state.canLoadMore) {
                    item(key = "history_load_more") {
                        Button(
                            onClick = { onAction(HistoryAction.LoadMore) },
                            enabled = !state.isLoadingMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.isLoadingMore) {
                                CircularProgressIndicator()
                            } else {
                                Text("加载更多")
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun HistoryItemCard(
    item: HistoryItemUiModel,
    todoStats: TodoDateStats?,
    onOpen: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "打开${item.dateLabel}工作记录：${item.previewText}" }
                .clickable(onClick = onOpen),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.dateLabel, style = MaterialTheme.typography.titleSmall)
                Text(
                    item.weekdayLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(item.previewText, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Text(
                item.contentSummary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            todoStats?.takeIf { it.total > 0 }?.let {
                Text(
                    "${it.done}/${it.total} 项待办已完成",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun HistoryEmptyContent(
    state: HistoryUiState,
    onAction: (HistoryAction) -> Unit,
) {
    val title =
        when {
            state.query.isNotBlank() -> "没有找到相关记录"
            state.mode == HistoryMode.DAY -> "这一天还没有工作记录"
            state.mode == HistoryMode.WEEK -> "这一周还没有工作记录"
            state.mode == HistoryMode.MONTH -> "这个月还没有工作记录"
            else -> "还没有历史工作记录"
        }
    EmptyState(
        title = title,
        body = "随时记录工作进展，便于日后回顾。",
        action =
            if (state.query.isBlank() && state.mode == HistoryMode.DAY) {
                {
                    Button(onClick = { onAction(HistoryAction.OpenEntry(state.selectedDate)) }) {
                        Text("开始记录")
                    }
                }
            } else {
                null
            },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HistoryLoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text("正在加载历史记录", modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun HistoryErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    EmptyState(
        title = message,
        body = "请稍后重试。",
        action = {
            Button(onClick = onRetry) { Text("重试") }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
