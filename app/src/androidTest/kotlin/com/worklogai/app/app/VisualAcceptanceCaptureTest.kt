package com.worklogai.app.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.navigation.NavDestination
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worklogai.app.app.navigation.TopLevelDestination
import com.worklogai.app.core.backup.BackupPreview
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.designsystem.theme.WorkLogTheme
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.DailyTodo
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.TableRow
import com.worklogai.app.core.model.TodoPriority
import com.worklogai.app.core.model.TodoStatus
import com.worklogai.app.core.model.WorkSummary
import com.worklogai.app.core.repository.TodoDateStats
import com.worklogai.app.feature.datamanagement.DataManagementContent
import com.worklogai.app.feature.datamanagement.DataManagementUiState
import com.worklogai.app.feature.editor.ImageBlockUiModel
import com.worklogai.app.feature.editor.SaveState
import com.worklogai.app.feature.editor.TableBlockUiModel
import com.worklogai.app.feature.editor.TextBlockUiModel
import com.worklogai.app.feature.editor.TodayScreenContent
import com.worklogai.app.feature.editor.TodayUiState
import com.worklogai.app.feature.history.HistoryItemUiModel
import com.worklogai.app.feature.history.HistoryMode
import com.worklogai.app.feature.history.HistoryScreenContent
import com.worklogai.app.feature.history.HistoryUiState
import com.worklogai.app.feature.settings.AiSettingsUiState
import com.worklogai.app.feature.settings.SettingsContent
import com.worklogai.app.feature.summary.SummaryContent
import com.worklogai.app.feature.summary.SummaryGenerationState
import com.worklogai.app.feature.summary.SummaryUiState
import com.worklogai.app.feature.todo.TodayTodoUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Captures deterministic, synthetic UI review images on a connected device.
 *
 * Output is written through MediaStore to Pictures/WorkLogAI-UI-Review-0.3.0.
 * The captured rectangle is the Compose test surface, so status/navigation bars
 * and their private device information are excluded.
 */
