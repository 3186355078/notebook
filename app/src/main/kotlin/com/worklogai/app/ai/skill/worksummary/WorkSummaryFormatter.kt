package com.worklogai.app.ai.skill.worksummary

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

interface WorkSummaryFormatter {
    fun format(result: WorkSummaryResult): String
}

class MarkdownWorkSummaryFormatter
    @Inject
    constructor() : WorkSummaryFormatter {
        override fun format(result: WorkSummaryResult): String =
            buildString {
                appendLine("# ${result.title.ifBlank { "工作总结" }}")
                result.overview.takeIf(String::isNotBlank)?.let {
                    appendLine()
                    appendLine("## 总体概述")
                    appendLine()
                    appendLine(it)
                }
                appendItems("已完成事项", result.completedItems)
                appendItems("进行中事项", result.inProgressItems)
                if (result.problemsAndSolutions.isNotEmpty()) {
                    appendLine()
                    appendLine("## 问题与解决")
                    result.problemsAndSolutions.forEach { item ->
                        appendLine("- 问题：${item.problem}${item.sourceDates.toNaturalDates()}")
                        item.solution?.let { solution -> appendLine("  - 解决：$solution") }
                    }
                }
                appendItems("关键决策", result.keyDecisions)
                if (result.metrics.isNotEmpty()) {
                    appendLine()
                    appendLine("## 数据与指标")
                    result.metrics.forEach { item ->
                        appendLine(
                            "- ${item.name}：${item.value}${item.context?.let { context ->
                                "（$context）"
                            }.orEmpty()}${item.sourceDates.toNaturalDates()}",
                        )
                    }
                }
                appendItems("未完成事项", result.unfinishedItems)
                appendItems("风险", result.risks)
                appendItems("下一步计划", result.nextActions)
                appendItems("亮点", result.highlights)
            }.trim()

        private fun StringBuilder.appendItems(
            title: String,
            items: List<SummaryItem>,
        ) {
            if (items.isEmpty()) return
            appendLine()
            appendLine("## $title")
            items.forEach { item -> appendLine("- ${item.content}${item.sourceDates.toNaturalDates()}") }
        }
    }

private fun List<String>.toNaturalDates(): String =
    takeIf(List<String>::isNotEmpty)
        ?.joinToString(prefix = "（", postfix = "）") { date ->
            runCatching { LocalDate.parse(date).format(DATE_FORMATTER) }.getOrDefault(date)
        }.orEmpty()

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("M月d日", Locale.SIMPLIFIED_CHINESE)
