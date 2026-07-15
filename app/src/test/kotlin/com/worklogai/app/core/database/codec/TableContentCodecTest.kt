package com.worklogai.app.core.database.codec

import com.worklogai.app.core.common.result.DataError
import com.worklogai.app.core.database.failureValue
import com.worklogai.app.core.database.successValue
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.TableRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TableContentCodecTest {
    private val codec = KotlinxTableContentCodec()

    @Test
    fun `round trips table with Chinese special characters and line breaks`() {
        val table =
            TableContent(
                title = "本周\"测试\"",
                columns =
                    listOf(
                        TableColumn(id = "module", name = "模块"),
                        TableColumn(id = "status", name = "状态"),
                    ),
                rows =
                    listOf(
                        TableRow(
                            id = "row-1",
                            cells = mapOf("module" to "登录\n接口", "status" to "通过 & 完成"),
                        ),
                    ),
            )

        val encoded = codec.encode(table).successValue()

        assertEquals(table, codec.decode(encoded).successValue())
    }

    @Test
    fun `accepts an empty table and missing optional title`() {
        val empty = TableContent()

        assertEquals(empty, codec.decode(codec.encode(empty).successValue()).successValue())
        assertEquals(empty, codec.decode("{}").successValue())
    }

    @Test
    fun `rejects malformed json and cells without a known column`() {
        val malformed = codec.decode("{not-json}").failureValue()
        val invalidTable =
            TableContent(
                columns = listOf(TableColumn(id = "known", name = "列")),
                rows = listOf(TableRow(id = "row", cells = mapOf("missing" to "值"))),
            )

        assertTrue(malformed is DataError.Validation)
        assertTrue(codec.encode(invalidTable).failureValue() is DataError.Validation)
    }
}