@RunWith(AndroidJUnit4::class)
class VisualAcceptanceCaptureTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val scene = mutableStateOf(VisualScene.TODAY_LIGHT)
    private var composeView: View? = null

    @Before
    fun prepareSyntheticAssets() {
        createSyntheticImages()
    }

    @Test
    fun capturePortraitMatrix() {
        composeRule.setContent {
            val currentScene = scene.value
            key(currentScene) {
                VisualReviewRoot(
                    scene = currentScene,
                    onViewAvailable = { composeView = it },
                )
            }
        }

        capture(VisualScene.TODAY_LIGHT, "today-light.png")
        capture(VisualScene.TODAY_DARK, "today-dark.png")
        capture(VisualScene.TODAY_DYNAMIC, "today-dynamic.png")
        capture(VisualScene.TODAY_EMPTY, "today-empty.png")
        capture(VisualScene.TODAY_MULTI, "today-multi.png")
        capture(VisualScene.TODAY_COLLAPSED, "today-completed-collapsed.png")
        capture(VisualScene.TODAY_QUICK_RECORD, "today-quick-record.png") {
            composeRule.onNodeWithText("今日工作记录").performScrollTo()
        }
        capture(VisualScene.TODAY_CONTENT, "today-content.png")
        capture(VisualScene.TODAY_FONT_15, "font-1.5x.png")

        capture(VisualScene.HISTORY_LIGHT, "history-light.png")
        capture(VisualScene.HISTORY_SEARCH, "history-search.png")
        capture(VisualScene.HISTORY_DAY, "history-day.png")
        capture(VisualScene.HISTORY_WEEK, "history-week.png")
        capture(VisualScene.HISTORY_MONTH, "history-month.png")
        capture(VisualScene.HISTORY_EMPTY, "history-empty.png")
        capture(VisualScene.HISTORY_NO_RESULTS, "history-no-results.png")
        capture(VisualScene.HISTORY_DARK, "history-dark.png")

        capture(VisualScene.SUMMARY_LIGHT, "summary-light.png")
        capture(VisualScene.SUMMARY_MONTHLY, "summary-monthly.png")
        capture(VisualScene.SUMMARY_GENERATING, "summary-generating.png")
        capture(VisualScene.SUMMARY_STALE, "summary-stale.png")
        capture(VisualScene.SUMMARY_FAILED, "summary-failed.png")
        capture(VisualScene.SUMMARY_EMPTY, "summary-empty.png")
        capture(VisualScene.SUMMARY_OVERFLOW, "summary-overflow.png") {
            composeRule.onNodeWithContentDescription("更多总结操作").performScrollTo().performClick()
        }
        capture(VisualScene.SUMMARY_DARK, "summary-dark.png")

        capture(VisualScene.SETTINGS_LIGHT, "settings-light.png")
        capture(VisualScene.SETTINGS_AI, "settings-ai.png") {
            composeRule.onNodeWithText("保存设置").performScrollTo()
        }
        capture(VisualScene.SETTINGS_AUTO, "settings-auto.png") {
            composeRule.onNodeWithText("自动生成周报").performScrollTo()
        }
        capture(VisualScene.SETTINGS_DATA, "settings-data.png") {
            composeRule.onNodeWithText("导出、备份与恢复").performScrollTo()
        }
        capture(VisualScene.SETTINGS_DARK, "settings-dark.png")
        capture(VisualScene.SETTINGS_FONT_15, "settings-font-1.5x.png")

        capture(VisualScene.DATA_LIGHT, "data-management-light.png")
        capture(VisualScene.DATA_PREVIEW, "data-management-preview.png")
        capture(VisualScene.DATA_ROLLED_BACK, "data-management-rolled-back.png") {
            composeRule.onNodeWithText("恢复失败，当前数据已完整回滚").performScrollTo()
        }
        capture(VisualScene.DATA_RECOVERY_REQUIRED, "data-management-recovery-required.png") {
            composeRule.onNodeWithText("恢复未能完整回滚，请重新启动后检查数据。").performScrollTo()
        }
        capture(VisualScene.DATA_DARK, "data-management-dark.png")
    }

    @Test
    fun captureLandscapeNavigationRail() {
        composeRule.setContent {
            VisualReviewRoot(
                scene = VisualScene.LANDSCAPE,
                onViewAvailable = { composeView = it },
            )
        }

        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(50, 2_000)
        captureCurrentScene("landscape.png")
    }

    private fun capture(
        targetScene: VisualScene,
        fileName: String,
        prepare: () -> Unit = {},
    ) {
        composeRule.runOnIdle { scene.value = targetScene }
        composeRule.waitForIdle()
        prepare()
        composeRule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(50, 2_000)
        captureCurrentScene(fileName)
    }

    private fun captureCurrentScene(fileName: String) {
        val view = checkNotNull(composeView) { "Compose review surface is unavailable" }
        val location = IntArray(2)
        val systemBarInsets = IntArray(4)
        composeRule.runOnIdle {
            view.getLocationOnScreen(location)
            view.rootWindowInsets
                ?.getInsets(WindowInsets.Type.systemBars())
                ?.let { insets ->
                    systemBarInsets[0] = insets.left
                    systemBarInsets[1] = insets.top
                    systemBarInsets[2] = insets.right
                    systemBarInsets[3] = insets.bottom
                }
        }

        val screen = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val left = (location[0] + systemBarInsets[0]).coerceIn(0, screen.width - 1)
        val top = (location[1] + systemBarInsets[1]).coerceIn(0, screen.height - 1)
        val contentWidth = view.width - systemBarInsets[0] - systemBarInsets[2]
        val contentHeight = view.height - systemBarInsets[1] - systemBarInsets[3]
        val width = min(contentWidth, screen.width - left)
        val height = min(contentHeight, screen.height - top)
        val cropped = Bitmap.createBitmap(screen, left, top, width, height)
        savePng(fileName, cropped)
        if (cropped !== screen) cropped.recycle()
        screen.recycle()
    }

    private fun savePng(
        fileName: String,
        bitmap: Bitmap,
    ) {
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val relativePath = "${Environment.DIRECTORY_PICTURES}/$OUTPUT_DIRECTORY/"
        resolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?",
            arrayOf(fileName, relativePath),
        )
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        resolver.openOutputStream(uri).use { stream ->
            checkNotNull(stream)
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun createSyntheticImages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "attachments/ui-review").apply { mkdirs() }
        writeDemoBitmap(File(directory, "demo-jpeg.jpg"), hasAlpha = false)
        writeDemoBitmap(File(directory, "demo-png.png"), hasAlpha = true)
    }

    private fun writeDemoBitmap(
        file: File,
        hasAlpha: Boolean,
    ) {
        val bitmap = Bitmap.createBitmap(960, 640, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(if (hasAlpha) Color.TRANSPARENT else Color.rgb(229, 240, 239))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(44, 111, 118)
        canvas.drawRoundRect(80f, 80f, 880f, 560f, 56f, 56f, paint)
        paint.color = Color.rgb(226, 237, 255)
        canvas.drawCircle(300f, 300f, 128f, paint)
        paint.color = Color.rgb(244, 177, 94)
        canvas.drawRoundRect(500f, 185f, 790f, 455f, 42f, 42f, paint)
        FileOutputStream(file).use { output ->
            bitmap.compress(
                if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                92,
                output,
            )
        }
        bitmap.recycle()
    }

    private companion object {
        const val OUTPUT_DIRECTORY = "WorkLogAI-UI-Review-0.3.0"
    }
}

