package com.worklogai.app.ai.skill.worksummary

import com.worklogai.app.core.model.ContentBlock
import com.worklogai.app.core.model.TableContent
import com.worklogai.app.core.model.WorkEntry
import java.time.LocalDate
import javax.inject.Inject

interface WorkSummaryInputBuilder {
    fun build(
        entries: List<WorkEntry>,
        request: WorkSummarySkillRequest,
    ): WorkSummaryInputBuildResult
}

data class WorkSummaryInputBuildResult(
    val input: WorkSummarySkillInput,
    val eligibleEntries: List<WorkEntry>,
)

class DefaultWorkSummaryInputBuilder
    @Inject
    constructor() : WorkSummaryInputBuilder {
        override fun build(
            entries: List<WorkEntry>,
            request: WorkSummarySkillRequest,
        ): WorkSummaryInputBuildResult {
            val eligible =
                entries
                    .asSequence()
                    .filter { entry -> !entry.isDeleted && entry.allowAiProcessing }
                    .filter { entry -> entry.entryDate in request.periodStart..request.periodEnd }
                    .sortedBy(WorkEntry::entryDate)
                    .toList()
            val mapped = eligible.mapNotNull(::toInputEntry)
            return WorkSummaryInputBuildResult(
                input =
                    WorkSummarySkillInput(
                        summaryType = request.summaryType,
                        periodStart = request.periodStart.toString(),
                        periodEnd = request.periodEnd.toString(),
                        language = request.language.ifBlank { "zh-CN" },
                        entries = mapped,
                    ),
                eligibleEntries =
                    eligible.filter {
                        it.entryDate in
                            mapped.map(WorkSummaryEntryInput::date).map(LocalDate::parse).toSet()
                    },
            )
        }

        private fun toInputEntry(entry: WorkEntry): WorkSummaryEntryInput? {
            val contents =
                entry.blocks
                    .sortedWith(compareBy<ContentBlock>(ContentBlock::order).thenBy(ContentBlock::id))
                    .mapNotNull(::toInputContent)
                    .distinctBy(::contentFingerprint)
            val title = entry.title.normalizeText().takeIf(String::isNotEmpty)
            return if (title == null &&
                contents.isEmpty()
            ) {
                null
            } else {
                WorkSummaryEntryInput(entry.entryDate.toString(), title, contents)
            }
        }

        private fun toInputContent(block: ContentBlock): WorkSummaryContentInput? =
            when (block) {
                is ContentBlock.Text ->
                    block.content
                        .normalizeText()
                        .takeIf(String::isNotEmpty)
                        ?.let { WorkSummaryContentInput(WorkSummaryContentType.TEXT, text = it) }

                is ContentBlock.Image ->
                    block.attachments
                        .asSequence()
                        .mapNotNull { attachment -> attachment.caption.normalizeText().takeIf(String::isNotEmpty) }
                        .firstOrNull()
                        ?.let { WorkSummaryContentInput(WorkSummaryContentType.IMAGE, text = it) }

                is ContentBlock.Table ->
                    block.content.toInputTable()?.let {
                        WorkSummaryContentInput(WorkSummaryContentType.TABLE, table = it)
                    }
            }

        private fun TableContent.toInputTable(): WorkSummaryTableInput? {
            val columns = columns.map { column -> column.name.normalizeText() }
            val rows =
                rows
                    .map { row -> columns.indices.map { index -> row.cells[this.columns[index].id].normalizeText() } }
                    .filter { row -> row.any(String::isNotEmpty) }
            val title = title.normalizeText().takeIf(String::isNotEmpty)
            return if (title == null &&
                columns.all(String::isEmpty) &&
                rows.isEmpty()
            ) {
                null
            } else {
                WorkSummaryTableInput(title, columns, rows)
            }
        }
    }

internal fun String?.normalizeText(): String = this.orEmpty().trim().replace(Regex("\\s+"), " ")

private fun contentFingerprint(content: WorkSummaryContentInput): String =
    when (content.type) {
        WorkSummaryContentType.TABLE -> content.table.toString()
        else -> "${content.type}:${content.text}"
    }
