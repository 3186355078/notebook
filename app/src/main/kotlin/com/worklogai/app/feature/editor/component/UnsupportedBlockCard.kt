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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.worklogai.app.R
import com.worklogai.app.core.model.ContentBlockType
import com.worklogai.app.feature.editor.UnsupportedBlockUiModel

@Composable
fun UnsupportedBlockCard(
    block: UnsupportedBlockUiModel,
    controls: BlockControls,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val message =
        when (block.blockType) {
            ContentBlockType.IMAGE -> stringResource(R.string.today_image_placeholder)
            ContentBlockType.TABLE -> stringResource(R.string.today_table_placeholder)
            ContentBlockType.TEXT -> return
        }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = message, modifier = Modifier.weight(1f))
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.today_more_actions))
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
        }
    }
}
