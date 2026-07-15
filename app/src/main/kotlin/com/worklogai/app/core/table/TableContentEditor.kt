package com.worklogai.app.core.table

import com.worklogai.app.core.common.id.IdGenerator
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.TableRow
import javax.inject.Inject

interface TableContentEditor {
    fun createDefault(): TableContent

    fun updateTitle(
        content: TableContent,
        title: String,
    ): TableContent

    fun updateColumnName(
        content: TableContent,
        columnId: String,
        name: String,
    ): TableContent

    fun updateCell(
        content: TableContent,
        rowId: String,
        columnId: String,
        value: String,
    ): TableContent

    fun addRow(content: TableContent): TableContent?

    fun deleteRow(
        content: TableContent,
        rowId: String,
    ): TableContent?

    fun addColumn(content: TableContent): TableContent?

    fun deleteColumn(
        content: TableContent,
        columnId: String,
    ): TableContent?
}

class DefaultTableContentEditor
    @Inject
    constructor(
        private val idGenerator: IdGenerator,
    ) : TableContentEditor {
        override fun createDefault(): TableContent {
            val columns = List(DEFAULT_COLUMN_COUNT) { index -> TableColumn(idGenerator.generate(), "列${index + 1}") }
            return TableContent(columns = columns, rows = List(DEFAULT_ROW_COUNT) { newRow(columns) })
        }

        override fun updateTitle(
            content: TableContent,
            title: String,
        ): TableContent = content.copy(title = title.trim().takeIf(String::isNotEmpty))

        override fun updateColumnName(
            content: TableContent,
            columnId: String,
            name: String,
        ): TableContent =
            content.copy(
                columns =
                    content.columns.map { column ->
                        if (column.id ==
                            columnId
                        ) {
                            column.copy(name = name)
                        } else {
                            column
                        }
                    },
            )

        override fun updateCell(
            content: TableContent,
            rowId: String,
            columnId: String,
            value: String,
        ): TableContent =
            content.copy(
                rows =
                    content.rows.map { row ->
                        if (row.id == rowId &&
                            content.columns.any { it.id == columnId }
                        ) {
                            row.copy(cells = row.cells + (columnId to value))
                        } else {
                            row
                        }
                    },
            )

        override fun addRow(content: TableContent): TableContent? =
            if (content.rows.size >= MAX_ROWS) null else content.copy(rows = content.rows + newRow(content.columns))

        override fun deleteRow(
            content: TableContent,
            rowId: String,
        ): TableContent? =
            if (content.rows.size <= MIN_ROWS ||
                content.rows.none { it.id == rowId }
            ) {
                null
            } else {
                content.copy(
                    rows =
                        content.rows.filterNot {
                            it.id ==
                                rowId
                        },
                )
            }

        override fun addColumn(content: TableContent): TableContent? {
            if (content.columns.size >= MAX_COLUMNS) return null
            val column = TableColumn(idGenerator.generate(), "列${content.columns.size + 1}")
            return content.copy(
                columns = content.columns + column,
                rows =
                    content.rows.map {
                        it.copy(
                            cells =
                                it.cells + (column.id to ""),
                        )
                    },
            )
        }

        override fun deleteColumn(
            content: TableContent,
            columnId: String,
        ): TableContent? {
            if (content.columns.size <= MIN_COLUMNS || content.columns.none { it.id == columnId }) return null
            return content.copy(
                columns = content.columns.filterNot { it.id == columnId },
                rows = content.rows.map { row -> row.copy(cells = row.cells - columnId) },
            )
        }

        private fun newRow(columns: List<TableColumn>): TableRow =
            TableRow(id = idGenerator.generate(), cells = columns.associate { it.id to "" })

        companion object {
            const val DEFAULT_COLUMN_COUNT = 2
            const val DEFAULT_ROW_COUNT = 2
            const val MIN_COLUMNS = 1
            const val MIN_ROWS = 1
            const val MAX_COLUMNS = 8
            const val MAX_ROWS = 50
        }
    }
