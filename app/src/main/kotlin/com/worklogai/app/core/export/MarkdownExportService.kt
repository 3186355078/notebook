package com.worklogai.app.core.export

import com.worklogai.app.ai.skill.worksummary.WorkSummarySkill
import com.worklogai.app.ai.skill.worksummary.WorkSummarySkillRequest
import com.worklogai.app.core.common.di.IoDispatcher
import com.worklogai.app.core.common.result.DataResult
import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.WorkEntry
import com.worklogai.app.core.model.WorkSummary
import com.worklogai.app.core.repository.WorkEntryRepository
import com.worklogai.app.core.repository.WorkSummaryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class MarkdownDocument(
    val suggestedFileName: String,
    val content: String,
)

sealed interface MarkdownExportResult {
    data class Success(
        val document: MarkdownDocument,
    ) : MarkdownExportResult

    data object Empty : MarkdownExportResult

    data object NotFound : MarkdownExportResult

    data object Failed : MarkdownExportResult
}

interface MarkdownExportService {
    suspend fun createEntryDocument(date: LocalDate): MarkdownExportResult

    suspend fun createSummaryDocument(
        type: SummaryType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): MarkdownExportResult
}

class DefaultMarkdownExportService
    @Inject
    constructor(
        private val workEntryRepository: WorkEntryRepository,
        private val workSummaryRepository: WorkSummaryRepository,
        private val workSummarySkill: WorkSummarySkill,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : MarkdownExportService {
        override suspend fun createEntryDocument(date: LocalDate): MarkdownExportResult =
            withContext(ioDispatcher) {
                when (val result = workEntryRepository.getEntry(date)) {
                    is DataResult.Failure -> MarkdownExportResult.Failed
                    is DataResult.Success -> result.value?.toEntryDocument() ?: MarkdownExportResult.NotFound
                }
            }

        override suspend fun createSummaryDocument(
            type: SummaryType,
            periodStart: LocalDate,
            periodEnd: LocalDate,
        ): MarkdownExportResult =
            withContext(ioDispatcher) {
                when (val result = workSummaryRepository.getSummary(type, periodStart, periodEnd)) {
                    is DataResult.Failure -> MarkdownExportResult.Failed
                    is DataResult.Success ->
                        result.value?.toSummaryDocument(workSummarySkill)
                            ?: MarkdownExportResult.NotFound
                }
            }
    }

private fun WorkEntry.toEntryDocument(): MarkdownExportResult {
    if (title.isNullOrBlank() && blocks.none(::hasExportableContent)) return MarkdownExportResult.Empty
    return MarkdownExportResult.Success(
        MarkdownDocument(
            suggestedFileName = "工作记录_$entryDate.md",
            content = buildEntryMarkdown(this),
        ),
    )
}

private fun WorkSummary.toSummaryDocument(skill: WorkSummarySkill): MarkdownExportResult {
    val content =
        editedContent
            ?.takeIf(String::isNotBlank)
            ?: originalContent
                ?.let { raw ->
                    skill
                        .parse(
                            raw,
                            WorkSummarySkillRequest(summaryType, periodStart, periodEnd, "zh-CN"),
                        ).getOrNull()
                        ?.let(skill::format)
                }
    if (content.isNullOrBlank()) return MarkdownExportResult.Empty
    return MarkdownExportResult.Success(
        MarkdownDocument(
            suggestedFileName = summaryFileName(summaryType, periodStart, periodEnd),
            content = buildSummaryMarkdown(this, content),
        ),
    )
}

private fun hasExportableContent(block: ContentBlock): Boolean =
    when (block) {
        is ContentBlock.Text -> block.content.isNotBlank()
        is ContentBlock.Image,
        is ContentBlock.Table,
        -> true
    }

private fun buildEntryMarkdown(entry: WorkEntry): String =
    buildString {
        appendLine("# ${entry.entryDate.format(ENTRY_TITLE_FORMATTER)}工作记录")
        entry.title?.takeIf(String::isNotBlank)?.let { title ->
            appendLine()
            appendLine("## 标题")
            appendLine()
            appendLine(title.trim())
        }
        appendLine()
        appendLine("## 工作内容")
        entry.blocks.sortedBy(ContentBlock::order).forEachIndexed { index, block ->
            appendBlock(block, index + 1)
        }
    }.trimEnd() + "\n"

private fun StringBuilder.appendBlock(
    block: ContentBlock,
    number: Int,
) {
    when (block) {
        is ContentBlock.Text -> {
            if (block.content.isBlank()) return
            appendLine()
            appendLine("### 文字记录 $number")
            appendLine()
            appendLine(block.content.trim())
        }

        is ContentBlock.Image -> {
            appendLine()
            appendLine("### 图片记录 $number")
            appendLine()
            val caption =
                block.attachments
                    .firstOrNull()
                    ?.caption
                    ?.trim()
            appendLine(caption?.let { "说明：$it" } ?: "图片记录")
        }

        is ContentBlock.Table -> {
            appendLine()
            appendTable(block.content)
        }
    }
}

private fun StringBuilder.appendTable(content: TableContent) {
    appendLine("### 表格${content.title?.trim()?.takeIf(String::isNotBlank)?.let { "：$it" }.orEmpty()}")
    if (content.columns.isEmpty()) {
        appendLine()
        appendLine("表格内容无法解析")
        return
    }
    appendLine()
    appendLine(content.columns.joinToString(separator = " | ", prefix = "| ", postfix = " |") { it.name.tableCell() })
    appendLine(content.columns.joinToString(separator = " | ", prefix = "| ", postfix = " |") { "---" })
    content.rows.forEach { row ->
        appendLine(
            content.columns.joinToString(separator = " | ", prefix = "| ", postfix = " |") { column ->
                row.cells[column.id].orEmpty().tableCell()
            },
        )
    }
}

private fun String.tableCell(): String =
    replace("\\", "\\\\")
        .replace("|", "\\|")
        .replace("\r\n", "<br>")
        .replace("\n", "<br>")
        .replace("\r", "<br>")

private fun buildSummaryMarkdown(
    summary: WorkSummary,
    content: String,
): String =
    buildString {
        appendLine("# ${if (summary.summaryType == SummaryType.WEEKLY) "工作周报" else "工作月报"}")
        appendLine()
        appendLine("- 覆盖周期：${summary.periodStart} 至 ${summary.periodEnd}")
        summary.generatedAt?.let { appendLine("- 生成时间：$it") }
        appendLine()
        appendLine(content.trim())
    }.trimEnd() + "\n"

private fun summaryFileName(
    type: SummaryType,
    periodStart: LocalDate,
    periodEnd: LocalDate,
): String =
    if (type == SummaryType.WEEKLY) {
        "工作周报_${periodStart}_$periodEnd.md"
    } else {
        "工作月报_${periodStart.year}-${periodStart.monthValue.toString().padStart(2, '0')}.md"
    }

private val ENTRY_TITLE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE)
