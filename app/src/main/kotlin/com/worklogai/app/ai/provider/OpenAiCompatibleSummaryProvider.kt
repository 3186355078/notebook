package com.worklogai.app.ai.provider

import android.content.Context
import android.content.pm.ApplicationInfo
import com.worklogai.app.ai.model.AiProviderError
import com.worklogai.app.ai.model.AiProviderException
import com.worklogai.app.ai.model.AiResponseFormat
import com.worklogai.app.ai.model.AiSummaryRequest
import com.worklogai.app.ai.model.AiSummaryResponse
import com.worklogai.app.ai.model.AiTokenUsage
import com.worklogai.app.core.datastore.AiSettingsRepository
import com.worklogai.app.core.security.SecretStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume

interface AiHttpClientFactory {
    fun create(timeoutSeconds: Int): OkHttpClient
}

class DefaultAiHttpClientFactory
    @Inject
    constructor() : AiHttpClientFactory {
        override fun create(timeoutSeconds: Int): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .build()
    }

class OpenAiCompatibleSummaryProvider internal constructor(
    private val settingsRepository: AiSettingsRepository,
    private val secretStore: SecretStore,
    private val httpClientFactory: AiHttpClientFactory,
    private val allowHttpForLocalhost: Boolean,
) : AiSummaryProvider {
    @Inject
    constructor(
        settingsRepository: AiSettingsRepository,
        secretStore: SecretStore,
        httpClientFactory: AiHttpClientFactory,
        @ApplicationContext context: Context,
    ) : this(settingsRepository, secretStore, httpClientFactory, context.applicationInfo.isDebuggable())

    constructor(
        settingsRepository: AiSettingsRepository,
        secretStore: SecretStore,
        httpClientFactory: AiHttpClientFactory,
    ) : this(settingsRepository, secretStore, httpClientFactory, false)

    override val providerId: String = "openai-compatible"
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun generateSummary(request: AiSummaryRequest): Result<AiSummaryResponse> =
        try {
            val settings = settingsRepository.getSettings()
            val resolved = resolveRequest(settings, request)
            executeResolved(settings.timeoutSeconds, resolved)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            Result.failure(AiProviderException(error.toProviderError()))
        } catch (_: IllegalArgumentException) {
            Result.failure(AiProviderException(AiProviderError.Unknown))
        } catch (_: IllegalStateException) {
            Result.failure(AiProviderException(AiProviderError.Unknown))
        }

    private suspend fun executeResolved(
        timeoutSeconds: Int,
        resolved: Result<ResolvedRequest>,
    ): Result<AiSummaryResponse> =
        resolved.exceptionOrNull()?.let { error -> Result.failure<AiSummaryResponse>(error) }
            ?: resolved.getOrNull()?.let { value -> execute(createCall(timeoutSeconds, value), value.model) }
            ?: Result.failure(AiProviderException(AiProviderError.Unknown))

    private fun createCall(
        timeoutSeconds: Int,
        resolved: ResolvedRequest,
    ): Call {
        val httpRequest =
            Request
                .Builder()
                .url(resolved.endpoint)
                .header("Authorization", "Bearer ${resolved.apiKey}")
                .header("Content-Type", "application/json")
                .post(requestPayload(resolved.request).toRequestBody(JSON_MEDIA_TYPE))
                .build()
        return httpClientFactory.create(timeoutSeconds).newCall(httpRequest)
    }

    private suspend fun resolveRequest(
        settings: com.worklogai.app.core.datastore.AiSettings,
        request: AiSummaryRequest,
    ): Result<ResolvedRequest> {
        val endpoint = normalizeEndpoint(settings.baseUrl, allowHttpForLocalhost)
        val apiKey = secretStore.getApiKey().getOrNull()?.takeIf(String::isNotBlank)
        val model = request.model.ifBlank { settings.model }.trim()
        return if (endpoint == null) {
            Result.failure(AiProviderException(AiProviderError.InvalidBaseUrl))
        } else if (apiKey == null || model.isEmpty()) {
            Result.failure(AiProviderException(AiProviderError.MissingConfiguration))
        } else {
            Result.success(ResolvedRequest(endpoint, apiKey, model, request.copy(model = model)))
        }
    }

    private suspend fun execute(
        call: Call,
        fallbackModel: String,
    ): Result<AiSummaryResponse> =
        awaitResponse(call).useResult { response ->
            val requestId = response.header("x-request-id")
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@useResult Result.failure(
                    AiProviderException(response.toProviderError(body)),
                )
            }
            val root =
                runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                    ?: return@useResult Result.failure(AiProviderException(AiProviderError.EmptyResponse))
            val content =
                root["choices"]
                    ?.jsonArrayOrNull()
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("message")
                    ?.jsonObject
                    ?.get("content")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.trim()
                    .orEmpty()
            if (content.isEmpty()) {
                return@useResult Result.failure(
                    AiProviderException(AiProviderError.EmptyResponse),
                )
            }
            Result.success(
                AiSummaryResponse(
                    content = content,
                    providerId = providerId,
                    model = root["model"]?.jsonPrimitive?.contentOrNull ?: fallbackModel,
                    requestId = requestId,
                    usage = root["usage"]?.jsonObjectOrNull()?.toUsage(),
                ),
            )
        }

    private fun requestPayload(request: AiSummaryRequest): String =
        json.encodeToString(
            kotlinx.serialization.json.JsonObject
                .serializer(),
            buildJsonObject {
                put("model", kotlinx.serialization.json.JsonPrimitive(request.model))
                request.temperature?.let { put("temperature", kotlinx.serialization.json.JsonPrimitive(it)) }
                put(
                    "messages",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", kotlinx.serialization.json.JsonPrimitive("system"))
                                put("content", kotlinx.serialization.json.JsonPrimitive(request.systemPrompt))
                            },
                        )
                        add(
                            buildJsonObject {
                                put("role", kotlinx.serialization.json.JsonPrimitive("user"))
                                put("content", kotlinx.serialization.json.JsonPrimitive(request.userPrompt))
                            },
                        )
                    },
                )
                if (request.responseFormat is AiResponseFormat.JsonObject) {
                    put(
                        "response_format",
                        buildJsonObject { put("type", kotlinx.serialization.json.JsonPrimitive("json_object")) },
                    )
                }
            },
        )
}

