package com.worklogai.app.core.history

import com.worklogai.app.core.model.Attachment
import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.TableRow
import com.worklogai.app.core.model.WorkEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WorkEntrySummaryBuilderTest {
    private val builder = DefaultWorkEntrySummaryBuilder(DefaultWorkEntryVisibilityPolicy())
    private val date = LocalDate.of(2026, 7, 12)
    private val instant = Instant.parse("2026-07-12T08:00:00Z")

    @Test
    fun `empty entry and whitespace text are not visible`() {
        assertNull(builder.build(entry()))
        assertNull(builder.build(entry(blocks = listOf(text(" \n  ")))))
    }

    @Test
    fun `first meaningful text becomes normalized preview`() {
        val summary =
            builder.build(
                entry(blocks = listOf(text("  \n  \n"), text("\u5b8c\u6210  \n \u8054\u8c03  ", order = 1))),
            )!!

        assertEquals("\u5b8c\u6210 \u8054\u8c03", summary.previewText)
        assertEquals(1, summary.textBlockCount)
    }

    @Test
    fun `long preview keeps emoji code points intact`() {
        val summary = builder.build(entry(blocks = listOf(text("\ud83d\ude80".repeat(101)))))!!

        assertTrue(summary.previewText.endsWith("\u2026"))
        assertEquals(101, summary.previewText.codePointCount(0, summary.previewText.length))
    }

    @Test
    fun `image and table fallbacks are stable and do not include paths`() {
        val imageSummary = builder.build(entry(blocks = listOf(image("images/private.jpg"))))!!
        val tableSummary = builder.build(entry(blocks = listOf(table(title = "\u6d4b\u8bd5\u7ed3\u679c"))))!!
        val mixedSummary = builder.build(entry(blocks = listOf(image("images/private.jpg"), table())))!!

        assertEquals("\u56fe\u7247\u8bb0\u5f55", imageSummary.previewText)
        assertEquals("\u6d4b\u8bd5\u7ed3\u679c", tableSummary.previewText)
        assertEquals("\u5305\u542b\u56fe\u7247\u548c\u8868\u683c", mixedSummary.previewText)
        assertTrue(imageSummary.previewText.none { it == '/' })
    }

    @Test
    fun `title only entry is visible and block ordering controls preview`() {
        val titleSummary = builder.build(entry(title = "  \u5468\u672b\u503c\u73ed  "))!!
        val textSummary =
            builder.build(
                entry(blocks = listOf(text("\u7b2c\u4e8c\u6761", 1), text("\u7b2c\u4e00\u6761", 0))),
            )!!

        assertEquals("\u5468\u672b\u503c\u73ed", titleSummary.previewText)
        assertEquals("\u7b2c\u4e00\u6761", textSummary.previewText)
    }

    private fun entry(
        title: String? = null,
        blocks: List<ContentBlock> = emptyList(),
    ) = WorkEntry(
        id = "entry",
        entryDate = date,
        title = title,
        allowAiProcessing = true,
        isDeleted = false,
        createdAt = instant,
        updatedAt = instant,
        blocks = blocks,
    )

    private fun text(
        value: String,
        order: Int = 0,
    ) = ContentBlock.Text("text-$order", "entry", order, value, instant, instant)

    private fun image(path: String) =
        ContentBlock.Image(
            id = "image",
            entryId = "entry",
            order = 0,
            attachments = listOf(Attachment("attachment", "image", path, "image/jpeg", 1, null, null, null, instant)),
            createdAt = instant,
            updatedAt = instant,
        )

    private fun table(title: String? = null) =
        ContentBlock.Table(
            id = "table",
            entryId = "entry",
            order = 1,
            content =
                TableContent(
                    title = title,
                    columns = listOf(TableColumn("column", "\u6a21\u5757")),
                    rows = listOf(TableRow("row", mapOf("column" to "\u767b\u5f55"))),
                ),
            createdAt = instant,
            updatedAt = instant,
        )
}