private enum class VisualScene {
    TODAY_LIGHT,
    TODAY_DARK,
    TODAY_DYNAMIC,
    TODAY_EMPTY,
    TODAY_MULTI,
    TODAY_COLLAPSED,
    TODAY_QUICK_RECORD,
    TODAY_CONTENT,
    TODAY_FONT_15,
    HISTORY_LIGHT,
    HISTORY_SEARCH,
    HISTORY_DAY,
    HISTORY_WEEK,
    HISTORY_MONTH,
    HISTORY_EMPTY,
    HISTORY_NO_RESULTS,
    HISTORY_DARK,
    SUMMARY_LIGHT,
    SUMMARY_MONTHLY,
    SUMMARY_GENERATING,
    SUMMARY_STALE,
    SUMMARY_FAILED,
    SUMMARY_EMPTY,
    SUMMARY_OVERFLOW,
    SUMMARY_DARK,
    SETTINGS_LIGHT,
    SETTINGS_AI,
    SETTINGS_AUTO,
    SETTINGS_DATA,
    SETTINGS_DARK,
    SETTINGS_FONT_15,
    DATA_LIGHT,
    DATA_PREVIEW,
    DATA_ROLLED_BACK,
    DATA_RECOVERY_REQUIRED,
    DATA_DARK,
    LANDSCAPE,
}

