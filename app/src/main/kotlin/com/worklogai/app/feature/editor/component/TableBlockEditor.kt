package com.worklogai.app.feature.editor.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.worklogai.app.core.designsystem.component.WorkLogContentSurface
import com.worklogai.app.core.designsystem.component.WorkLogSectionHeader
import com.worklogai.app.core.designsystem.theme.WorkLogSpacing
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableRow
import com.worklogai.app.feature.editor.TableBlockUiModel

private val TABLE_COLUMN_WIDTH = 120.dp
private val TABLE_ROW_ACTION_SIZE = 20.dp

@Composable
fun TableBlockEditor(
    block: TableBlockUiModel,
    controls: BlockControls,
    callbacks: TableBlockCallbacks,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val content = block.content
    WorkLogContentSurface(modifier = modifier) {
        Column(
            modifier = Modifier.padding(WorkLogSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(WorkLogSpacing.small),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                WorkLogSectionHeader(
                    title = "表格",
                    description = "${content.rows.size} 行 × ${content.columns.size} 列，可横向滚动",
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "更多表格操作")
                }
                ImageOverflowMenu(menuExpanded, { menuExpanded = false }, controls)
            }
            OutlinedTextField(
                value = content.title.orEmpty(),
                onValueChange = callbacks.onTitleChanged,
                label = { Text("表格标题（可选）") },
                shape = MaterialTheme.shapes.medium,
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
            Row(horizontalArrangement = Arrangement.spacedBy(WorkLogSpacing.small)) {
                OutlinedButton(
                    onClick = callbacks.onAddRow,
                    enabled = content.rows.size < 50,
                    contentPadding = PaddingValues(horizontal = WorkLogSpacing.medium, vertical = WorkLogSpacing.small),
                    modifier = Modifier.testTag("table_add_row_${block.id}"),
                ) {
                    Text("添加一行")
                }
                OutlinedButton(
                    onClick = callbacks.onAddColumn,
                    enabled = content.columns.size < 8,
                    contentPadding = PaddingValues(horizontal = WorkLogSpacing.medium, vertical = WorkLogSpacing.small),
                    modifier = Modifier.testTag("table_add_column_${block.id}"),
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
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(WorkLogSpacing.small),
                ).padding(vertical = WorkLogSpacing.extraSmall),
        verticalAlignment = Alignment.Top,
    ) {
        columns.forEach { column ->
            Column(
                modifier =
                    Modifier.padding(
                        start = WorkLogSpacing.extraSmall,
                        end = WorkLogSpacing.extraSmall,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OutlinedTextField(
                    value = column.name,
                    onValueChange = { onColumnNameChanged(column.id, it) },
                    placeholder = { Text("列名") },
                    shape = MaterialTheme.shapes.small,
                    textStyle = MaterialTheme.typography.titleSmall,
                    minLines = 1,
                    modifier =
                        Modifier
                            .width(TABLE_COLUMN_WIDTH)
                            .testTag("table_column_name_${column.id}"),
                )
                IconButton(
                    onClick = { onDeleteColumn(column.id) },
                    enabled = canDelete,
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "删除列",
                        modifier = Modifier.size(TABLE_ROW_ACTION_SIZE),
                    )
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
    Row(
        modifier = Modifier.padding(top = WorkLogSpacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        columns.forEach { column ->
            OutlinedTextField(
                value = row.cells[column.id].orEmpty(),
                onValueChange = { onCellChanged(row.id, column.id, it) },
                placeholder = { Text(column.name.ifBlank { "列" }) },
                shape = MaterialTheme.shapes.small,
                minLines = 1,
                modifier =
                    Modifier
                        .padding(horizontal = WorkLogSpacing.extraSmall)
                        .width(TABLE_COLUMN_WIDTH)
                        .testTag("table_cell_${row.id}_${column.id}"),
            )
        }
        IconButton(
            onClick = { onDeleteRow(row.id) },
            enabled = canDelete,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "删除行",
                modifier = Modifier.size(TABLE_ROW_ACTION_SIZE),
            )
        }
    }
}
