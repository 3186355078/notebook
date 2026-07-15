package com.worklogai.app.core.export

import com.worklogai.app.ai.skill.worksummary.WorkSummarySkill
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.model.Attachment
import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.SummaryStatus
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.TableColumn
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.TableRow
import com.worklogai.app.core.model.WorkEntry
import com.worklogai.app.core.model.WorkSummary
import com.worklogai.app.core.repository.WorkEntryRepository
import com.worklogai.app.core.repository.WorkSummaryRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MarkdownExportServiceTest {
    private val entries = mockk<WorkEntryRepository>()
    private val summaries = mockk<WorkSummaryRepository>()
    private val skill = mockk<WorkSummarySkill>()
    private val service = DefaultMarkdownExportService(entries, summaries, skill, Dispatchers.Unconfined)

    @Test
    fun `entry markdown preserves block order and keeps attachment paths private`() {
        runBlocking {
            val date = LocalDate.of(2026, 7, 14)
            coEvery { entries.getEntry(date) } returns DataResult.Success(entry(date))

            val result = service.createEntryDocument(date) as MarkdownExportResult.Success

            assertEquals("工作记录_2026-07-14.md", result.document.suggestedFileName)
            assertTrue(result.document.content.indexOf("文字记录 1") < result.document.content.indexOf("图片记录 2"))
            assertTrue(result.document.content.contains("登录联调"))
            assertTrue(result.document.content.contains("\\|"))
            assertTrue(result.document.content.contains("<br>"))
            assertFalse(result.document.content.contains("images/private.jpg"))
            assertFalse(result.document.content.contains("entry-1"))
        }
    }

    @Test
    fun `empty entry has no markdown document`() {
        runBlocking {
            val date = LocalDate.of(2026, 7, 14)
            coEvery { entries.getEntry(date) } returns
                DataResult.Success(entry(date, blocks = emptyList(), title = null))

            assertEquals(MarkdownExportResult.Empty, service.createEntryDocument(date))
        }
    }

    @Test
    fun `missing entry is reported without creating a document`() {
        runBlocking {
            coEvery { entries.getEntry(any()) } returns DataResult.Success(null)

            assertEquals(MarkdownExportResult.NotFound, service.createEntryDocument(LocalDate.of(2026, 7, 14)))
        }
    }

    @Test
    fun `summary export prefers edited content and excludes provider data`() {
        runBlocking {
            val summary = summary(edited = "人工确认后的周报", original = "raw-provider-json")
            coEvery { summaries.getSummary(any(), any(), any()) } returns DataResult.Success(summary)

            val result =
                service.createSummaryDocument(
                    summary.summaryType,
                    summary.periodStart,
                    summary.periodEnd,
                ) as MarkdownExportResult.Success

            assertEquals("工作周报_2026-07-06_2026-07-12.md", result.document.suggestedFileName)
            assertTrue(result.document.content.contains("人工确认后的周报"))
            assertFalse(result.document.content.contains("raw-provider-json"))
            assertFalse(result.document.content.contains("provider-key"))
        }
    }

    @Test
    fun `summary without displayable content is empty`() {
        runBlocking {
            val summary = summary(edited = null, original = null)
            coEvery { summaries.getSummary(any(), any(), any()) } returns DataResult.Success(summary)

            assertEquals(
                MarkdownExportResult.Empty,
                service.createSummaryDocument(summary.summaryType, summary.periodStart, summary.periodEnd),
            )
        }
    }

    private fun entry(
        date: LocalDate,
        title: String? = "工作标题",
        blocks: List<ContentBlock> =
            listOf(
                ContentBlock.Text("text", "entry-1", 0, "完成登录模块", NOW, NOW),
                ContentBlock.Image(
                    "image",
                    "entry-1",
                    1,
                    listOf(Attachment("attachment", "image", "images/private.jpg", "image/jpeg", 4, 2, 2, "登录联调", NOW)),
                    NOW,
                    NOW,
                ),
                ContentBlock.Table(
                    "table",
                    "entry-1",
                    2,
                    TableContent(
                        "测试|结果",
                        listOf(TableColumn("column", "模块|名称")),
                        listOf(
                            TableRow(
                                "row",
                                mapOf(
                                    "column" to "通过\n稳定",
                                ),
                            ),
                        ),
                    ),
                    NOW,
                    NOW,
                ),
            ),
    ) = WorkEntry("entry-1", date, title, true, false, NOW, NOW, blocks)

    private fun summary(
        edited: String?,
        original: String?,
    ) = WorkSummary(
        "summary",
        SummaryType.WEEKLY,
        LocalDate.of(2026, 7, 6),
        LocalDate.of(2026, 7, 12),
        SummaryStatus.SUCCESS,
        "source",
        "provider-key",
        "model",
        original,
        edited,
        null,
        NOW,
        NOW,
        NOW,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-14T08:00:00Z")
    }
}
