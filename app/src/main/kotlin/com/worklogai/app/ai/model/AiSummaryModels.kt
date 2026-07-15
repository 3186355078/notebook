package com.worklogai.app.ai.model

enum class AiProviderType {
    OPENAI_COMPATIBLE,
}

sealed interface AiResponseFormat {
    data object JsonObject : AiResponseFormat
}

data class AiSummaryRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val model: String,
    val temperature: Double? = null,
    val responseFormat: AiResponseFormat = AiResponseFormat.JsonObject,
)

data class AiTokenUsage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
)

data class AiSummaryResponse(
    val content: String,
    val providerId: String,
    val model: String,
    val requestId: String? = null,
    val usage: AiTokenUsage? = null,
)

sealed interface AiProviderError {
    data object MissingConfiguration : AiProviderError

    data object InvalidBaseUrl : AiProviderError

    data object Unauthorized : AiProviderError

    data object Forbidden : AiProviderError

    data object ModelNotFound : AiProviderError

    data object RateLimited : AiProviderError

    data object Timeout : AiProviderError

    data object NetworkUnavailable : AiProviderError

    data object EmptyResponse : AiProviderError

    data object UnsupportedResponseFormat : AiProviderError

    data class ServerError(
        val statusCode: Int,
    ) : AiProviderError

    data object Unknown : AiProviderError
}

class AiProviderException(
    val providerError: AiProviderError,
) : IllegalStateException(providerError.userMessage())

fun AiProviderError.userMessage(): String =
    when (this) {
        AiProviderError.MissingConfiguration -> "尚未配置大模型服务"
        AiProviderError.InvalidBaseUrl -> "服务地址无效，请检查 HTTPS 地址"
        AiProviderError.Unauthorized,
        AiProviderError.Forbidden,
        -> "API Key 无效或无权访问"
        AiProviderError.ModelNotFound -> "当前模型不可用，请检查模型名称"
        AiProviderError.RateLimited -> "请求过于频繁，请稍后重试"
        AiProviderError.Timeout -> "总结生成超时，请重试"
        AiProviderError.NetworkUnavailable -> "网络暂时不可用"
        AiProviderError.EmptyResponse -> "大模型未返回有效内容"
        AiProviderError.UnsupportedResponseFormat -> "当前服务不支持所需的 JSON 返回格式"
        is AiProviderError.ServerError -> "大模型服务暂时不可用"
        AiProviderError.Unknown -> "总结生成失败，请重试"
    }
