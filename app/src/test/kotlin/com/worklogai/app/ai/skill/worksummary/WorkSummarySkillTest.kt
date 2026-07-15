package com.worklogai.app.ai.skill.worksummary

import com.worklogai.app.core.model.Attachment
import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.TableRow
import com.worklogai.app.core.model.WorkEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class WorkSummarySkillTest {
    private val request =
        WorkSummarySkillRequest(
            SummaryType.WEEKLY,
            LocalDate.of(2026, 7, 13),
            LocalDate.of(2026, 7, 19),
            "zh-CN",
        )

    @Test
    fun `input builder filters disallowed entries and omits image paths and ids`() {
        val allowed = entry("entry-a", LocalDate.of(2026, 7, 13), true)
        val blocked = entry("entry-b", LocalDate.of(2026, 7, 14), false)

        val result = DefaultWorkSummaryInputBuilder().build(listOf(blocked, allowed), request)
        val serialized = SkillJson.instance.encodeToString(WorkSummarySkillInput.serializer(), result.input)

        assertEquals(listOf("2026-07-13"), result.input.entries.map { it.date })
        assertTrue(serialized.contains("完成登录模块"))
        assertTrue(serialized.contains("联调截图"))
        assertTrue(serialized.contains("模块"))
        assertFalse(serialized.contains("images/private.jpg"))
        assertFalse(serialized.contains("entry-a"))
    }

    @Test
    fun `input limiter truncates safely and parser sanitizes markdown and invalid dates`() {
        val input =
            WorkSummarySkillInput(
                SummaryType.WEEKLY,
                "2026-07-13",
                "2026-07-19",
                "zh-CN",
                listOf(
                    WorkSummaryEntryInput(
                        "2026-07-13",
                        contents =
                            listOf(
                                WorkSummaryContentInput(WorkSummaryContentType.TEXT, text = "😀".repeat(200)),
                            ),
                    ),
                ),
            )
        val limited = DefaultSummaryInputLimiter().limit(input, 250)
        val response =
            """
            说明
            ```json
            {"title":"周报","completedItems":[{"content":"完成登录","sourceDates":["2026-07-13","2025-01-01"]}]}
            ```
            """.trimIndent()
        val parsed =
            DefaultWorkSummaryOutputParser()
                .parse(
                    response,
                    request,
                ).getOrThrow()

        assertTrue(limited.wasTruncated)
        assertTrue(SkillJson.instance.encodeToString(WorkSummarySkillInput.serializer(), limited.input).length <= 250)
        assertEquals(listOf("2026-07-13"), parsed.completedItems.single().sourceDates)
        assertEquals("# 周报", MarkdownWorkSummaryFormatter().format(parsed).lineSequence().first())
    }

    @Test
    fun `parser rejects malformed content and removes duplicate blank items`() {
        val parser = DefaultWorkSummaryOutputParser()
        assertTrue(parser.parse("[1]", request).isFailure)
        val response =
            """
            {"title":"x","nextActions":[{"content":"  "},{"content":"继续测试"},{"content":"继续测试"}]}
            """.trimIndent()

        val parsed =
            parser
                .parse(
                    response,
                    request,
                ).getOrThrow()
        assertEquals(listOf("继续测试"), parsed.nextActions.map { it.content })
    }

    @Test
    fun `parser only keeps source dates that supplied model input`() {
        val response =
            """
            {"title":"x","completedItems":[{"content":"已完成","sourceDates":["2026-07-13","2026-07-14"]}]}
            """.trimIndent()
        val parsed =
            DefaultWorkSummaryOutputParser()
                .parse(
                    response,
                    request.copy(sourceDates = setOf(LocalDate.of(2026, 7, 13))),
                ).getOrThrow()

        assertEquals(listOf("2026-07-13"), parsed.completedItems.single().sourceDates)
    }

    private fun entry(
        id: String,
        date: LocalDate,
        allowAi: Boolean,
    ): WorkEntry {
        val now = Instant.parse("2026-07-13T00:00:00Z")
        val table =
            TableContent(
                title = "测试结果",
                columns = listOf(TableColumn("column", "模块")),
                rows = listOf(TableRow("row", mapOf("column" to "通过"))),
            )
        return WorkEntry(
            id = id,
            entryDate = date,
            title = "登录",
            allowAiProcessing = allowAi,
            isDeleted = false,
            createdAt = now,
            updatedAt = now,
            blocks =
                listOf(
                    ContentBlock.Text("text", id, 0, "完成登录模块", now, now),
                    ContentBlock.Image(
                        "image",
                        id,
                        1,
                        listOf(
                            Attachment("attachment", "image", "images/private.jpg", "image/jpeg", 1, 1, 1, "联调截图", now),
                        ),
                        now,
                        now,
                    ),
                    ContentBlock.Table("table", id, 2, table, now, now),
                ),
        )
    }
}
