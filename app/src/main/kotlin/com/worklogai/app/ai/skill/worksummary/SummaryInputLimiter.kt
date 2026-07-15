package com.worklogai.app.ai.skill.worksummary

import kotlinx.serialization.encodeToString
import javax.inject.Inject

interface SummaryInputLimiter {
    fun limit(
        input: WorkSummarySkillInput,
        maxCharacters: Int,
    ): SummaryInputLimitResult
}

class DefaultSummaryInputLimiter
    @Inject
    constructor() : SummaryInputLimiter {
        private val json = SkillJson.instance

        override fun limit(
            input: WorkSummarySkillInput,
            maxCharacters: Int,
        ): SummaryInputLimitResult {
            require(maxCharacters > 0) { "最大输入长度必须大于 0" }
            var excluded = 0
            var truncated = false
            val normalizedEntries =
                input.entries.map { entry ->
                    val title =
                        entry.title.truncateCodePoints(MAX_TITLE_CHARACTERS).also {
                            if (it != entry.title) truncated = true
                        }
                    entry.copy(
                        title = title,
                        contents =
                            entry.contents.map { content ->
                                val limited = content.limitFields()
                                if (limited != content) truncated = true
                                limited
                            },
                    )
                }
            val accepted = mutableListOf<WorkSummaryEntryInput>()
            normalizedEntries.forEach { entry ->
                val acceptedContents = mutableListOf<WorkSummaryContentInput>()
                entry.contents.forEach { content ->
                    val candidate = entry.copy(contents = acceptedContents + content)
                    if (serializedLength(input.copy(entries = accepted + candidate)) <= maxCharacters) {
                        acceptedContents += content
                    } else {
                        excluded++
                        truncated = true
                    }
                }
                val candidate = entry.copy(contents = acceptedContents)
                if (candidate.title != null || candidate.contents.isNotEmpty()) {
                    if (serializedLength(input.copy(entries = accepted + candidate)) <= maxCharacters) {
                        accepted += candidate
                    } else {
                        excluded += candidate.contents.size
                        truncated = true
                    }
                }
            }
            return SummaryInputLimitResult(input.copy(entries = accepted), truncated, excluded)
        }

        private fun serializedLength(input: WorkSummarySkillInput): Int = json.encodeToString(input).length
    }

private fun WorkSummaryContentInput.limitFields(): WorkSummaryContentInput =
    when (type) {
        WorkSummaryContentType.TABLE ->
            copy(
                table =
                    table?.let { source ->
                        source.copy(
                            title = source.title.truncateCodePoints(MAX_TABLE_CELL_CHARACTERS),
                            columns =
                                source.columns.map { value ->
                                    value.truncateCodePoints(MAX_TABLE_CELL_CHARACTERS).orEmpty()
                                },
                            rows =
                                source.rows.map { row ->
                                    row.map { value -> value.truncateCodePoints(MAX_TABLE_CELL_CHARACTERS).orEmpty() }
                                },
                        )
                    },
            )

        else -> copy(text = text.truncateCodePoints(MAX_TEXT_BLOCK_CHARACTERS))
    }

private fun String?.truncateCodePoints(maximum: Int): String? {
    val value = this ?: return null
    return if (value.codePointCount(0, value.length) <= maximum) {
        value
    } else {
        value.substring(0, value.offsetByCodePoints(0, maximum))
    }
}

private const val MAX_TITLE_CHARACTERS = 500
private const val MAX_TEXT_BLOCK_CHARACTERS = 5_000
private const val MAX_TABLE_CELL_CHARACTERS = 1_000