private data class ResolvedRequest(
    val endpoint: String,
    val apiKey: String,
    val model: String,
    val request: AiSummaryRequest,
)

private suspend fun awaitResponse(call: Call): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    error: IOException,
                ) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(error))
                }

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    if (continuation.isActive) continuation.resume(response) else response.close()
                }
            },
        )
    }

private inline fun <T> Response.useResult(block: (Response) -> Result<T>): Result<T> = use(block)

private fun normalizeEndpoint(
    rawValue: String,
    allowHttpForLocalhost: Boolean,
): String? =
    runCatching {
        val uri = URI(rawValue.trim())
        val isAllowedScheme =
            uri.scheme == "https" || (uri.scheme == "http" && allowHttpForLocalhost && uri.host.isLoopbackHost())
        require(
            isAllowedScheme && uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null,
        )
        val base = uri.toString().trimEnd('/')
        when {
            base.endsWith("/v1/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
    }.getOrNull()

private fun String?.isLoopbackHost(): Boolean = this == "localhost" || this == "127.0.0.1" || this == "::1"

private fun ApplicationInfo.isDebuggable(): Boolean = flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

private fun IOException.toProviderError(): AiProviderError =
    when (this) {
        is java.net.SocketTimeoutException -> AiProviderError.Timeout
        is UnknownHostException -> AiProviderError.NetworkUnavailable
        else -> AiProviderError.NetworkUnavailable
    }

private fun Response.toProviderError(body: String): AiProviderError =
    when (code) {
        HTTP_UNAUTHORIZED -> AiProviderError.Unauthorized
        HTTP_FORBIDDEN -> AiProviderError.Forbidden
        HTTP_NOT_FOUND -> AiProviderError.ModelNotFound
        HTTP_REQUEST_TIMEOUT -> AiProviderError.Timeout
        HTTP_TOO_MANY_REQUESTS -> AiProviderError.RateLimited
        in HTTP_SERVER_ERROR_START..HTTP_SERVER_ERROR_END -> AiProviderError.ServerError(code)
        HTTP_BAD_REQUEST ->
            if (body.contains(
                    "response_format",
                    ignoreCase = true,
                )
            ) {
                AiProviderError.UnsupportedResponseFormat
            } else {
                AiProviderError.Unknown
            }
        else -> AiProviderError.Unknown
    }

private fun kotlinx.serialization.json.JsonElement.jsonArrayOrNull(): kotlinx.serialization.json.JsonArray? =
    this as? kotlinx.serialization.json.JsonArray

private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): kotlinx.serialization.json.JsonObject? =
    this as? kotlinx.serialization.json.JsonObject

private fun kotlinx.serialization.json.JsonObject.toUsage(): AiTokenUsage =
    AiTokenUsage(
        promptTokens = this["prompt_tokens"]?.jsonPrimitive?.intOrNull,
        completionTokens = this["completion_tokens"]?.jsonPrimitive?.intOrNull,
        totalTokens = this["total_tokens"]?.jsonPrimitive?.intOrNull,
    )

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR_START = 500
private const val HTTP_SERVER_ERROR_END = 599
