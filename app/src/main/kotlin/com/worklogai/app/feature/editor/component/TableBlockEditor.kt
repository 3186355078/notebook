package com.worklogai.app.feature.editor.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableRow
import com.worklogai.app.feature.editor.TableBlockUiModel

@Composable
fun TableBlockEditor(
    block: TableBlockUiModel,
    controls: BlockControls,
    callbacks: TableBlockCallbacks,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val content = block.content
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("表格", modifier = Modifier.weight(1f))
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多表格操作")
                }
                ImageOverflowMenu(menuExpanded, { menuExpanded = false }, controls)
            }
            OutlinedTextField(
                value = content.title.orEmpty(),
                onValueChange = callbacks.onTitleChanged,
                label = { Text("表格标题（可选）") },
                modifier = Modifier.fillMaxWidth().testTag("table_title_${block.id}"),
            )
            Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                TableHeader(
                    columns = content.columns,
                    onColumnNameChanged = callbacks.onColumnNameChanged,
                    onDeleteColumn = callbacks.onDeleteColumn,
                    canDelete = content.columns.size > 1,
                )
                content.rows.forEach { row ->
                    TableRowEditor(
                        row = row,
                        columns = content.columns,
                        onCellChanged = callbacks.onCellChanged,
                        onDeleteRow = callbacks.onDeleteRow,
                        canDelete = content.rows.size > 1,
                    )
                }
            }
            Row {
                OutlinedButton(
                    onClick = callbacks.onAddRow,
                    enabled = content.rows.size < 50,
                    modifier = Modifier.testTag("table_add_row_${block.id}"),
                ) {
                    Text("添加一行")
                }
                OutlinedButton(
                    onClick = callbacks.onAddColumn,
                    enabled = content.columns.size < 8,
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .testTag("table_add_column_${block.id}"),
                ) {
                    Text("添加一列")
                }
            }
        }
    }
}

@Composable
private fun TableHeader(
    columns: List<TableColumn>,
    onColumnNameChanged: (String, String) -> Unit,
    onDeleteColumn: (String) -> Unit,
    canDelete: Boolean,
) {
    Row {
        columns.forEach { column ->
            Column(modifier = Modifier.padding(end = 8.dp)) {
                OutlinedTextField(
                    value = column.name,
                    onValueChange = { onColumnNameChanged(column.id, it) },
                    label = { Text("列名") },
                )
                OutlinedButton(onClick = { onDeleteColumn(column.id) }, enabled = canDelete) {
                    Text("删除列")
                }
            }
        }
    }
}

@Composable
private fun TableRowEditor(
    row: TableRow,
    columns: List<TableColumn>,
    onCellChanged: (String, String, String) -> Unit,
    onDeleteRow: (String) -> Unit,
    canDelete: Boolean,
) {
    Row(modifier = Modifier.padding(top = 8.dp)) {
        columns.forEach { column ->
            OutlinedTextField(
                value = row.cells[column.id].orEmpty(),
                onValueChange = { onCellChanged(row.id, column.id, it) },
                label = { Text(column.name.ifBlank { "列" }) },
                minLines = 1,
                modifier =
                    Modifier
                        .padding(end = 8.dp)
                        .testTag("table_cell_${row.id}_${column.id}"),
            )
        }
        OutlinedButton(onClick = { onDeleteRow(row.id) }, enabled = canDelete) {
            Text("删除行")
        }
    }
}
