package com.worklogai.app.ai.provider

import com.worklogai.app.ai.model.AiProviderError
import com.worklogai.app.ai.model.AiProviderException
import com.worklogai.app.ai.model.AiSummaryRequest
import com.worklogai.app.ai.model.AiSummaryResponse
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

enum class MockAiSummaryMode {
    SUCCESS,
    FAILURE,
    TIMEOUT,
    INVALID_JSON,
    EMPTY_RESPONSE,
}

data class MockAiSummaryProviderConfig(
    val delayMillis: Long = 0,
    val mode: MockAiSummaryMode = MockAiSummaryMode.SUCCESS,
)

class MockAiSummaryProvider(
    private val config: MockAiSummaryProviderConfig,
) : AiSummaryProvider {
    @Inject
    constructor() : this(MockAiSummaryProviderConfig())

    override val providerId: String = "mock"
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun generateSummary(request: AiSummaryRequest): Result<AiSummaryResponse> {
        if (config.delayMillis > 0) delay(config.delayMillis)
        return when (config.mode) {
            MockAiSummaryMode.EMPTY_RESPONSE -> Result.success(response("", request.model))
            MockAiSummaryMode.FAILURE -> Result.failure(AiProviderException(AiProviderError.NetworkUnavailable))
            MockAiSummaryMode.INVALID_JSON -> Result.success(response("{", request.model))
            MockAiSummaryMode.TIMEOUT -> Result.failure(AiProviderException(AiProviderError.Timeout))
            MockAiSummaryMode.SUCCESS -> Result.success(response(buildContent(request.userPrompt), request.model))
        }
    }

    private fun response(
        content: String,
        model: String,
    ): AiSummaryResponse =
        AiSummaryResponse(
            content = content,
            providerId = providerId,
            model = model.ifBlank { "mock-work-summary-v1" },
        )

    private fun buildContent(userPrompt: String): String {
        val input = runCatching { json.parseToJsonElement(userPrompt).jsonObject }.getOrNull()
        val type =
            input
                ?.get("summaryType")
                ?.jsonPrimitive
                ?.contentOrNull
                .orEmpty()
        val entries = input?.get("entries")?.jsonArray ?: JsonArray(emptyList())
        val firstText = entries.firstTextContent()
        val title = if (type == "MONTHLY") "工作月报" else "工作周报"
        val result =
            buildJsonObject {
                put("title", JsonPrimitive(title))
                put("overview", JsonPrimitive("根据提供的 ${entries.size} 条工作记录整理。"))
                put("completedItems", firstText?.let(::singleItemArray) ?: JsonArray(emptyList()))
                put("inProgressItems", JsonArray(emptyList()))
                put("problemsAndSolutions", JsonArray(emptyList()))
                put("keyDecisions", JsonArray(emptyList()))
                put("metrics", JsonArray(emptyList()))
                put("unfinishedItems", JsonArray(emptyList()))
                put("nextActions", JsonArray(emptyList()))
                put("risks", JsonArray(emptyList()))
                put("highlights", JsonArray(emptyList()))
            }
        return json.encodeToString(JsonObject.serializer(), result)
    }
}

private fun JsonArray.firstTextContent(): Pair<String, String>? =
    asSequence()
        .mapNotNull { entry ->
            val objectValue = entry.jsonObject
            val date = objectValue["date"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            objectValue["contents"]?.jsonArray?.firstNotNullOfOrNull { content ->
                val contentObject = content.jsonObject
                val text = contentObject["text"]?.jsonPrimitive?.contentOrNull?.trim()
                text?.takeIf(String::isNotEmpty)?.let { date to it }
            }
        }.firstOrNull()

private fun singleItemArray(item: Pair<String, String>): JsonArray =
    buildJsonArray {
        add(
            buildJsonObject {
                put("content", JsonPrimitive(item.second))
                put("sourceDates", buildJsonArray { add(JsonPrimitive(item.first)) })
            },
        )
    }
