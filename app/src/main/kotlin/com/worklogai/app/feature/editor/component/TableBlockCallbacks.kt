package com.worklogai.app.feature.editor.component

data class TableBlockCallbacks(
    val onTitleChanged: (String) -> Unit,
    val onColumnNameChanged: (String, String) -> Unit,
    val onCellChanged: (String, String, String) -> Unit,
    val onAddRow: () -> Unit,
    val onDeleteRow: (String) -> Unit,
    val onAddColumn: () -> Unit,
    val onDeleteColumn: (String) -> Unit,
)
