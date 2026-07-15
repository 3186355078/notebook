package com.worklogai.app.ai.provider

import com.worklogai.app.ai.model.AiProviderError
import com.worklogai.app.ai.model.AiProviderException
import com.worklogai.app.ai.model.AiSummaryRequest
import com.worklogai.app.core.datastore.AiSettings
import com.worklogai.app.core.datastore.AiSettingsRepository
import com.worklogai.app.core.security.SecretStore
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Proxy
import java.util.concurrent.atomic.AtomicReference

class AiSummaryProviderTest {
    @Test
    fun `mock provider is deterministic and returns structured json`() =
        runBlocking {
            val provider = MockAiSummaryProvider()
            val request =
                request(
                    """
                    {"summaryType":"WEEKLY","entries":[{"date":"2026-07-13","contents":[{"type":"TEXT","text":"完成测试"}]}]}
                    """.trimIndent(),
                )

            val first = provider.generateSummary(request).getOrThrow()
            val second = provider.generateSummary(request).getOrThrow()

            assertEquals("mock", first.providerId)
            assertEquals(first.content, second.content)
            assertTrue(first.content.contains("completedItems"))
            assertTrue(first.content.contains("完成测试"))
        }

    @Test
    fun `mock provider supports controlled failures and cancellation`() =
        runBlocking {
            val failed =
                MockAiSummaryProvider(
                    MockAiSummaryProviderConfig(mode = MockAiSummaryMode.TIMEOUT),
                ).generateSummary(request("{}"))
            assertEquals(AiProviderError.Timeout, (failed.exceptionOrNull() as AiProviderException).providerError)

            val running =
                async {
                    MockAiSummaryProvider(
                        MockAiSummaryProviderConfig(delayMillis = 10_000),
                    ).generateSummary(request("{}"))
                }
            delay(1)
            running.cancelAndJoin()
            assertTrue(running.isCancelled)
        }

    @Test
    fun `openai compatible provider maps request and response without a network call`() =
        runBlocking {
            val recorded = AtomicReference<Request>()
            val provider =
                OpenAiCompatibleSummaryProvider(
                    settingsRepository =
                        FakeAiSettingsRepository(
                            AiSettings(
                                baseUrl = "http://127.0.0.1:8080",
                                model = "test-model",
                                useMockProvider = false,
                            ),
                        ),
                    secretStore = FakeSecretStore("test-secret"),
                    httpClientFactory = InterceptingHttpClientFactory(recorded),
                    allowHttpForLocalhost = true,
                )

            val result = provider.generateSummary(request("{}"))
            val httpRequest = recorded.get()

            assertTrue("provider error=${result.exceptionOrNull()?.message}", result.isSuccess)
            assertEquals("/v1/chat/completions", httpRequest.url.encodedPath)
            assertEquals("Bearer test-secret", httpRequest.header("Authorization"))
            assertEquals("application/json", httpRequest.header("Content-Type"))
            val body = Buffer().also { buffer -> httpRequest.body!!.writeTo(buffer) }.readUtf8()
            assertTrue(body.contains("\"response_format\":{\"type\":\"json_object\"}"))
        }

    @Test
    fun `openai compatible provider rejects insecure urls without exposing api key`() =
        runBlocking {
            val provider =
                OpenAiCompatibleSummaryProvider(
                    settingsRepository =
                        FakeAiSettingsRepository(
                            AiSettings(baseUrl = "http://example.com", model = "x", useMockProvider = false),
                        ),
                    secretStore = FakeSecretStore("test-secret"),
                    httpClientFactory = InterceptingHttpClientFactory(AtomicReference()),
                )

            val result = provider.generateSummary(request("{}"))

            assertFalse(result.isSuccess)
            assertEquals(
                AiProviderError.InvalidBaseUrl,
                (result.exceptionOrNull() as AiProviderException).providerError,
            )
            assertFalse(result.exceptionOrNull()!!.message!!.contains("test-secret"))
        }
}

private fun request(content: String) =
    AiSummaryRequest(systemPrompt = "system", userPrompt = content, model = "test-model")

private class InterceptingHttpClientFactory(
    private val recorded: AtomicReference<Request>,
) : AiHttpClientFactory {
    override fun create(timeoutSeconds: Int): OkHttpClient =
        OkHttpClient
            .Builder()
            .proxy(Proxy.NO_PROXY)
            .addInterceptor { chain ->
                val request = chain.request()
                recorded.set(request)
                Response
                    .Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """
                        {"model":"test-model","choices":[{"message":{"content":"{\"title\":\"周报\"}"}}]}
                        """.trimIndent()
                            .toResponseBody(),
                    ).build()
            }.build()
}

private class FakeAiSettingsRepository(
    private var value: AiSettings,
) : AiSettingsRepository {
    override val settings: Flow<AiSettings> = flowOf(value)

    override suspend fun getSettings(): AiSettings = value

    override suspend fun saveSettings(settings: AiSettings): Result<Unit> =
        Result.success(Unit).also { value = settings }
}

private class FakeSecretStore(
    private var value: String?,
) : SecretStore {
    override suspend fun saveApiKey(value: String): Result<Unit> = Result.success(Unit).also { this.value = value }

    override suspend fun getApiKey(): Result<String?> = Result.success(value)

    override suspend fun deleteApiKey(): Result<Unit> = Result.success(Unit).also { value = null }

    override suspend fun hasApiKey(): Boolean = value != null
}
