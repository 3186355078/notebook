package com.worklogai.app.ai.skill.worksummary

import com.worklogai.app.core.model.WorkEntry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject

const val WORK_SUMMARY_SKILL_VERSION = "work-summary-skill-v1"

val WORK_SUMMARY_SYSTEM_PROMPT =
    """
work-summary-skill-v1
你是一个专业、严谨的工作日志总结助手。根据指定时间范围内的工作日志生成准确、简洁、结构清晰的工作总结。
只能使用输入中明确存在的信息；不得虚构工作成果、数据、进度、人员、项目、原因或结论。
合并重复事项，区分已完成、进行中、未完成和风险；信息不足时返回空数组。
保持客观、专业、自然的中文。sourceDates 只能使用输入中真实存在的日期。
不得输出图片本地路径、ID 或内部字段。必须只返回约定 JSON，不得输出解释、Markdown 或代码围栏。
    """.trimIndent()

interface WorkSummarySkill {
    val systemPrompt: String

    fun prepare(
        entries: List<WorkEntry>,
        request: WorkSummarySkillRequest,
    ): Result<PreparedWorkSummaryInput>

    fun parse(
        rawContent: String,
        request: WorkSummarySkillRequest,
    ): Result<WorkSummaryResult>

    fun format(result: WorkSummaryResult): String
}

data class WorkSummarySkillRequest(
    val summaryType: com.worklogai.app.core.model.SummaryType,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val language: String,
    /** Dates that actually supplied content to the model; used to validate model citations. */
    val sourceDates: Set<LocalDate> = emptySet(),
)

data class PreparedWorkSummaryInput(
    val input: WorkSummarySkillInput,
    val userPrompt: String,
    val wasTruncated: Boolean,
    val excludedContentCount: Int,
    val eligibleEntries: List<WorkEntry>,
)

class DefaultWorkSummarySkill
    @Inject
    constructor(
        private val inputBuilder: WorkSummaryInputBuilder,
        private val inputLimiter: SummaryInputLimiter,
        private val outputParser: WorkSummaryOutputParser,
        private val formatter: WorkSummaryFormatter,
    ) : WorkSummarySkill {
        override val systemPrompt: String = WORK_SUMMARY_SYSTEM_PROMPT
        private val json = SkillJson.instance

        override fun prepare(
            entries: List<WorkEntry>,
            request: WorkSummarySkillRequest,
        ): Result<PreparedWorkSummaryInput> =
            runCatching {
                val built = inputBuilder.build(entries, request)
                val limited = inputLimiter.limit(built.input, DEFAULT_MAX_INPUT_CHARACTERS)
                PreparedWorkSummaryInput(
                    input = limited.input,
                    userPrompt = json.encodeToString(limited.input),
                    wasTruncated = limited.wasTruncated,
                    excludedContentCount = limited.excludedContentCount,
                    eligibleEntries = built.eligibleEntries,
                )
            }

        override fun parse(
            rawContent: String,
            request: WorkSummarySkillRequest,
        ): Result<WorkSummaryResult> = outputParser.parse(rawContent, request)

        override fun format(result: WorkSummaryResult): String = formatter.format(result)
    }

private const val DEFAULT_MAX_INPUT_CHARACTERS = 30_000

internal object SkillJson {
    val instance =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }
}