@Composable
private fun VisualReviewRoot(
    scene: VisualScene,
    onViewAvailable: (View) -> Unit,
) {
    val view = LocalView.current
    SideEffect { onViewAvailable(view) }
    val dark = scene in DARK_SCENES
    val dynamic = scene == VisualScene.TODAY_DYNAMIC
    val fontScale =
        if (scene == VisualScene.TODAY_FONT_15 || scene == VisualScene.SETTINGS_FONT_15) {
            1.5f
        } else {
            1f
        }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        WorkLogTheme(darkTheme = dark, dynamicColor = dynamic) {
            Surface(modifier = Modifier.fillMaxSize()) {
                VisualReviewShell(scene)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisualReviewShell(scene: VisualScene) {
    when {
        scene == VisualScene.LANDSCAPE ->
            Row(Modifier.fillMaxSize()) {
                WorkLogNavigationRail(
                    visible = true,
                    currentDestination = visualDestination(TopLevelDestination.TODAY),
                    onDestinationSelected = {},
                )
                Box(Modifier.weight(1f)) {
                    TodayReviewContent(scene)
                }
            }
        scene in DATA_SCENES ->
            Scaffold(
                topBar = { TopAppBar(title = { Text("数据管理") }) },
            ) { padding ->
                DataReviewContent(scene, Modifier.padding(padding))
            }
        else ->
            Scaffold(
                bottomBar = {
                    WorkLogBottomBar(
                        visible = true,
                        currentDestination = visualDestination(scene.destination()),
                        onDestinationSelected = {},
                    )
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (scene) {
                        in TODAY_SCENES -> TodayReviewContent(scene)
                        in HISTORY_SCENES -> HistoryReviewContent(scene)
                        in SUMMARY_SCENES -> SummaryReviewContent(scene)
                        in SETTINGS_SCENES -> SettingsReviewContent(scene)
                        else -> Unit
                    }
                }
            }
    }
}

@Suppress("DEPRECATION")
private fun visualDestination(destination: TopLevelDestination): NavDestination =
    NavDestination("visual-review").apply { route = destination.route }

private fun VisualScene.destination(): TopLevelDestination =
    when (this) {
        in HISTORY_SCENES -> TopLevelDestination.HISTORY
        in SUMMARY_SCENES -> TopLevelDestination.SUMMARY
        in SETTINGS_SCENES -> TopLevelDestination.SETTINGS
        else -> TopLevelDestination.TODAY
    }

@Composable
private fun TodayReviewContent(scene: VisualScene) {
    val emptyTodos = scene == VisualScene.TODAY_EMPTY || scene == VisualScene.TODAY_CONTENT
    val todos =
        when {
            emptyTodos -> emptyList()
            scene == VisualScene.TODAY_QUICK_RECORD -> DEMO_TODOS.take(2)
            else -> DEMO_TODOS
        }
    val blocks =
        when (scene) {
            VisualScene.TODAY_EMPTY,
            VisualScene.TODAY_MULTI,
            VisualScene.TODAY_COLLAPSED,
            -> emptyList()
            else -> DEMO_BLOCKS
        }
    TodayScreenContent(
        state =
            TodayUiState(
                date = REVIEW_DATE,
                isLoading = false,
                entryId = "review-entry",
                blocks = blocks,
                saveState = SaveState.Saved(REVIEW_INSTANT),
                focusedBlockId = if (scene == VisualScene.TODAY_CONTENT) "review-table" else null,
            ),
        todoState =
            TodayTodoUiState(
                date = REVIEW_DATE,
                isLoading = false,
                todos = todos,
                olderIncomplete = if (scene == VisualScene.TODAY_MULTI) listOf(DEMO_OLDER_TODO) else emptyList(),
                quickTitle = if (scene == VisualScene.TODAY_QUICK_RECORD) "补充内部试用反馈" else "",
                completedCollapsed = scene == VisualScene.TODAY_COLLAPSED,
            ),
        snackbarHostState = remember { SnackbarHostState() },
        onAction = {},
        onTodoAction = {},
        onPickImage = {},
    )
}

@Composable
private fun HistoryReviewContent(scene: VisualScene) {
    val mode =
        when (scene) {
            VisualScene.HISTORY_DAY -> HistoryMode.DAY
            VisualScene.HISTORY_WEEK -> HistoryMode.WEEK
            VisualScene.HISTORY_MONTH -> HistoryMode.MONTH
            else -> HistoryMode.RECENT
        }
    val query =
        when (scene) {
            VisualScene.HISTORY_SEARCH -> "界面验收"
            VisualScene.HISTORY_NO_RESULTS -> "不存在的模拟关键词"
            else -> ""
        }
    val items =
        if (scene == VisualScene.HISTORY_EMPTY || scene == VisualScene.HISTORY_NO_RESULTS) {
            emptyList()
        } else {
            DEMO_HISTORY
        }
    HistoryScreenContent(
        state =
            HistoryUiState(
                mode = mode,
                selectedDate = REVIEW_DATE,
                selectedWeekStart = LocalDate.of(2026, 7, 20),
                selectedMonth = YearMonth.of(2026, 7),
                query = query,
                items = items,
                isLoading = false,
                isSearching = query.isNotBlank(),
                canLoadMore = items.isNotEmpty(),
            ),
        snackbarHostState = remember { SnackbarHostState() },
        onAction = {},
        todoStats = DEMO_HISTORY.associate { it.date to TodoDateStats(total = 6, done = 4) },
    )
}

@Composable
private fun SummaryReviewContent(scene: VisualScene) {
    val monthly = scene == VisualScene.SUMMARY_MONTHLY
    val type = if (monthly) SummaryType.MONTHLY else SummaryType.WEEKLY
    val period =
        if (monthly) {
            DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))
        } else {
            DateRange(LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 19))
        }
    val summary =
        if (scene == VisualScene.SUMMARY_EMPTY || scene == VisualScene.SUMMARY_FAILED) {
            null
        } else {
            demoSummary(type, period)
        }
    val generation =
        when (scene) {
            VisualScene.SUMMARY_GENERATING -> SummaryGenerationState.Generating
            VisualScene.SUMMARY_FAILED -> SummaryGenerationState.Failed("演示服务暂时不可用，请稍后重试", false)
            else -> SummaryGenerationState.Idle
        }
    SummaryContent(
        state =
            SummaryUiState(
                summaryType = type,
                period = period,
                selectedMonth = YearMonth.of(2026, 6),
                isLoading = false,
                entryCount = 12,
                eligibleEntryCount = 10,
                summary = summary,
                generationState = generation,
                isOutdated = scene == VisualScene.SUMMARY_STALE,
            ),
        onAction = {},
    )
}

