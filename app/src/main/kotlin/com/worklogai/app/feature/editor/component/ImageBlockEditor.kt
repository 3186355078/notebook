package com.worklogai.app.feature.editor.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.worklogai.app.feature.editor.ImageBlockUiModel
import java.io.File

@Composable
fun ImageBlockEditor(
    block: ImageBlockUiModel,
    controls: BlockControls,
    onCaptionChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(false) }
    val imageFile = block.relativePath?.let { File(LocalContext.current.filesDir, "attachments/$it") }
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("图片", modifier = Modifier.weight(1f))
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多图片操作")
                }
                ImageOverflowMenu(menuExpanded, { menuExpanded = false }, controls)
            }
            if (imageFile?.isFile == true) {
                AsyncImage(
                    model = imageFile,
                    contentDescription = "查看大图",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).clickable { previewVisible = true },
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                    Icon(Icons.Outlined.BrokenImage, contentDescription = "图片文件已不存在")
                    Text("图片文件已不存在", modifier = Modifier.padding(start = 8.dp))
                }
            }
            OutlinedTextField(
                value = block.caption,
                onValueChange = onCaptionChanged,
                label = { Text("图片说明（可选）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (previewVisible && imageFile?.isFile == true) {
        AlertDialog(
            onDismissRequest = { previewVisible = false },
            confirmButton = { TextButton(onClick = { previewVisible = false }) { Text("关闭") } },
            text = { AsyncImage(model = imageFile, contentDescription = "图片预览", modifier = Modifier.fillMaxWidth()) },
        )
    }
}

@Composable
internal fun ImageOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    controls: BlockControls,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("上移记录") }, onClick = {
            onDismiss()
            controls.onMoveUp()
        }, enabled = controls.canMoveUp)
        DropdownMenuItem(text = { Text("下移记录") }, onClick = {
            onDismiss()
            controls.onMoveDown()
        }, enabled = controls.canMoveDown)
        DropdownMenuItem(text = { Text("删除记录") }, onClick = {
            onDismiss()
            controls.onDelete()
        })
    }
}
