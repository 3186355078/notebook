package com.worklogai.app.feature.editor.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.worklogai.app.R
import com.worklogai.app.core.designsystem.theme.WorkLogTheme
import com.worklogai.app.feature.editor.TextBlockUiModel

@Composable
fun TextBlockEditor(
    block: TextBlockUiModel,
    requestFocus: Boolean,
    controls: BlockControls,
    callbacks: TextBlockCallbacks,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember(block.id) { FocusRequester() }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            callbacks.onFocusRequestHandled()
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout
                    .Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.today_more_actions),
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.today_move_up)) },
                        onClick = {
                            menuExpanded = false
                            controls.onMoveUp()
                        },
                        enabled = controls.canMoveUp,
                        leadingIcon = { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.today_move_down)) },
                        onClick = {
                            menuExpanded = false
                            controls.onMoveDown()
                        },
                        enabled = controls.canMoveDown,
                        leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.today_delete)) },
                        onClick = {
                            menuExpanded = false
                            controls.onDelete()
                        },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                    )
                }
            }
            OutlinedTextField(
                value = block.text,
                onValueChange = callbacks.onTextChanged,
                minLines = 4,
                maxLines = Int.MAX_VALUE,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { callbacks.onFocusChanged(it.isFocused) },
                supportingText = {
                    if (block.hasSaveError) {
                        Text(
                            text = stringResource(R.string.today_save_failed),
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (block.isSaving) {
                        Text(text = stringResource(R.string.today_save_saving))
                    }
                },
            )
        }
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun TextBlockEditorPreview() {
    WorkLogTheme {
        TextBlockEditor(
            block =
                TextBlockUiModel(
                    id = "preview-text",
                    order = 0,
                    text = "完成今天的接口联调。\n整理了异常场景。",
                    isSaving = false,
                    hasSaveError = false,
                ),
            requestFocus = false,
            controls = BlockControls(false, true, {}, {}, {}),
            callbacks = TextBlockCallbacks({}, {}, {}),
        )
    }
}
