package com.worklogai.app.core.security

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedPreferencesSecretStoreTest {
    @Test
    fun `saves encrypted payload reads overwrites and deletes without plaintext storage`() =
        runBlocking {
            val payloadStore = InMemoryPayloadStore()
            val store =
                EncryptedPreferencesSecretStore(
                    payloadStore,
                    ReversingCipher(),
                    kotlinx.coroutines.Dispatchers.Unconfined,
                )

            assertFalse(store.hasApiKey())
            assertTrue(store.saveApiKey("first-value").isSuccess)
            assertEquals("first-value", store.getApiKey().getOrThrow())
            val overwrite = store.saveApiKey("next-value")
            assertTrue(
                "overwrite error=${overwrite.exceptionOrNull()?.cause?.javaClass?.simpleName}",
                overwrite.isSuccess,
            )
            assertEquals("next-value", store.getApiKey().getOrThrow())
            assertFalse(payloadStore.payload!!.cipherText.contains("next-value"))

            assertTrue(store.deleteApiKey().isSuccess)
            assertNull(store.getApiKey().getOrThrow())
            assertFalse(store.hasApiKey())
            Unit
        }

    @Test
    fun `rejects blank keys and maps cipher errors without exposing value`() =
        runBlocking {
            val store =
                EncryptedPreferencesSecretStore(
                    InMemoryPayloadStore(),
                    FailingCipher(),
                    kotlinx.coroutines.Dispatchers.Unconfined,
                )

            val blank = store.saveApiKey("   ")
            val failed = store.saveApiKey("sensitive-value")

            assertTrue(blank.isFailure)
            assertTrue(failed.isFailure)
            assertFalse(failed.exceptionOrNull()!!.message!!.contains("sensitive-value"))
            Unit
        }
}

private class ReversingCipher : SecretCipher {
    override fun encrypt(value: String): EncryptedSecretPayload = EncryptedSecretPayload(value.reversed(), "test-iv")

    override fun decrypt(payload: EncryptedSecretPayload): String = payload.cipherText.reversed()
}

private class FailingCipher : SecretCipher {
    override fun encrypt(value: String): EncryptedSecretPayload = error("failure")

    override fun decrypt(payload: EncryptedSecretPayload): String = error("failure")
}

private class InMemoryPayloadStore : EncryptedSecretPayloadStore {
    var payload: EncryptedSecretPayload? = null

    override suspend fun read(): EncryptedSecretPayload? = payload

    override suspend fun write(payload: EncryptedSecretPayload) {
        this.payload = payload
    }

    override suspend fun clear() {
        payload = null
    }
}
