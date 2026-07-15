package com.worklogai.app.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.worklogai.app.core.common.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

interface SecretStore {
    suspend fun saveApiKey(value: String): Result<Unit>

    suspend fun getApiKey(): Result<String?>

    suspend fun deleteApiKey(): Result<Unit>

    suspend fun hasApiKey(): Boolean
}

data class EncryptedSecretPayload(
    val cipherText: String,
    val initializationVector: String,
)

interface SecretCipher {
    fun encrypt(value: String): EncryptedSecretPayload

    fun decrypt(payload: EncryptedSecretPayload): String
}

class SecretStoreException(
    cause: Throwable? = null,
) : IllegalStateException("无法读取安全密钥存储", cause)

class AndroidKeystoreSecretCipher
    @Inject
    constructor() : SecretCipher {
        override fun encrypt(value: String): EncryptedSecretPayload {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            return EncryptedSecretPayload(
                cipherText = Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8))),
                initializationVector = Base64.getEncoder().encodeToString(cipher.iv),
            )
        }

        override fun decrypt(payload: EncryptedSecretPayload): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.getDecoder().decode(payload.initializationVector)),
            )
            return cipher.doFinal(Base64.getDecoder().decode(payload.cipherText)).toString(Charsets.UTF_8)
        }

        private fun getOrCreateSecretKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            keyGenerator.init(
                KeyGenParameterSpec
                    .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            return keyGenerator.generateKey()
        }
    }

class EncryptedPreferencesSecretStore
    @Inject
    constructor(
        private val payloadStore: EncryptedSecretPayloadStore,
        private val secretCipher: SecretCipher,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : SecretStore {
        override suspend fun saveApiKey(value: String): Result<Unit> =
            guarded {
                val normalized = value.trim()
                require(normalized.isNotEmpty()) { "API Key 不能为空" }
                val encrypted = secretCipher.encrypt(normalized)
                payloadStore.write(encrypted)
            }

        override suspend fun getApiKey(): Result<String?> =
            guarded {
                val payload = payloadStore.read()
                if (payload == null) {
                    null
                } else {
                    secretCipher.decrypt(payload)
                }
            }

        override suspend fun deleteApiKey(): Result<Unit> =
            guarded {
                payloadStore.clear()
            }

        override suspend fun hasApiKey(): Boolean = getApiKey().getOrNull()?.isNotBlank() == true

        private suspend fun <T> guarded(block: suspend () -> T): Result<T> =
            withContext(ioDispatcher) {
                try {
                    Result.success(block())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: GeneralSecurityException) {
                    Result.failure(SecretStoreException(error))
                } catch (error: IOException) {
                    Result.failure(SecretStoreException(error))
                } catch (error: IllegalArgumentException) {
                    Result.failure(SecretStoreException(error))
                } catch (error: IllegalStateException) {
                    Result.failure(SecretStoreException(error))
                }
            }
    }

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "worklog_ai_api_key_v1"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_LENGTH_BITS = 128
