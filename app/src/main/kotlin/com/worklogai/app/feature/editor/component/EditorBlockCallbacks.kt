package com.worklogai.app.feature.editor.component

data class BlockControls(
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val onMoveUp: () -> Unit,
    val onMoveDown: () -> Unit,
    val onDelete: () -> Unit,
    val onConvertToTodo: (() -> Unit)? = null,
)

data class TextBlockCallbacks(
    val onTextChanged: (String) -> Unit,
    val onFocusChanged: (Boolean) -> Unit,
    val onFocusRequestHandled: () -> Unit,
)
