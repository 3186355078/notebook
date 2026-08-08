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
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference

class AiSummaryProviderTest {
    @Test
    fun `default client applies timeout to the complete call`() {
        val client = DefaultAiHttpClientFactory().create(17)

        assertEquals(17_000, client.callTimeoutMillis)
        assertEquals(17_000, client.connectTimeoutMillis)
        assertEquals(17_000, client.readTimeoutMillis)
        assertEquals(17_000, client.writeTimeoutMillis)
    }

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
            assertTrue(body.contains("\"stream\":false"))
            assertFalse(body.contains("\"thinking\""))
        }

    @Test
    fun `official deepseek request disables thinking and bounds summary output`() =
        runBlocking {
            val recorded = AtomicReference<Request>()
            val provider =
                OpenAiCompatibleSummaryProvider(
                    settingsRepository =
                        FakeAiSettingsRepository(
                            AiSettings(
                                baseUrl = "https://api.deepseek.com",
                                model = "deepseek-v4-flash",
                                useMockProvider = false,
                            ),
                        ),
                    secretStore = FakeSecretStore("test-secret"),
                    httpClientFactory = InterceptingHttpClientFactory(recorded),
                )

            val result = provider.generateSummary(request("{}"))
            val body = Buffer().also { buffer -> recorded.get().body!!.writeTo(buffer) }.readUtf8()

            assertTrue(result.isSuccess)
            assertTrue(body.contains("\"thinking\":{\"type\":\"disabled\"}"))
            assertTrue(body.contains("\"max_tokens\":8192"))
            assertTrue(body.contains("\"stream\":false"))
        }

    @Test
    fun `response body is consumed away from the caller thread`() =
        runBlocking {
            val callerThread = Thread.currentThread().name
            val bodyReadThread = AtomicReference<String>()
            val result = provider(ThreadRecordingHttpClientFactory(bodyReadThread)).generateSummary(request("{}"))

            assertTrue(result.isSuccess)
            assertTrue(bodyReadThread.get().isNotBlank())
            assertFalse(bodyReadThread.get() == callerThread)
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

    @Test
    fun `openai compatible provider classifies transient and permanent http failures safely`() =
        runBlocking {
            val cases =
                listOf(
                    429 to AiProviderError.RateLimited,
                    500 to AiProviderError.ServerError(500),
                    401 to AiProviderError.Unauthorized,
                    403 to AiProviderError.Forbidden,
                    404 to AiProviderError.ModelNotFound,
                )

            cases.forEach { (statusCode, expected) ->
                val result =
                    provider(
                        InterceptingHttpClientFactory(
                            recorded = AtomicReference(),
                            statusCode = statusCode,
                            responseBody = "sensitive-response-body",
                        ),
                    ).generateSummary(request("non-sensitive-test-input"))
                val error = result.exceptionOrNull() as AiProviderException

                assertEquals(expected, error.providerError)
                assertFalse(error.message.orEmpty().contains("sensitive-response-body"))
                assertFalse(error.message.orEmpty().contains("test-secret"))
            }
        }

    @Test
    fun `openai compatible provider maps socket timeout without exposing request content`() =
        runBlocking {
            val result =
                provider(
                    InterceptingHttpClientFactory(
                        recorded = AtomicReference(),
                        failure = SocketTimeoutException("non-public timeout detail"),
                    ),
                ).generateSummary(request("non-sensitive-test-input"))
            val error = result.exceptionOrNull() as AiProviderException

            assertEquals(AiProviderError.Timeout, error.providerError)
            assertFalse(error.message.orEmpty().contains("non-public timeout detail"))
            assertFalse(error.message.orEmpty().contains("non-sensitive-test-input"))
        }

    @Test
    fun `openai compatible provider maps complete call timeout`() =
        runBlocking {
            val result =
                provider(
                    InterceptingHttpClientFactory(
                        recorded = AtomicReference(),
                        failure = InterruptedIOException("timeout"),
                    ),
                ).generateSummary(request("non-sensitive-test-input"))

            assertEquals(
                AiProviderError.Timeout,
                (result.exceptionOrNull() as AiProviderException).providerError,
            )
        }

    private fun provider(httpClientFactory: AiHttpClientFactory) =
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
            httpClientFactory = httpClientFactory,
            allowHttpForLocalhost = true,
        )
}

private fun request(content: String) =
    AiSummaryRequest(systemPrompt = "system", userPrompt = content, model = "test-model")

private class InterceptingHttpClientFactory(
    private val recorded: AtomicReference<Request>,
    private val statusCode: Int = 200,
    private val responseBody: String =
        """{"model":"test-model","choices":[{"message":{"content":"{\"title\":\"summary\"}"}}]}""",
    private val failure: IOException? = null,
) : AiHttpClientFactory {
    override fun create(timeoutSeconds: Int): OkHttpClient =
        OkHttpClient
            .Builder()
            .proxy(Proxy.NO_PROXY)
            .addInterceptor { chain ->
                val request = chain.request()
                recorded.set(request)
                failure?.let { throw it }
                if (statusCode != 200) {
                    return@addInterceptor Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(statusCode)
                        .message("test failure")
                        .body(responseBody.toResponseBody())
                        .build()
                }
                Response
                    .Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message("test response")
                    .body(
                        """
                        {"model":"test-model","choices":[{"message":{"content":"{\"title\":\"周报\"}"}}]}
                        """.trimIndent()
                            .toResponseBody(),
                    ).build()
            }.build()
}

private class ThreadRecordingHttpClientFactory(
    private val bodyReadThread: AtomicReference<String>,
) : AiHttpClientFactory {
    override fun create(timeoutSeconds: Int): OkHttpClient =
        OkHttpClient
            .Builder()
            .proxy(Proxy.NO_PROXY)
            .addInterceptor { chain ->
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("test response")
                    .body(ThreadRecordingResponseBody(bodyReadThread))
                    .build()
            }.build()
}

private class ThreadRecordingResponseBody(
    private val bodyReadThread: AtomicReference<String>,
) : ResponseBody() {
    private val content =
        """{"model":"test-model","choices":[{"message":{"content":"{\"title\":\"summary\"}"}}]}"""

    override fun contentType(): MediaType? = null

    override fun contentLength(): Long = content.toByteArray().size.toLong()

    override fun source(): BufferedSource {
        val source = Buffer().writeUtf8(content)
        return object : ForwardingSource(source) {
            override fun read(
                sink: Buffer,
                byteCount: Long,
            ): Long {
                bodyReadThread.compareAndSet(null, Thread.currentThread().name)
                return super.read(sink, byteCount)
            }
        }.buffer()
    }
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