@Composable
private fun SettingsReviewContent(
    @Suppress("UNUSED_PARAMETER") scene: VisualScene,
) {
    SettingsContent(
        state =
            AiSettingsUiState(
                settings =
                    AiSettings(
                        baseUrl = "https://example.com/v1",
                        model = "example-model",
                        useMockProvider = false,
                        autoWeeklySummaryEnabled = true,
                        autoMonthlySummaryEnabled = false,
                        notifyOnAutoSummaryCompletion = true,
                        autoSummaryConsentAcknowledged = true,
                    ),
                apiKeyInput = "",
                hasApiKey = true,
                isLoading = false,
                notificationPermissionGranted = true,
            ),
        onAction = {},
        onOpenDataManagement = {},
    )
}

@Composable
private fun DataReviewContent(
    scene: VisualScene,
    modifier: Modifier = Modifier,
) {
    val state =
        when (scene) {
            VisualScene.DATA_PREVIEW ->
                DataManagementUiState(
                    exportDate = REVIEW_DATE.toString(),
                    restorePreview =
                        BackupPreview(
                            createdAt = REVIEW_INSTANT,
                            entryCount = 18,
                            blockCount = 32,
                            attachmentCount = 4,
                            summaryCount = 2,
                            warningCount = 1,
                            todoCount = 12,
                        ),
                )
            VisualScene.DATA_ROLLED_BACK ->
                DataManagementUiState(
                    exportDate = REVIEW_DATE.toString(),
                    warningMessage = "恢复失败，当前数据已完整回滚",
                )
            VisualScene.DATA_RECOVERY_REQUIRED ->
                DataManagementUiState(
                    exportDate = REVIEW_DATE.toString(),
                    warningMessage = "启动修复将在下次打开应用时继续处理",
                    requiresRecovery = true,
                )
            else -> DataManagementUiState(exportDate = REVIEW_DATE.toString())
        }
    DataManagementContent(
        state = state,
        snackbarHost = {},
        onAction = {},
        modifier = modifier,
    )
}

