package com.worklogai.app.feature.todo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.worklogai.app.core.designsystem.theme.WorkLogTheme
import com.worklogai.app.core.model.DailyTodo
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class TodayTodoSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateAndQuickAddExposePrimaryAction() {
        val actions =
            render(TodayTodoUiState(DATE, isLoading = false)) { state, action ->
                when (action) {
                    is TodayTodoAction.QuickTitleChanged -> state.copy(quickTitle = action.value)
                    TodayTodoAction.QuickAdd -> state.copy(quickTitle = "")
                    else -> state
                }
            }

        composeRule.onNodeWithText("今天还没有待办").assertIsDisplayed()
        composeRule.onNodeWithTag("quick_add_todo").assertIsNotEnabled()
        composeRule.onNodeWithTag("quick_todo_input").performTextInput("整理接口")
        composeRule.onNodeWithTag("quick_add_todo").assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertTrue(actions.contains(TodayTodoAction.QuickTitleChanged("整理接口")))
            assertEquals(TodayTodoAction.QuickAdd, actions.last())
        }
    }

    @Test
    fun prioritiesStatusesAndProgressHaveTextSemantics() {
        render(
            state(
                todo("urgent", "修复阻断问题", TodoPriority.URGENT, TodoStatus.NOT_STARTED),
                todo("working", "联调", TodoPriority.HIGH, TodoStatus.IN_PROGRESS),
                todo("done", "归档", TodoPriority.LOW, TodoStatus.DONE),
            ),
        )

        composeRule.onNodeWithText("已完成 1/3 · 进行中 1").assertIsDisplayed()
        composeRule.onNodeWithText("紧急").assertIsDisplayed()
        composeRule.onNodeWithText("高").assertIsDisplayed()
        composeRule.onNodeWithText("进行中").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("今日待办完成进度").assertIsDisplayed()
    }

    @Test
    fun completionAndStatusMenuEmitExplicitActions() {
        val item = todo("todo", "整理接口", TodoPriority.MEDIUM, TodoStatus.NOT_STARTED)
        val actions = render(state(item))

        composeRule.onNodeWithContentDescription("将“整理接口”标记为已完成").performClick()
        composeRule.onNodeWithContentDescription("“整理接口”更多操作").performClick()
        composeRule.onNodeWithText("设为进行中").performClick()

        composeRule.runOnIdle {
            assertTrue(actions.contains(TodayTodoAction.ToggleDone("todo")))
            assertEquals(TodayTodoAction.ChangeStatus("todo", TodoStatus.IN_PROGRESS), actions.last())
        }
    }

    @Test
    fun completionDialogOffersLocalOnlyAndWorkRecordChoices() {
        val actions =
            render(
                state(todo("todo", "完成联调")).copy(
                    completionPrompt = TodoCompletionPrompt("todo", "完成联调"),
                ),
            )

        composeRule.onNodeWithText("仅完成待办").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(TodayTodoAction.CompleteOnly, actions.last()) }
    }

    @Test
    fun editorSupportsPriorityDateAndCompletionNote() {
        val draft =
            TodoEditorDraft(
                id = "todo",
                title = "完整编辑",
                scheduledDate = DATE,
                status = TodoStatus.IN_PROGRESS,
            )
        val actions = render(state().copy(editor = draft))

        composeRule.onNodeWithTag("todo_editor_title").assertIsDisplayed()
        composeRule.onNodeWithText("高").performClick()
        composeRule.onNodeWithContentDescription("后一天").performClick()
        composeRule.onNodeWithTag("save_todo_editor").performClick()

        composeRule.runOnIdle {
            assertTrue(actions.any { it is TodayTodoAction.UpdateEditor })
            assertEquals(TodayTodoAction.SaveEditor, actions.last())
        }
    }

    @Test
    fun olderIncompleteAndCompletedCollapseAreAvailable() {
        val actions =
            render(
                state(todo("done", "已完成", status = TodoStatus.DONE)).copy(
                    olderIncomplete = listOf(todo("old", "昨日遗留").copy(scheduledDate = DATE.minusDays(1))),
                ),
            )

        composeRule.onNodeWithText("有 1 项未完成待办").assertIsDisplayed()
        composeRule.onNodeWithText("移到今天").performClick()
        composeRule.onNodeWithTag("toggle_completed_todos").performClick()

        composeRule.runOnIdle {
            assertTrue(actions.contains(TodayTodoAction.MoveOlderToToday("old")))
            assertEquals(TodayTodoAction.ToggleCompletedCollapsed, actions.last())
        }
    }

    @Test
    fun linkedTodoProvidesOpenAndResyncActions() {
        val linked = todo("linked", "已同步", status = TodoStatus.DONE).copy(linkedContentBlockId = "block")
        val actions = render(state(linked))

        composeRule.onNodeWithText("查看记录").performClick()
        composeRule.onNodeWithContentDescription("“已同步”更多操作").performClick()
        composeRule.onNodeWithText("重新同步为新的工作记录").performClick()

        composeRule.runOnIdle {
            assertTrue(actions.contains(TodayTodoAction.OpenLinkedRecord("linked")))
            assertEquals(TodayTodoAction.RequestResync("linked"), actions.last())
        }
    }

    @Test
    fun linkedRecordActionDisablesAfterFirstActivation() {
        val linked = todo("linked", "已同步", status = TodoStatus.DONE).copy(linkedContentBlockId = "block")
        val actions =
            render(state(linked)) { current, action ->
                if (action == TodayTodoAction.OpenLinkedRecord("linked")) {
                    current.copy(navigatingLinkedTodoId = "linked")
                } else {
                    current
                }
            }

        composeRule.onNodeWithTag("linked_record_linked").performClick()
        composeRule.onNodeWithTag("linked_record_linked").assertIsNotEnabled()

        composeRule.runOnIdle {
            assertEquals(
                1,
                actions.count { it == TodayTodoAction.OpenLinkedRecord("linked") },
            )
        }
    }

    @Test
    fun deleteRequiresConfirmationAndExplainsThatWorkRecordIsPreserved() {
        val item = todo("delete", "只删除待办")
        val actions =
            render(state(item)) { currentState, action ->
                when (action) {
                    is TodayTodoAction.RequestDelete ->
                        currentState.copy(deleteConfirmationId = action.todoId)
                    TodayTodoAction.DismissDelete ->
                        currentState.copy(deleteConfirmationId = null)
                    else -> currentState
                }
            }

        composeRule.onNodeWithContentDescription("“只删除待办”更多操作").performClick()
        composeRule.onNodeWithText("删除待办").performClick()

        composeRule.onNodeWithText("删除待办？").assertIsDisplayed()
        composeRule.onNodeWithText("“只删除待办”将被删除，已关联的历史工作记录不会被删除。").assertIsDisplayed()
        composeRule.onNodeWithText("删除").performClick()
        composeRule.runOnIdle { assertEquals(TodayTodoAction.ConfirmDelete, actions.last()) }
    }

    @Test
    fun fiftyTodosRemainReachableThroughLazyListScrolling() {
        val items =
            (0 until 50).map { index ->
                todo("todo-$index", "待办 $index").copy(sortOrder = index)
            }
        render(
            TodayTodoUiState(
                date = DATE,
                isLoading = false,
                todos = items,
            ),
        )

        composeRule.onNodeWithTag("todo_todo-0").assertIsDisplayed()
        composeRule.onNodeWithTag("todo_list").performScrollToIndex(49)
        composeRule.onNodeWithTag("todo_todo-49").assertIsDisplayed()
    }

    @Test
    fun largeFontKeepsQuickAddAndEditorEntryVisible() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.5f)) {
                WorkLogTheme(dynamicColor = false) {
                    TodayTodoSection(state(), {})
                }
            }
        }

        composeRule.onNodeWithTag("quick_todo_input").assertIsDisplayed()
        composeRule.onNodeWithTag("open_full_todo_editor").assertIsDisplayed()
    }

    private fun render(
        state: TodayTodoUiState,
        reducer: (TodayTodoUiState, TodayTodoAction) -> TodayTodoUiState = { current, _ -> current },
    ): MutableList<TodayTodoAction> {
        val actions = mutableListOf<TodayTodoAction>()
        var renderedState by mutableStateOf(state)
        composeRule.setContent {
            WorkLogTheme(dynamicColor = false) {
                TodayTodoSection(
                    state = renderedState,
                    onAction = { action ->
                        actions += action
                        renderedState = reducer(renderedState, action)
                    },
                )
            }
        }
        return actions
    }

    private fun state(vararg todos: DailyTodo) =
        TodayTodoUiState(
            date = DATE,
            isLoading = false,
            todos = todos.toList(),
        )

    private fun todo(
        id: String,
        title: String,
        priority: TodoPriority = TodoPriority.MEDIUM,
        status: TodoStatus = TodoStatus.NOT_STARTED,
    ) = DailyTodo(
        id,
        DATE,
        title,
        null,
        priority,
        status,
        0,
        null,
        null,
        NOW,
        NOW,
        if (status == TodoStatus.DONE) NOW else null,
    )

    private companion object {
        val DATE: LocalDate = LocalDate.of(2026, 7, 24)
        val NOW: Instant = Instant.parse("2026-07-24T08:00:00Z")
    }
}
