package com.worklogai.app.feature.editor

import android.net.Uri
import java.time.LocalDate

sealed interface TodayAction {
    data object AddTextBlock : TodayAction

    data object AddTableBlock : TodayAction

    data class ImageSelected(
        val uri: Uri,
    ) : TodayAction

    data class ImageCaptionChanged(
        val blockId: String,
        val caption: String,
    ) : TodayAction

    data class TableTitleChanged(
        val blockId: String,
        val title: String,
    ) : TodayAction

    data class TableColumnNameChanged(
        val blockId: String,
        val columnId: String,
        val name: String,
    ) : TodayAction

    data class TableCellChanged(
        val blockId: String,
        val rowId: String,
        val columnId: String,
        val value: String,
    ) : TodayAction

    data class AddTableRow(
        val blockId: String,
    ) : TodayAction

    data class DeleteTableRow(
        val blockId: String,
        val rowId: String,
    ) : TodayAction

    data class AddTableColumn(
        val blockId: String,
    ) : TodayAction

    data class DeleteTableColumn(
        val blockId: String,
        val columnId: String,
    ) : TodayAction

    data class TextChanged(
        val blockId: String,
        val text: String,
    ) : TodayAction

    data class TextFocusChanged(
        val blockId: String,
        val isFocused: Boolean,
    ) : TodayAction

    data class MoveBlockUp(
        val blockId: String,
    ) : TodayAction

    data class MoveBlockDown(
        val blockId: String,
    ) : TodayAction

    data class RequestDeleteBlock(
        val blockId: String,
    ) : TodayAction

    data class ConfirmDeleteBlock(
        val blockId: String,
    ) : TodayAction

    data object CancelDeleteBlock : TodayAction

    data object FocusRequestConsumed : TodayAction

    data object LinkedBlockHighlightConsumed : TodayAction

    data class ShowLinkedBlock(
        val blockId: String,
    ) : TodayAction

    data object FlushPendingEdits : TodayAction

    data object RetryLoad : TodayAction

    data object RetryFailedSaves : TodayAction

    data class DateChanged(
        val date: LocalDate,
    ) : TodayAction
}

sealed interface TodayUiEvent {
    data class ShowMessage(
        val message: String,
    ) : TodayUiEvent
}
