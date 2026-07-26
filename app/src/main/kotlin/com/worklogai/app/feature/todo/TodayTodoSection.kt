package com.worklogai.app.feature.todo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.worklogai.app.core.designsystem.component.WorkLogContentSurface
import com.worklogai.app.core.designsystem.component.WorkLogSectionHeader
import com.worklogai.app.core.designsystem.component.WorkLogStatusChip
import com.worklogai.app.core.designsystem.theme.WorkLogElevation
import com.worklogai.app.core.designsystem.theme.WorkLogSpacing
import com.worklogai.app.core.model.DailyTodo
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import kotlin.math.abs

private const val DRAG_STEP_PX = 72f
private const val PERCENT_MULTIPLIER = 100
private const val OLDER_TODO_PREVIEW_LIMIT = 3

@Composable
@Suppress("LongMethod")
fun TodayTodoSection(
    state: TodayTodoUiState,
    onAction: (TodayTodoAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("today_todo_section"),
        verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.medium),
    ) {
        TodoSectionHeader(state)
        QuickTodoInput(state, onAction)
        if (state.olderIncomplete.isNotEmpty()) {
            OlderTodoBanner(state, onAction)
        }
        TodoListContent(state, onAction)
        TextButton(
            onClick = { onAction(TodayTodoAction.OpenEditor()) },
            enabled = !state.isBusy,
            modifier = Modifier.align(Alignment.End).testTag("open_full_todo_editor"),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text("详细添加")
        }
        Text(
            "待办仅保存在本地；同步为工作记录后，才可能按 AI 设置参与总结。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    TodoDialogs(state, onAction)
}

@Composable
private fun TodoSectionHeader(state: TodayTodoUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.small)) {
        WorkLogSectionHeader(
            title = "今日待办",
            description = "已完成 ${state.doneCount}/${state.todos.size} · 进行中 ${state.inProgressCount}",
            trailing = {
                WorkLogStatusChip(
                    label = "${(state.completionFraction * PERCENT_MULTIPLIER).toInt()}%",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
        )
        LinearProgressIndicator(
            progress = { state.completionFraction },
            modifier =
                Modifier.fillMaxWidth().semantics {
                    contentDescription = "今日待办完成进度"
                    stateDescription = "已完成 ${state.doneCount} 项，共 ${state.todos.size} 项"
                },
        )
    }
}

@Composable
private fun QuickTodoInput(
    state: TodayTodoUiState,
    onAction: (TodayTodoAction) -> Unit,
) {
    OutlinedTextField(
        value = state.quickTitle,
        onValueChange = { onAction(TodayTodoAction.QuickTitleChanged(it)) },
        placeholder = { Text("添加今日待办……") },
        singleLine = true,
        trailingIcon = {
            IconButton(
                onClick = { onAction(TodayTodoAction.QuickAdd) },
                enabled = state.quickTitle.isNotBlank() && !state.isBusy,
                modifier = Modifier.testTag("quick_add_todo"),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "添加待办")
            }
        },
        modifier = Modifier.fillMaxWidth().testTag("quick_todo_input"),
    )
}

@Composable
private fun OlderTodoBanner(
    state: TodayTodoUiState,
    onAction: (TodayTodoAction) -> Unit,
) {
    WorkLogContentSurface(emphasized = true) {
        Column(
            modifier = Modifier.padding(WorkLogSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.small),
        ) {
            Text("有 ${state.olderIncomplete.size} 项未完成待办", style = MaterialTheme.typography.titleSmall)
            state.olderIncomplete.take(OLDER_TODO_PREVIEW_LIMIT).forEach { todo ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        todo.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onAction(TodayTodoAction.MoveOlderToToday(todo.id)) }) {
                        Text("移到今天")
                    }
                    IconButton(onClick = { onAction(TodayTodoAction.CancelOlder(todo.id)) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "将“${todo.title}”标记为已取消")
                    }
                }
            }
            if (state.olderIncomplete.size > 1) {
                OutlinedButton(onClick = { onAction(TodayTodoAction.MoveAllOlderToToday) }) {
                    Text("全部移到今天")
                }
            }
        }
    }
}

