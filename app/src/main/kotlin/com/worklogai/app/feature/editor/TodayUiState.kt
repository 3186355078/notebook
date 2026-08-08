package com.worklogai.app.feature.editor

import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.core.model.TableContent
import java.time.Instant
import java.time.LocalDate

data class TodayUiState(
    val date: LocalDate,
    val followsCurrentDate: Boolean = true,
    val isLoading: Boolean = true,
    val entryId: String? = null,
    val blocks: List<EditorBlockUiModel> = emptyList(),
    val saveState: SaveState = SaveState.Idle,
    val focusedBlockId: String? = null,
    val highlightedBlockId: String? = null,
    val pendingDeleteBlockId: String? = null,
    val errorMessage: String? = null,
    val isStructureOperationInProgress: Boolean = false,
    val isImageImporting: Boolean = false,
    val isFuturePlanning: Boolean = false,
) {
    val canEdit: Boolean
        get() = !isLoading && !isFuturePlanning && errorMessage == null
}

sealed interface SaveState {
    data object Idle : SaveState

    data object Saving : SaveState

    data class Saved(
        val savedAt: Instant,
    ) : SaveState

    data class Failed(
        val message: String,
    ) : SaveState
}

sealed interface EditorBlockUiModel {
    val id: String
    val order: Int
}

data class TextBlockUiModel(
    override val id: String,
    override val order: Int,
    val text: String,
    val isSaving: Boolean,
    val hasSaveError: Boolean,
    val isPending: Boolean = false,
) : EditorBlockUiModel

data class UnsupportedBlockUiModel(
    override val id: String,
    override val order: Int,
    val blockType: ContentBlockType,
) : EditorBlockUiModel

data class ImageBlockUiModel(
    override val id: String,
    override val order: Int,
    val attachmentId: String?,
    val relativePath: String?,
    val caption: String,
    val isSaving: Boolean,
    val hasSaveError: Boolean,
) : EditorBlockUiModel

data class TableBlockUiModel(
    override val id: String,
    override val order: Int,
    val content: TableContent,
    val isSaving: Boolean,
    val hasSaveError: Boolean,
) : EditorBlockUiModel
