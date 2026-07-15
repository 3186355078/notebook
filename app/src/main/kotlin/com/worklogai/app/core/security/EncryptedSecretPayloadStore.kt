package com.worklogai.app.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.worklogai.app.core.datastore.AiSettingsDataStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject

interface EncryptedSecretPayloadStore {
    suspend fun read(): EncryptedSecretPayload?

    suspend fun write(payload: EncryptedSecretPayload)

    suspend fun clear()
}

class DataStoreEncryptedSecretPayloadStore
    @Inject
    constructor(
        @AiSettingsDataStore private val dataStore: DataStore<Preferences>,
    ) : EncryptedSecretPayloadStore {
        override suspend fun read(): EncryptedSecretPayload? {
            val preferences = dataStore.data.first()
            val cipherText = preferences[CIPHER_TEXT]
            val initializationVector = preferences[INITIALIZATION_VECTOR]
            return if (cipherText == null ||
                initializationVector == null
            ) {
                null
            } else {
                EncryptedSecretPayload(cipherText, initializationVector)
            }
        }

        override suspend fun write(payload: EncryptedSecretPayload) {
            dataStore.edit { preferences ->
                preferences[CIPHER_TEXT] = payload.cipherText
                preferences[INITIALIZATION_VECTOR] = payload.initializationVector
            }
        }

        override suspend fun clear() {
            dataStore.edit { preferences ->
                preferences.remove(CIPHER_TEXT)
                preferences.remove(INITIALIZATION_VECTOR)
            }
        }
    }

private val CIPHER_TEXT = stringPreferencesKey("ai_api_key_ciphertext")
private val INITIALIZATION_VECTOR = stringPreferencesKey("ai_api_key_iv")