@Composable
@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun TodoItem(
    todo: DailyTodo,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isDragging: Boolean,
    onAction: (TodayTodoAction) -> Unit,
) {
    var menuExpanded by remember(todo.id) { mutableStateOf(false) }
    var dragDistance by remember(todo.id) { mutableFloatStateOf(0f) }
    val containerColor by
        animateColorAsState(
            if (isDragging) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            label = "todo-drag-color",
        )
    val elevation by
        animateDpAsState(
            targetValue = if (isDragging) WorkLogElevation.dragging else WorkLogElevation.flat,
            label = "todo-drag-elevation",
        )
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("todo_${todo.id}")
                .semantics {
                    stateDescription = todo.status.chineseLabel
                    customActions =
                        listOf(
                            CustomAccessibilityAction("上移") {
                                if (canMoveUp) onAction(TodayTodoAction.AccessibleMove(todo.id, -1))
                                canMoveUp
                            },
                            CustomAccessibilityAction("下移") {
                                if (canMoveDown) onAction(TodayTodoAction.AccessibleMove(todo.id, 1))
                                canMoveDown
                            },
                            CustomAccessibilityAction("设为更高优先级") {
                                todo.priority.higher()?.let {
                                    onAction(TodayTodoAction.ChangePriority(todo.id, it))
                                    true
                                } ?: false
                            },
                            CustomAccessibilityAction("设为更低优先级") {
                                todo.priority.lower()?.let {
                                    onAction(TodayTodoAction.ChangePriority(todo.id, it))
                                    true
                                } ?: false
                            },
                        )
                },
        color = containerColor,
        shape = MaterialTheme.shapes.large,
        tonalElevation = elevation,
        shadowElevation = elevation,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(todoPriorityColor(todo.priority)),
            )
            IconButton(
                onClick = { onAction(TodayTodoAction.ToggleDone(todo.id)) },
                modifier =
                    Modifier.semantics {
                        contentDescription =
                            if (todo.status == TodoStatus.DONE) {
                                "将“${todo.title}”恢复为未开始"
                            } else {
                                "将“${todo.title}”标记为已完成"
                            }
                    },
            ) {
                Icon(
                    imageVector =
                        when (todo.status) {
                            TodoStatus.NOT_STARTED -> Icons.Outlined.RadioButtonUnchecked
                            TodoStatus.IN_PROGRESS -> Icons.Outlined.Pending
                            TodoStatus.DONE -> Icons.Outlined.CheckCircle
                            TodoStatus.CANCELED -> Icons.Outlined.Cancel
                        },
                    contentDescription = null,
                    tint =
                        when (todo.status) {
                            TodoStatus.NOT_STARTED -> MaterialTheme.colorScheme.onSurfaceVariant
                            TodoStatus.IN_PROGRESS -> MaterialTheme.colorScheme.tertiary
                            TodoStatus.DONE -> MaterialTheme.colorScheme.primary
                            TodoStatus.CANCELED -> MaterialTheme.colorScheme.outline
                        },
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(vertical = WorkLogSpacing.small),
                verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.extraSmall),
            ) {
                Text(
                    todo.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration =
                        if (todo.status == TodoStatus.DONE) {
                            TextDecoration.LineThrough
                        } else {
                            TextDecoration.None
                        },
                    color =
                        if (todo.status == TodoStatus.CANCELED) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    style = MaterialTheme.typography.bodyLarge,
                )
                todo.note?.let {
                    Text(
                        it,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PriorityBadge(todo.priority)
                    if (todo.status == TodoStatus.IN_PROGRESS || todo.status == TodoStatus.CANCELED) {
                        WorkLogStatusChip(
                            label = todo.status.chineseLabel,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (todo.linkedContentBlockId != null) {
                        AssistChip(
                            onClick = { onAction(TodayTodoAction.OpenLinkedRecord(todo.id)) },
                            label = { Text("已同步记录") },
                            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                        )
                    }
                }
            }
            if (todo.status == TodoStatus.NOT_STARTED || todo.status == TodoStatus.IN_PROGRESS) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .pointerInput(todo.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragDistance = 0f
                                        onAction(TodayTodoAction.StartDrag(todo.id))
                                    },
                                    onDragEnd = {
                                        dragDistance = 0f
                                        onAction(TodayTodoAction.CommitDrag)
                                    },
                                    onDragCancel = {
                                        dragDistance = 0f
                                        onAction(TodayTodoAction.CommitDrag)
                                    },
                                ) { change, amount ->
                                    change.consume()
                                    dragDistance += amount.y
                                    if (abs(dragDistance) >= DRAG_STEP_PX) {
                                        onAction(TodayTodoAction.MoveDragged(if (dragDistance > 0) 1 else -1))
                                        dragDistance = 0f
                                    }
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.DragHandle, contentDescription = "拖动“${todo.title}”调整顺序")
                }
            }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "“${todo.title}”更多操作")
            }
            TodoItemMenu(todo, menuExpanded, { menuExpanded = false }, onAction)
        }
    }
}