private fun demoSummary(
    type: SummaryType,
    period: DateRange,
) = WorkSummary(
    id = "review-summary-${type.name.lowercase()}",
    summaryType = type,
    periodStart = period.start,
    periodEnd = period.end,
    status = SummaryStatus.SUCCESS,
    sourceHash = "synthetic-source-hash",
    aiProvider = "mock",
    modelName = "mock",
    originalContent =
        """
        本期概览
        完成了内部演示环境的界面校对，工作节奏稳定。

        完成事项
        · 核对核心页面的信息层级
        · 整理模拟验收清单

        进行中
        · 继续观察深色模式和大字体表现

        问题与解决
        · 通过统一间距和状态标签减少视觉噪声

        下一步计划
        · 收集内部试用反馈并做小范围调整
        """.trimIndent(),
    editedContent = null,
    errorMessage = null,
    createdAt = REVIEW_INSTANT,
    updatedAt = REVIEW_INSTANT,
    generatedAt = REVIEW_INSTANT,
)

private val REVIEW_DATE = LocalDate.of(2026, 7, 26)
private val REVIEW_INSTANT = Instant.parse("2026-07-26T08:00:00Z")
private val DEMO_TODO_ORDER = AtomicInteger()

private val DEMO_TODOS =
    listOf(
        demoTodo("todo-urgent", "完成视觉验收清单", "优先检查 Today 首屏层级", TodoPriority.URGENT),
        demoTodo("todo-high", "核对深色模式对比度", "检查标题、状态和分隔线", TodoPriority.HIGH),
        demoTodo(
            "todo-progress",
            "整理内部试用反馈",
            "汇总无障碍和横屏体验",
            TodoPriority.MEDIUM,
            TodoStatus.IN_PROGRESS,
        ),
        demoTodo("todo-medium", "检查数据管理安全提示", null, TodoPriority.MEDIUM),
        demoTodo(
            "todo-done",
            "完成模拟周报",
            "使用非敏感演示数据",
            TodoPriority.LOW,
            TodoStatus.DONE,
            linked = true,
        ),
        demoTodo(
            "todo-cancelled",
            "旧版图标方案",
            "已由统一线性图标替代",
            TodoPriority.LOW,
            TodoStatus.CANCELED,
        ),
    )

private val DEMO_OLDER_TODO =
    demoTodo(
        "todo-older",
        "补充昨日的可用性记录",
        "仅用于演示迁移入口",
        TodoPriority.MEDIUM,
    ).copy(scheduledDate = REVIEW_DATE.minusDays(1))

private fun demoTodo(
    id: String,
    title: String,
    note: String?,
    priority: TodoPriority,
    status: TodoStatus = TodoStatus.NOT_STARTED,
    linked: Boolean = false,
) = DailyTodo(
    id = id,
    scheduledDate = REVIEW_DATE,
    title = title,
    note = note,
    priority = priority,
    status = status,
    sortOrder = DEMO_TODO_ORDER.getAndIncrement(),
    completionNote = if (status == TodoStatus.DONE) "已完成演示核对" else null,
    linkedContentBlockId = if (linked) "review-text" else null,
    createdAt = REVIEW_INSTANT,
    updatedAt = REVIEW_INSTANT,
    completedAt = if (status == TodoStatus.DONE) REVIEW_INSTANT else null,
)

