package com.worklogai.app.feature.editor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.worklogai.app.core.designsystem.theme.WorkLogTheme
import java.time.LocalDate

private val PreviewDate = LocalDate.of(2026, 7, 12)

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun TodayEmptyPreview() {
    WorkLogTheme {
        TodayScreenContent(
            state =
                TodayUiState(
                    date = PreviewDate,
                    isLoading = false,
                    entryId = "preview-entry",
                ),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun TodaySaveFailurePreview() {
    WorkLogTheme(darkTheme = true) {
        TodayScreenContent(
            state =
                TodayUiState(
                    date = PreviewDate,
                    isLoading = false,
                    entryId = "preview-entry",
                    blocks =
                        listOf(
                            TextBlockUiModel(
                                id = "preview-text",
                                order = 0,
                                text = "这段内容尚未保存。",
                                isSaving = false,
                                hasSaveError = true,
                            ),
                        ),
                    saveState = SaveState.Failed("保存失败，内容仍保留在当前页面"),
                ),
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}