@Composable
private fun TodoItemMenu(
    todo: DailyTodo,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (TodayTodoAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("编辑") },
            onClick = {
                onDismiss()
                onAction(TodayTodoAction.OpenEditor(todo.id))
            },
            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
        )
        TodoStatus.entries.forEach { status ->
            DropdownMenuItem(
                text = { Text("设为${status.chineseLabel}") },
                onClick = {
                    onDismiss()
                    onAction(TodayTodoAction.ChangeStatus(todo.id, status))
                },
                enabled = status != todo.status,
            )
        }
        HorizontalDivider()
        TodoPriority.entries.forEach { priority ->
            DropdownMenuItem(
                text = { Text("${priority.chineseLabel}优先级") },
                onClick = {
                    onDismiss()
                    onAction(TodayTodoAction.ChangePriority(todo.id, priority))
                },
                enabled =
                    priority != todo.priority &&
                        todo.status != TodoStatus.DONE &&
                        todo.status != TodoStatus.CANCELED,
            )
        }
        if (todo.linkedContentBlockId != null) {
            DropdownMenuItem(
                text = { Text("重新同步为新的工作记录") },
                onClick = {
                    onDismiss()
                    onAction(TodayTodoAction.RequestResync(todo.id))
                },
                leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
            )
        }
        DropdownMenuItem(
            text = { Text("删除待办") },
            onClick = {
                onDismiss()
                onAction(TodayTodoAction.RequestDelete(todo.id))
            },
            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        )
    }
}

@Composable
private fun TodoDialogs(
    state: TodayTodoUiState,
    onAction: (TodayTodoAction) -> Unit,
) {
    state.editor?.let { draft -> TodoEditorDialog(draft, state.isBusy, onAction) }
    state.completionPrompt?.let { prompt -> TodoCompletionDialog(prompt, state.isBusy, onAction) }
    state.deleteConfirmationId?.let { id ->
        val title = (state.todos + state.olderIncomplete).firstOrNull { it.id == id }?.title.orEmpty()
        AlertDialog(
            onDismissRequest = { onAction(TodayTodoAction.DismissDelete) },
            title = { Text("删除待办？") },
            text = { Text("“$title”将被删除，已关联的历史工作记录不会被删除。") },
            confirmButton = {
                Button(onClick = { onAction(TodayTodoAction.ConfirmDelete) }, enabled = !state.isBusy) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(TodayTodoAction.DismissDelete) }) { Text("取消") }
            },
        )
    }
    state.conversionPrompt?.let { prompt -> TodoConversionDialog(prompt, state.isBusy, onAction) }
}

