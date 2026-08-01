package com.worklogai.app.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.withTransaction
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worklogai.app.core.backup.BackupOperationResult
import com.worklogai.app.core.database.entity.ContentBlockEntity
import com.worklogai.app.core.database.entity.TodoEntity
import com.worklogai.app.core.database.entity.WorkEntryEntity
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class LinkedRecordNavigationIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun tenRapidActivationsOpenOneEntryAndOneBackReturnsToToday() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dependencies = EntryPointAccessors.fromApplication(context, Stage9TestEntryPoint::class.java)
        val original = ByteArrayOutputStream()
        assertTrue(
            runBlocking {
                dependencies.backupArchiveService().createBackup(original)
            } is BackupOperationResult.Success,
        )

        try {
            seedLinkedTodo(dependencies)
            composeRule.waitUntil(timeoutMillis = UI_TIMEOUT_MILLIS) {
                composeRule
                    .onAllNodes(
                        androidx.compose.ui.test
                            .hasTestTag(LINKED_RECORD_TAG),
                    ).fetchSemanticsNodes()
                    .isNotEmpty()
            }
            val linkedRecordNode =
                composeRule
                    .onNodeWithTag(LINKED_RECORD_TAG)
                    .fetchSemanticsNode()
            composeRule.runOnUiThread {
                val clickAction = linkedRecordNode.config[SemanticsActions.OnClick]
                repeat(RAPID_ACTIVATION_COUNT) {
                    clickAction.action?.invoke()
                }
            }

            composeRule.onNodeWithText("工作记录").assertIsDisplayed()
            pressBack()
            composeRule.onNodeWithTag("today_todo_section").assertIsDisplayed()

            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("today_todo_section").assertIsDisplayed()
            composeRule.onNodeWithTag(LINKED_RECORD_TAG).assertIsDisplayed().performClick()
            composeRule.onNodeWithText("工作记录").assertIsDisplayed()
            pressBack()
            composeRule.onNodeWithTag("today_todo_section").assertIsDisplayed()
        } finally {
            val restored =
                runBlocking {
                    dependencies
                        .backupArchiveService()
                        .restoreBackup(ByteArrayInputStream(original.toByteArray()))
                }
            assertTrue(restored is BackupOperationResult.Success)
        }
    }

    private fun seedLinkedTodo(dependencies: Stage9TestEntryPoint) {
        runBlocking {
            val database = dependencies.database()
            val date = LocalDate.now()
            val now = Instant.now()
            database.clearAllTables()
            database.withTransaction {
                database.workEntryDao().insert(
                    WorkEntryEntity(
                        id = ENTRY_ID,
                        entryDate = date,
                        title = "导航测试",
                        allowAiProcessing = false,
                        isDeleted = false,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                database.contentBlockDao().insert(
                    ContentBlockEntity(
                        id = BLOCK_ID,
                        entryId = ENTRY_ID,
                        blockType = ContentBlockType.TEXT,
                        blockOrder = 0,
                        textContent = "已完成模拟联调记录",
                        structuredContent = null,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                database.todoDao().insert(
                    TodoEntity(
                        id = TODO_ID,
                        scheduledDate = date,
                        title = "检查同步记录导航",
                        note = null,
                        priority = TodoPriority.HIGH,
                        status = TodoStatus.DONE,
                        sortOrder = 0,
                        completionNote = "模拟完成说明",
                        linkedContentBlockId = BLOCK_ID,
                        createdAt = now,
                        updatedAt = now,
                        completedAt = now,
                    ),
                )
            }
        }
    }

    private companion object {
        const val ENTRY_ID = "linked-navigation-entry"
        const val BLOCK_ID = "linked-navigation-block"
        const val TODO_ID = "linked-navigation-todo"
        const val LINKED_RECORD_TAG = "linked_record_$TODO_ID"
        const val RAPID_ACTIVATION_COUNT = 10
        const val UI_TIMEOUT_MILLIS = 10_000L
    }
}
