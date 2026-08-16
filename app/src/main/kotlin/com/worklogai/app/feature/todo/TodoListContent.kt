package com.worklogai.app.feature.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.worklogai.app.core.designsystem.component.WorkLogEmptyState
import com.worklogai.app.core.designsystem.theme.WorkLogIndicatorSize
import com.worklogai.app.core.designsystem.theme.WorkLogSpacing

private val TODO_LIST_MAX_HEIGHT = 480.dp
private val TODO_ITEM_ESTIMATED_HEIGHT = 80.dp

@Composable
internal fun TodoListContent(
    state: TodayTodoUiState,
    onAction: (TodayTodoAction) -> Unit,
) {
    when {
        state.isLoading -> TodoListLoading()
        state.todos.isEmpty() -> TodoListEmpty()
        else -> {
            val completedCount =
                when {
                    state.completedTodos.isEmpty() -> 0
                    state.completedCollapsed -> 1
                    else -> state.completedTodos.size + 1
                }
            val visibleCount = state.incompleteTodos.size + completedCount
            val listHeight =
                (TODO_ITEM_ESTIMATED_HEIGHT * visibleCount.coerceAtLeast(1))
                    .coerceAtMost(TODO_LIST_MAX_HEIGHT)
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(listHeight)
                        .testTag("todo_list"),
                verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.extraSmall),
            ) {
                itemsIndexed(
                    items = state.incompleteTodos,
                    key = { _, todo -> todo.id },
                ) { index, todo ->
                    TodoItem(
                        todo = todo,
                        presentation =
                            TodoItemPresentation(
                                canMoveUp = index > 0,
                                canMoveDown = index < state.incompleteTodos.lastIndex,
                                isDragging = state.draggedTodoId == todo.id,
                                isLinkedNavigationInProgress = state.navigatingLinkedTodoId == todo.id,
                            ),
                        onAction = onAction,
                    )
                }
                if (state.completedTodos.isNotEmpty()) {
                    item(key = "completed-toggle") {
                        CompletedTodosToggle(state = state, onAction = onAction)
                    }
                }
                if (!state.completedCollapsed) {
                    items(
                        items = state.completedTodos,
                        key = { todo -> todo.id },
                    ) { todo ->
                        TodoItem(
                            todo = todo,
                            presentation =
                                TodoItemPresentation(
                                    isLinkedNavigationInProgress = state.navigatingLinkedTodoId == todo.id,
                                ),
                            onAction = onAction,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedTodosToggle(
    state: TodayTodoUiState,
    onAction: (TodayTodoAction) -> Unit,
) {
    TextButton(
        onClick = { onAction(TodayTodoAction.ToggleCompletedCollapsed) },
        modifier = Modifier.testTag("toggle_completed_todos"),
    ) {
        Text(
            if (state.completedCollapsed) {
                "显示已完成和已取消（${state.completedTodos.size}）"
            } else {
                "收起已完成和已取消"
            },
        )
        Icon(
            imageVector =
                if (state.completedCollapsed) {
                    Icons.Outlined.ExpandMore
                } else {
                    Icons.Outlined.ExpandLess
                },
            contentDescription = null,
        )
    }
}

@Composable
private fun TodoListLoading() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = WorkLogSpacing.medium),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(WorkLogIndicatorSize.compact))
    }
}

@Composable
private fun TodoListEmpty() {
    WorkLogEmptyState(
        title = "今天还没有待办",
        body = "添加一件最重要的事情开始吧",
        icon = Icons.AutoMirrored.Outlined.ListAlt,
        compact = true,
    )
}