@Composable
@Suppress("LongMethod")
private fun TodoEditorDialog(
    draft: TodoEditorDraft,
    isBusy: Boolean,
    onAction: (TodayTodoAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onAction(TodayTodoAction.CloseEditor) },
        title = { Text(if (draft.id == null) "新建待办" else "编辑待办") },
        text = {
            Column(
                modifier =
                    Modifier
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { onAction(TodayTodoAction.UpdateEditor(draft.copy(title = it))) },
                    label = { Text("标题") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("todo_editor_title"),
                )
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = { onAction(TodayTodoAction.UpdateEditor(draft.copy(note = it))) },
                    label = { Text("备注") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                TodoDateStepper(
                    date = draft.scheduledDate,
                    onDateChanged = {
                        onAction(TodayTodoAction.UpdateEditor(draft.copy(scheduledDate = it)))
                    },
                )
                Text("优先级", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TodoPriority.entries.forEach { priority ->
                        FilterChip(
                            selected = draft.priority == priority,
                            onClick = {
                                onAction(TodayTodoAction.UpdateEditor(draft.copy(priority = priority)))
                            },
                            label = { Text(priority.chineseLabel) },
                        )
                    }
                }
                if (draft.id != null) {
                    Text("状态", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TodoStatus.entries.forEach { status ->
                            FilterChip(
                                selected = draft.status == status,
                                onClick = {
                                    onAction(TodayTodoAction.UpdateEditor(draft.copy(status = status)))
                                },
                                label = { Text(status.chineseLabel) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = draft.completionNote,
                        onValueChange = {
                            onAction(TodayTodoAction.UpdateEditor(draft.copy(completionNote = it)))
                        },
                        label = { Text("完成备注") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAction(TodayTodoAction.SaveEditor) },
                enabled = draft.title.isNotBlank() && !isBusy,
                modifier = Modifier.testTag("save_todo_editor"),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(TodayTodoAction.CloseEditor) }, enabled = !isBusy) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun TodoCompletionDialog(
    prompt: TodoCompletionPrompt,
    isBusy: Boolean,
    onAction: (TodayTodoAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onAction(TodayTodoAction.DismissCompletion) },
        title = { Text(if (prompt.isResync) "重新同步工作记录？" else "完成待办") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(prompt.title)
                if (prompt.isResync) {
                    Text(
                        "将创建新的文字记录并更新链接；旧工作记录不会被删除或改写。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = prompt.completionNote,
                    onValueChange = { onAction(TodayTodoAction.UpdateCompletionNote(it)) },
                    label = { Text("完成备注（可选）") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAction(TodayTodoAction.CompleteAndRecord) }, enabled = !isBusy) {
                Text(if (prompt.isResync) "确认重新同步" else "完成并记录")
            }
        },
        dismissButton = {
            Row {
                if (!prompt.isResync) {
                    TextButton(onClick = { onAction(TodayTodoAction.CompleteOnly) }, enabled = !isBusy) {
                        Text("仅完成待办")
                    }
                }
                TextButton(onClick = { onAction(TodayTodoAction.DismissCompletion) }, enabled = !isBusy) {
                    Text("取消")
                }
            }
        },
    )
}

@Composable
private fun TodoConversionDialog(
    prompt: TodoConversionPrompt,
    isBusy: Boolean,
    onAction: (TodayTodoAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onAction(TodayTodoAction.DismissTextConversion) },
        title = { Text(if (prompt.duplicateWarning) "同标题待办已存在" else "转为待办") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (prompt.duplicateWarning) {
                    Text("同一天已有同标题待办。原工作记录不会删除，是否仍要创建？")
                } else {
                    Text("原文字工作记录会保留。标题取第一行，其余内容作为备注。")
                }
                TodoDateStepper(
                    date = prompt.scheduledDate,
                    onDateChanged = {
                        onAction(TodayTodoAction.UpdateConversion(prompt.copy(scheduledDate = it)))
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TodoPriority.entries.forEach { priority ->
                        FilterChip(
                            selected = prompt.priority == priority,
                            onClick = {
                                onAction(TodayTodoAction.UpdateConversion(prompt.copy(priority = priority)))
                            },
                            label = { Text(priority.chineseLabel) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAction(TodayTodoAction.ConfirmTextConversion) }, enabled = !isBusy) {
                Text(if (prompt.duplicateWarning) "仍然创建" else "创建待办")
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(TodayTodoAction.DismissTextConversion) }, enabled = !isBusy) {
                Text("取消")
            }
        },
    )
}