private val DEMO_BLOCKS =
    listOf(
        TextBlockUiModel(
            id = "review-text",
            order = 0,
            text = "完成 0.3.0 界面演示数据整理。\n所有内容均为模拟信息。",
            isSaving = false,
            hasSaveError = false,
        ),
        ImageBlockUiModel(
            id = "review-image-jpeg",
            order = 1,
            attachmentId = "review-attachment-jpeg",
            relativePath = "ui-review/demo-jpeg.jpg",
            caption = "模拟界面色彩参考图",
            isSaving = false,
            hasSaveError = false,
        ),
        ImageBlockUiModel(
            id = "review-image-png",
            order = 2,
            attachmentId = "review-attachment-png",
            relativePath = "ui-review/demo-png.png",
            caption = "透明 PNG 演示素材",
            isSaving = false,
            hasSaveError = false,
        ),
        TableBlockUiModel(
            id = "review-table",
            order = 3,
            content =
                TableContent(
                    title = "发布节奏",
                    columns =
                        listOf(
                            TableColumn("item", "检查项"),
                            TableColumn("status", "状态"),
                        ),
                    rows =
                        listOf(
                            TableRow("row-1", mapOf("item" to "视觉材料", "status" to "完成")),
                            TableRow("row-2", mapOf("item" to "用户确认", "status" to "待确认")),
                        ),
                ),
            isSaving = false,
            hasSaveError = false,
        ),
    )

private val DEMO_HISTORY =
    (0 until 10).map { index ->
        val date = REVIEW_DATE.minusDays(index.toLong())
        HistoryItemUiModel(
            entryId = "history-$index",
            date = date,
            dateLabel = "${date.monthValue}月${date.dayOfMonth}日",
            weekdayLabel = listOf("周日", "周六", "周五", "周四", "周三", "周二", "周一")[index % 7],
            previewText =
                listOf(
                    "完成界面验收并整理内部反馈",
                    "核对待办优先级与状态表达",
                    "检查历史时间线和搜索体验",
                    "验证深色模式下的文字对比度",
                )[index % 4],
            contentSummary = "${1 + index % 3} 条文字 · ${index % 2} 张图片 · ${index % 2} 个表格",
        )
    }

private val TODAY_SCENES =
    setOf(
        VisualScene.TODAY_LIGHT,
        VisualScene.TODAY_DARK,
        VisualScene.TODAY_DYNAMIC,
        VisualScene.TODAY_EMPTY,
        VisualScene.TODAY_MULTI,
        VisualScene.TODAY_COLLAPSED,
        VisualScene.TODAY_QUICK_RECORD,
        VisualScene.TODAY_CONTENT,
        VisualScene.TODAY_FONT_15,
    )

private val HISTORY_SCENES =
    setOf(
        VisualScene.HISTORY_LIGHT,
        VisualScene.HISTORY_SEARCH,
        VisualScene.HISTORY_DAY,
        VisualScene.HISTORY_WEEK,
        VisualScene.HISTORY_MONTH,
        VisualScene.HISTORY_EMPTY,
        VisualScene.HISTORY_NO_RESULTS,
        VisualScene.HISTORY_DARK,
    )

private val SUMMARY_SCENES =
    setOf(
        VisualScene.SUMMARY_LIGHT,
        VisualScene.SUMMARY_MONTHLY,
        VisualScene.SUMMARY_GENERATING,
        VisualScene.SUMMARY_STALE,
        VisualScene.SUMMARY_FAILED,
        VisualScene.SUMMARY_EMPTY,
        VisualScene.SUMMARY_OVERFLOW,
        VisualScene.SUMMARY_DARK,
    )

private val SETTINGS_SCENES =
    setOf(
        VisualScene.SETTINGS_LIGHT,
        VisualScene.SETTINGS_AI,
        VisualScene.SETTINGS_AUTO,
        VisualScene.SETTINGS_DATA,
        VisualScene.SETTINGS_DARK,
        VisualScene.SETTINGS_FONT_15,
    )

private val DATA_SCENES =
    setOf(
        VisualScene.DATA_LIGHT,
        VisualScene.DATA_PREVIEW,
        VisualScene.DATA_ROLLED_BACK,
        VisualScene.DATA_RECOVERY_REQUIRED,
        VisualScene.DATA_DARK,
    )

private val DARK_SCENES =
    setOf(
        VisualScene.TODAY_DARK,
        VisualScene.HISTORY_DARK,
        VisualScene.SUMMARY_DARK,
        VisualScene.SETTINGS_DARK,
        VisualScene.DATA_DARK,
    )
