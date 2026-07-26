package com.worklogai.app.feature.todo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.worklogai.app.app.navigation.ENTRY_DATE_ARGUMENT
import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.common.time.LocalDateProvider
import com.worklogai.app.core.model.DailyTodo
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import com.worklogai.app.core.repository.CreateTodoRequest
import com.worklogai.app.core.repository.TodoPlacement
import com.worklogai.app.core.repository.TodoRepository
import com.worklogai.app.core.repository.UpdateTodoRequest
import com.worklogai.app.core.todo.CompleteTodoAndRecordUseCase
import com.worklogai.app.core.todo.CreateTodoFromTextBlockUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
// Each small function owns one explicit state transition; combining them would obscure the state machine.
@Suppress("TooManyFunctions")
class TodayTodoViewModel
    @Inject
    constructor(
        private val repository: TodoRepository,
        private val completeTodoAndRecord: CompleteTodoAndRecordUseCase,
        private val createTodoFromTextBlock: CreateTodoFromTextBlockUseCase,
        private val localDateProvider: LocalDateProvider,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val fixedDate = savedStateHandle.get<String>(ENTRY_DATE_ARGUMENT)?.toLocalDateOrNull()
        private val followsCurrentDate = fixedDate == null
        private val initialDate = fixedDate ?: localDateProvider.today()
        private val _uiState = MutableStateFlow(TodayTodoUiState(date = initialDate))
        private val _events = Channel<TodayTodoUiEvent>(Channel.BUFFERED)
        private var todosJob: Job? = null
        private var olderJob: Job? = null
        private var persistedTodos: List<DailyTodo> = emptyList()

        val uiState = _uiState.asStateFlow()
        val events = _events.receiveAsFlow()

        init {
            observeDate(initialDate)
        }

        fun onAction(action: TodayTodoAction) {
            when (action) {
                is TodayTodoAction.Primary -> handlePrimaryAction(action)
                is TodayTodoAction.Editor -> handleEditorAction(action)
                is TodayTodoAction.Completion -> handleCompletionAction(action)
                is TodayTodoAction.Delete -> handleDeleteAction(action)
                is TodayTodoAction.Ordering -> handleOrderingAction(action)
                is TodayTodoAction.Migration -> handleMigrationAction(action)
                is TodayTodoAction.Conversion -> handleConversionAction(action)
            }
        }

        private fun handlePrimaryAction(action: TodayTodoAction.Primary) {
            when (action) {
                is TodayTodoAction.DateChanged -> if (followsCurrentDate) observeDate(action.date)
                TodayTodoAction.QuickAdd -> quickAdd()
                is TodayTodoAction.QuickTitleChanged -> _uiState.update { it.copy(quickTitle = action.value) }
                TodayTodoAction.ToggleCompletedCollapsed ->
                    _uiState.update { it.copy(completedCollapsed = !it.completedCollapsed) }
                is TodayTodoAction.OpenLinkedRecord -> openLinkedRecord(action.todoId)
            }
        }

        private fun handleEditorAction(action: TodayTodoAction.Editor) {
            when (action) {
                TodayTodoAction.CloseEditor -> _uiState.update { it.copy(editor = null) }
                is TodayTodoAction.OpenEditor -> openEditor(action.todoId)
                TodayTodoAction.SaveEditor -> saveEditor()
                is TodayTodoAction.UpdateEditor -> _uiState.update { it.copy(editor = action.draft) }
            }
        }

        private fun handleCompletionAction(action: TodayTodoAction.Completion) {
            when (action) {
                TodayTodoAction.CompleteAndRecord -> completeAndRecord()
                TodayTodoAction.CompleteOnly -> completeOnly()
                is TodayTodoAction.ChangeStatus -> changeStatus(action.todoId, action.status)
                TodayTodoAction.DismissCompletion -> _uiState.update { it.copy(completionPrompt = null) }
                is TodayTodoAction.RequestResync -> requestResync(action.todoId)
                is TodayTodoAction.ToggleDone -> toggleDone(action.todoId)
                is TodayTodoAction.UpdateCompletionNote ->
                    _uiState.update { state ->
                        state.copy(
                            completionPrompt = state.completionPrompt?.copy(completionNote = action.value),
                        )
                    }
            }
        }

        private fun handleDeleteAction(action: TodayTodoAction.Delete) {
            when (action) {
                TodayTodoAction.ConfirmDelete -> confirmDelete()
                TodayTodoAction.DismissDelete -> _uiState.update { it.copy(deleteConfirmationId = null) }
                is TodayTodoAction.RequestDelete ->
                    _uiState.update { it.copy(deleteConfirmationId = action.todoId) }
            }
        }

        private fun handleOrderingAction(action: TodayTodoAction.Ordering) {
            when (action) {
                is TodayTodoAction.AccessibleMove -> accessibleMove(action.todoId, action.direction)
                TodayTodoAction.CommitDrag -> commitDrag()
                is TodayTodoAction.ChangePriority -> changePriority(action.todoId, action.priority)
                is TodayTodoAction.MoveDragged -> moveDragged(action.direction)
                is TodayTodoAction.StartDrag -> startDrag(action.todoId)
            }
        }

        private fun handleMigrationAction(action: TodayTodoAction.Migration) {
            when (action) {
                is TodayTodoAction.CancelOlder -> changeStatus(action.todoId, TodoStatus.CANCELED)
                TodayTodoAction.MoveAllOlderToToday ->
                    moveOlder(
                        _uiState.value.olderIncomplete
                            .map(DailyTodo::id)
                            .toSet(),
                    )
                is TodayTodoAction.MoveOlderToToday -> moveOlder(setOf(action.todoId))
            }
        }

        private fun handleConversionAction(action: TodayTodoAction.Conversion) {
            when (action) {
                TodayTodoAction.ConfirmTextConversion -> confirmTextConversion()
                TodayTodoAction.DismissTextConversion -> _uiState.update { it.copy(conversionPrompt = null) }
                is TodayTodoAction.RequestTextConversion -> requestTextConversion(action.blockId)
                is TodayTodoAction.UpdateConversion -> _uiState.update { it.copy(conversionPrompt = action.draft) }
            }
        }

        private fun observeDate(date: LocalDate) {
            if (_uiState.value.date == date && todosJob != null) return
            todosJob?.cancel()
            olderJob?.cancel()
            persistedTodos = emptyList()
            _uiState.value = TodayTodoUiState(date = date)
            todosJob =
                viewModelScope.launch {
                    repository.observeByDate(date).collect { result ->
                        when (result) {
                            is DataResult.Failure -> {
                                _uiState.update {
                                    it.copy(isLoading = false, errorMessage = "今日待办加载失败，请重试")
                                }
                            }
                            is DataResult.Success -> {
                                persistedTodos = result.value
                                _uiState.update {
                                    if (it.isDragging) {
                                        it.copy(isLoading = false)
                                    } else {
                                        it.copy(
                                            todos = result.value,
                                            isLoading = false,
                                            errorMessage = null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            olderJob =
                viewModelScope.launch {
                    repository.observeIncompleteBefore(date).collect { result ->
                        if (result is DataResult.Success) {
                            _uiState.update { it.copy(olderIncomplete = result.value) }
                        }
                    }
                }
        }

        private fun quickAdd() {
            val state = _uiState.value
            val title = state.quickTitle.trim()
            if (title.isEmpty() || state.isBusy) return
            runBusy {
                when (
                    repository.create(
                        CreateTodoRequest(
                            scheduledDate = state.date,
                            title = title,
                            priority = TodoPriority.MEDIUM,
                        ),
                    )
                ) {
                    is DataResult.Failure -> showMessage("待办添加失败，请重试")
                    is DataResult.Success -> _uiState.update { it.copy(quickTitle = "") }
                }
            }
        }

        private fun openEditor(todoId: String?) {
            val state = _uiState.value
            val todo = todoId?.let { id -> state.todos.firstOrNull { it.id == id } }
            _uiState.update {
                it.copy(
                    editor =
                        todo?.toEditorDraft()
                            ?: TodoEditorDraft(scheduledDate = state.date),
                )
            }
        }

        private fun saveEditor() {
            val draft = _uiState.value.editor ?: return
            if (draft.title.isBlank() || _uiState.value.isBusy) return
            val existing = draft.id?.let(::findTodo)
            val requestsCompletion =
                existing != null &&
                    existing.status != TodoStatus.DONE &&
                    draft.status == TodoStatus.DONE
            runBusy {
                val result =
                    if (draft.id == null) {
                        repository.create(
                            CreateTodoRequest(
                                draft.scheduledDate,
                                draft.title,
                                draft.note,
                                draft.priority,
                            ),
                        )
                    } else {
                        repository.update(
                            UpdateTodoRequest(
                                draft.id,
                                draft.scheduledDate,
                                draft.title,
                                draft.note,
                                draft.priority,
                                if (requestsCompletion) requireNotNull(existing).status else draft.status,
                                draft.completionNote,
                            ),
                        )
                    }
                when (result) {
                    is DataResult.Failure -> showMessage("待办保存失败，请检查输入")
                    is DataResult.Success ->
                        _uiState.update {
                            it.copy(
                                editor = null,
                                completionPrompt =
                                    if (requestsCompletion) {
                                        TodoCompletionPrompt(
                                            result.value.id,
                                            result.value.title,
                                            draft.completionNote,
                                        )
                                    } else {
                                        it.completionPrompt
                                    },
                            )
                        }
                }
            }
        }

        private fun toggleDone(todoId: String) {
            val todo = findTodo(todoId) ?: return
            if (todo.status == TodoStatus.DONE) {
                changeStatus(todoId, TodoStatus.NOT_STARTED)
            } else {
                _uiState.update {
                    it.copy(completionPrompt = TodoCompletionPrompt(todo.id, todo.title, todo.completionNote.orEmpty()))
                }
            }
        }

        private fun changeStatus(
            todoId: String,
            status: TodoStatus,
        ) {
            if (status == TodoStatus.DONE) {
                toggleDone(todoId)
                return
            }
            runBusy {
                when (repository.updateStatus(todoId, status)) {
                    is DataResult.Failure -> showMessage("状态更新失败，请重试")
                    is DataResult.Success -> Unit
                }
            }
        }

        private fun completeOnly() {
            val prompt = _uiState.value.completionPrompt ?: return
            runBusy {
                when (
                    repository.updateStatus(
                        prompt.todoId,
                        TodoStatus.DONE,
                        prompt.completionNote,
                    )
                ) {
                    is DataResult.Failure -> showMessage("完成状态保存失败，请重试")
                    is DataResult.Success -> _uiState.update { it.copy(completionPrompt = null) }
                }
            }
        }

        private fun completeAndRecord() {
            val prompt = _uiState.value.completionPrompt ?: return
            runBusy {
                val result =
                    completeTodoAndRecord(
                        todoId = prompt.todoId,
                        completionNote = prompt.completionNote,
                        resync = prompt.isResync,
                    )
                when (result) {
                    is DataResult.Failure -> showMessage(messageForCompletionFailure(result.error))
                    is DataResult.Success -> {
                        _uiState.update { it.copy(completionPrompt = null) }
                        showMessage(if (prompt.isResync) "已重新同步为新的工作记录" else "已完成并同步到工作记录")
                    }
                }
            }
        }

        private fun requestResync(todoId: String) {
            val todo = findTodo(todoId) ?: return
            _uiState.update {
                it.copy(
                    completionPrompt =
                        TodoCompletionPrompt(
                            todo.id,
                            todo.title,
                            todo.completionNote.orEmpty(),
                            isResync = true,
                        ),
                )
            }
        }

        private fun changePriority(
            todoId: String,
            priority: TodoPriority,
        ) {
            val active = _uiState.value.incompleteTodos
            if (active.none { it.id == todoId }) return
            val placements =
                active.map { todo ->
                    TodoPlacement(todo.id, if (todo.id == todoId) priority else todo.priority)
                }
            persistPlacements(placements)
        }

        private fun startDrag(todoId: String) {
            if (_uiState.value.incompleteTodos.none { it.id == todoId }) return
            _uiState.update { it.copy(isDragging = true, draggedTodoId = todoId) }
        }

        private fun moveDragged(direction: Int) {
            val state = _uiState.value
            val id = state.draggedTodoId ?: return
            val incomplete = state.incompleteTodos.toMutableList()
            val from = incomplete.indexOfFirst { it.id == id }
            val target = (from + direction.coerceIn(-1, 1)).coerceIn(0, incomplete.lastIndex)
            if (from < 0 || from == target) return
            val targetPriority = incomplete[target].priority
            val moved = incomplete.removeAt(from).copy(priority = targetPriority)
            incomplete.add(target, moved)
            _uiState.update {
                it.copy(todos = incomplete + it.completedTodos)
            }
        }

        private fun commitDrag() {
            val state = _uiState.value
            if (!state.isDragging) return
            _uiState.update { it.copy(isDragging = false, draggedTodoId = null) }
            persistPlacements(state.incompleteTodos.map { TodoPlacement(it.id, it.priority) })
        }

        private fun accessibleMove(
            todoId: String,
            direction: Int,
        ) {
            val active = _uiState.value.incompleteTodos.toMutableList()
            val from = active.indexOfFirst { it.id == todoId }
            val target = (from + direction.coerceIn(-1, 1)).coerceIn(0, active.lastIndex)
            if (from < 0 || from == target) return
            val targetPriority = active[target].priority
            active.add(target, active.removeAt(from).copy(priority = targetPriority))
            persistPlacements(active.map { TodoPlacement(it.id, it.priority) })
        }

        private fun persistPlacements(placements: List<TodoPlacement>) {
            val date = _uiState.value.date
            runBusy {
                when (repository.reorder(date, placements)) {
                    is DataResult.Failure -> {
                        _uiState.update { it.copy(todos = persistedTodos) }
                        showMessage("待办顺序保存失败，已恢复原顺序")
                    }
                    is DataResult.Success -> Unit
                }
            }
        }

        private fun moveOlder(ids: Set<String>) {
            if (ids.isEmpty()) return
            val target = _uiState.value.date
            runBusy {
                when (repository.moveIncompleteToDate(ids, target)) {
                    is DataResult.Failure -> showMessage("迁移失败，所有待办均保持原日期")
                    is DataResult.Success -> showMessage("待办已移到今天")
                }
            }
        }

        private fun confirmDelete() {
            val id = _uiState.value.deleteConfirmationId ?: return
            runBusy {
                when (repository.delete(id)) {
                    is DataResult.Failure -> showMessage("删除失败，请重试")
                    is DataResult.Success -> _uiState.update { it.copy(deleteConfirmationId = null) }
                }
            }
        }

        private fun openLinkedRecord(todoId: String) {
            val todo = findTodo(todoId) ?: return
            if (todo.linkedContentBlockId == null) {
                showMessage("关联的工作记录已失效")
            } else {
                _events.trySend(TodayTodoUiEvent.OpenWorkEntry(todo.scheduledDate))
            }
        }

        private fun requestTextConversion(blockId: String) {
            val defaultDate =
                if (followsCurrentDate) {
                    _uiState.value.date
                } else {
                    _uiState.value.date.plusDays(1)
                }
            _uiState.update {
                it.copy(conversionPrompt = TodoConversionPrompt(blockId, defaultDate))
            }
        }

        private fun confirmTextConversion() {
            val prompt = _uiState.value.conversionPrompt ?: return
            runBusy {
                when (
                    val result =
                        createTodoFromTextBlock(
                            prompt.blockId,
                            prompt.scheduledDate,
                            prompt.priority,
                            prompt.duplicateWarning,
                        )
                ) {
                    is DataResult.Failure ->
                        if (result.error == DataError.Conflict) {
                            _uiState.update {
                                it.copy(conversionPrompt = prompt.copy(duplicateWarning = true))
                            }
                        } else {
                            showMessage("无法从该文字记录创建待办")
                        }
                    is DataResult.Success -> {
                        _uiState.update { it.copy(conversionPrompt = null) }
                        showMessage("已创建待办，原工作记录保持不变")
                    }
                }
            }
        }

        private fun findTodo(id: String): DailyTodo? =
            (_uiState.value.todos + _uiState.value.olderIncomplete).firstOrNull { it.id == id }

        private fun runBusy(block: suspend () -> Unit) {
            if (_uiState.value.isBusy) return
            viewModelScope.launch {
                _uiState.update { it.copy(isBusy = true) }
                try {
                    block()
                } finally {
                    _uiState.update { it.copy(isBusy = false) }
                }
            }
        }

        private fun showMessage(message: String) {
            _events.trySend(TodayTodoUiEvent.ShowMessage(message))
        }
    }

private fun DailyTodo.toEditorDraft(): TodoEditorDraft =
    TodoEditorDraft(
        id,
        title,
        note.orEmpty(),
        scheduledDate,
        priority,
        status,
        completionNote.orEmpty(),
    )

private fun String.toLocalDateOrNull(): LocalDate? = runCatching(LocalDate::parse).getOrNull()

private fun messageForCompletionFailure(error: DataError): String =
    when (error) {
        is DataError.Validation ->
            when (error.reason) {
                com.worklogai.app.core.common.result.DataValidationReason.FUTURE_WORK_ENTRY ->
                    "未来待办不能同步为未来工作记录"
                com.worklogai.app.core.common.result.DataValidationReason.ALREADY_LINKED ->
                    "该完成结果已同步；如需更新，请使用重新同步"
                else -> "待办内容不符合记录要求"
            }
        else -> "同步失败，待办和工作记录均未发生部分修改"
    }
