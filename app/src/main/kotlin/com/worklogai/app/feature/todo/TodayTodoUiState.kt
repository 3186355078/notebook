package com.worklogai.app.feature.todo

import com.worklogai.app.core.model.DailyTodo
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import java.time.LocalDate

data class TodayTodoUiState(
    val date: LocalDate,
    val isLoading: Boolean = true,
    val todos: List<DailyTodo> = emptyList(),
    val olderIncomplete: List<DailyTodo> = emptyList(),
    val quickTitle: String = "",
    val completedCollapsed: Boolean = false,
    val isBusy: Boolean = false,
    val isDragging: Boolean = false,
    val draggedTodoId: String? = null,
    val editor: TodoEditorDraft? = null,
    val completionPrompt: TodoCompletionPrompt? = null,
    val deleteConfirmationId: String? = null,
    val conversionPrompt: TodoConversionPrompt? = null,
    val errorMessage: String? = null,
) {
    val incompleteTodos: List<DailyTodo>
        get() = todos.filter { it.status == TodoStatus.NOT_STARTED || it.status == TodoStatus.IN_PROGRESS }

    val completedTodos: List<DailyTodo>
        get() = todos.filter { it.status == TodoStatus.DONE || it.status == TodoStatus.CANCELED }

    val doneCount: Int
        get() = todos.count { it.status == TodoStatus.DONE }

    val inProgressCount: Int
        get() = todos.count { it.status == TodoStatus.IN_PROGRESS }

    val completionFraction: Float
        get() = if (todos.isEmpty()) 0f else doneCount.toFloat() / todos.size
}

data class TodoEditorDraft(
    val id: String? = null,
    val title: String = "",
    val note: String = "",
    val scheduledDate: LocalDate,
    val priority: TodoPriority = TodoPriority.MEDIUM,
    val status: TodoStatus = TodoStatus.NOT_STARTED,
    val completionNote: String = "",
)

data class TodoCompletionPrompt(
    val todoId: String,
    val title: String,
    val completionNote: String = "",
    val isResync: Boolean = false,
)

data class TodoConversionPrompt(
    val blockId: String,
    val scheduledDate: LocalDate,
    val priority: TodoPriority = TodoPriority.MEDIUM,
    val duplicateWarning: Boolean = false,
)

sealed interface TodayTodoAction {
    sealed interface Primary : TodayTodoAction

    sealed interface Editor : TodayTodoAction

    sealed interface Completion : TodayTodoAction

    sealed interface Delete : TodayTodoAction

    sealed interface Ordering : TodayTodoAction

    sealed interface Migration : TodayTodoAction

    sealed interface Conversion : TodayTodoAction

    data class DateChanged(
        val date: LocalDate,
    ) : Primary

    data class QuickTitleChanged(
        val value: String,
    ) : Primary

    data object QuickAdd : Primary

    data class OpenEditor(
        val todoId: String? = null,
    ) : Editor

    data class UpdateEditor(
        val draft: TodoEditorDraft,
    ) : Editor

    data object SaveEditor : Editor

    data object CloseEditor : Editor

    data class ToggleDone(
        val todoId: String,
    ) : Completion

    data class ChangeStatus(
        val todoId: String,
        val status: TodoStatus,
    ) : Completion

    data class ChangePriority(
        val todoId: String,
        val priority: TodoPriority,
    ) : Ordering

    data class UpdateCompletionNote(
        val value: String,
    ) : Completion

    data object CompleteOnly : Completion

    data object CompleteAndRecord : Completion

    data object DismissCompletion : Completion

    data class RequestResync(
        val todoId: String,
    ) : Completion

    data class RequestDelete(
        val todoId: String,
    ) : Delete

    data object ConfirmDelete : Delete

    data object DismissDelete : Delete

    data class StartDrag(
        val todoId: String,
    ) : Ordering

    data class MoveDragged(
        val direction: Int,
    ) : Ordering

    data object CommitDrag : Ordering

    data class AccessibleMove(
        val todoId: String,
        val direction: Int,
    ) : Ordering

    data class MoveOlderToToday(
        val todoId: String,
    ) : Migration

    data object MoveAllOlderToToday : Migration

    data class CancelOlder(
        val todoId: String,
    ) : Migration

    data object ToggleCompletedCollapsed : Primary

    data class OpenLinkedRecord(
        val todoId: String,
    ) : Primary

    data class RequestTextConversion(
        val blockId: String,
    ) : Conversion

    data class UpdateConversion(
        val draft: TodoConversionPrompt,
    ) : Conversion

    data object ConfirmTextConversion : Conversion

    data object DismissTextConversion : Conversion
}

sealed interface TodayTodoUiEvent {
    data class ShowMessage(
        val message: String,
    ) : TodayTodoUiEvent

    data class OpenWorkEntry(
        val date: LocalDate,
    ) : TodayTodoUiEvent
}
