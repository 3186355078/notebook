package com.worklogai.app.core.table

import com.worklogai.app.core.common.id.IdGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTableContentEditorTest {
    private val editor = DefaultTableContentEditor(IncrementingIdGenerator())

    @Test
    fun `default table has stable two by two structure`() {
        val table = editor.createDefault()

        assertEquals(2, table.columns.size)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("列1", "列2"), table.columns.map { it.name })
        assertEquals(
            2,
            table.columns
                .map { it.id }
                .distinct()
                .size,
        )
        assertEquals(
            2,
            table.rows
                .map { it.id }
                .distinct()
                .size,
        )
        assertEquals(
            table.columns.map { it.id }.toSet(),
            table.rows
                .first()
                .cells.keys,
        )
    }

    @Test
    fun `delete column also removes its cells`() {
        val table = editor.createDefault()
        val removedColumn = table.columns.first()

        val updated = editor.deleteColumn(table, removedColumn.id)

        assertNotNull(updated)
        assertFalse(updated!!.rows.any { removedColumn.id in it.cells })
    }

    @Test
    fun `last row and column cannot be removed`() {
        val table = editor.createDefault()
        val oneRow = editor.deleteRow(table, table.rows.first().id)!!
        val oneColumn = editor.deleteColumn(table, table.columns.first().id)!!

        assertNull(editor.deleteRow(oneRow, oneRow.rows.single().id))
        assertNull(editor.deleteColumn(oneColumn, oneColumn.columns.single().id))
    }

    @Test
    fun `row and column limits are enforced`() {
        var table = editor.createDefault()
        repeat(48) { table = editor.addRow(table)!! }
        assertNull(editor.addRow(table))
        assertEquals(DefaultTableContentEditor.MAX_ROWS, table.rows.size)

        var columns = editor.createDefault()
        repeat(6) { columns = editor.addColumn(columns)!! }
        assertNull(editor.addColumn(columns))
        assertEquals(DefaultTableContentEditor.MAX_COLUMNS, columns.columns.size)
    }

    @Test
    fun `cell title and column changes preserve ids`() {
        val table = editor.createDefault()
        val row = table.rows.first()
        val column = table.columns.first()

        val updated =
            editor
                .updateTitle(table, "测试表")
                .let { editor.updateColumnName(it, column.id, "项目") }
                .let { editor.updateCell(it, row.id, column.id, "含有换行\n与引号\"的值") }

        assertEquals("测试表", updated.title)
        assertEquals("项目", updated.columns.first().name)
        assertEquals("含有换行\n与引号\"的值", updated.rows.first().cells[column.id])
    }

    @Test
    fun `adding row and column creates stable ids and cells for every row`() {
        val table = editor.createDefault()

        val withRow = editor.addRow(table)!!
        val withColumn = editor.addColumn(withRow)!!
        val addedColumn = withColumn.columns.last()

        assertEquals(3, withColumn.rows.size)
        assertEquals(3, withColumn.columns.size)
        assertEquals(
            3,
            withColumn.rows
                .map { it.id }
                .distinct()
                .size,
        )
        assertTrue(withColumn.rows.all { it.cells[addedColumn.id] == "" })
        assertEquals(2, table.rows.size)
        assertEquals(2, table.columns.size)
    }

    @Test
    fun `deleting a column preserves unrelated cells and leaves source unchanged`() {
        val table = editor.createDefault()
        val retainedColumn = table.columns.last()
        val removedColumn = table.columns.first()
        val source = editor.updateCell(table, table.rows.first().id, retainedColumn.id, "keep")

        val updated = editor.deleteColumn(source, removedColumn.id)!!

        assertEquals("keep", updated.rows.first().cells[retainedColumn.id])
        assertFalse(updated.rows.any { removedColumn.id in it.cells })
        assertTrue(removedColumn.id in source.rows.first().cells)
    }

    @Test
    fun `invalid row and column targets leave content unchanged`() {
        val table = editor.createDefault()

        assertEquals(table, editor.updateCell(table, "missing-row", table.columns.first().id, "x"))
        assertEquals(table, editor.updateCell(table, table.rows.first().id, "missing-column", "x"))
        assertEquals(table, editor.updateColumnName(table, "missing-column", "name"))
        assertNull(editor.deleteRow(table, "missing-row"))
        assertNull(editor.deleteColumn(table, "missing-column"))
    }

    @Test
    fun `special characters and blank title survive editing without changing source`() {
        val table = editor.createDefault()
        val row = table.rows.first()
        val column = table.columns.first()
        val updated =
            editor
                .updateTitle(table, "  中文 😀 table  ")
                .let { editor.updateColumnName(it, column.id, "column\n\"name\"") }
                .let { editor.updateCell(it, row.id, column.id, "line\nquote\" emoji 😀") }

        assertEquals("中文 😀 table", updated.title)
        assertEquals("column\n\"name\"", updated.columns.first().name)
        assertEquals("line\nquote\" emoji 😀", updated.rows.first().cells[column.id])
        assertNull(editor.updateTitle(table, "   ").title)
        assertNull(table.title)
    }
}

private class IncrementingIdGenerator : IdGenerator {
    private var value = 0

    override fun generate(): String = "id-${value++}"
}
