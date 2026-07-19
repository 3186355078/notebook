package com.worklogai.app.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.core.security.AndroidKeystoreSecretCipher
import com.worklogai.app.core.security.EncryptedPreferencesSecretStore
import com.worklogai.app.core.security.EncryptedSecretPayload
import com.worklogai.app.core.security.EncryptedSecretPayloadStore
import com.worklogai.app.worker.AUTO_SUMMARY_NOTIFICATION_CHANNEL_ID
import com.worklogai.app.worker.AndroidAutoSummaryNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DeviceSecurityIntegrationTest {
    @Test
    fun androidKeystoreRoundTripsWithoutPersistingPlaintext() =
        runBlocking {
            val payloadStore = InMemoryPayloadStore()
            val secretStore =
                EncryptedPreferencesSecretStore(
                    payloadStore = payloadStore,
                    secretCipher = AndroidKeystoreSecretCipher(),
                    ioDispatcher = Dispatchers.IO,
                )
            val testKey = "test-api-key-device-only"

            assertTrue(secretStore.saveApiKey(testKey).isSuccess)
            assertEquals(testKey, secretStore.getApiKey().getOrThrow())
            assertFalse(
                payloadStore.payload
                    ?.cipherText
                    .orEmpty()
                    .contains(testKey),
            )
            assertTrue(payloadStore.payload?.initializationVector?.isNotBlank() == true)

            assertTrue(secretStore.deleteApiKey().isSuccess)
            assertNull(secretStore.getApiKey().getOrThrow())
        }

    @Test
    fun authorizedNotificationIsPrivateAndReusesTheSamePeriodSlot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
        }
        notificationManager.cancelAll()

        try {
            val notifier = AndroidAutoSummaryNotificationManager(context)
            val period = DateRange(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12))

            notifier.notifySuccess(SummaryType.WEEKLY, period)
            notifier.notifySuccess(SummaryType.WEEKLY, period)

            val matching =
                notificationManager.activeNotifications.filter {
                    it.notification.channelId == AUTO_SUMMARY_NOTIFICATION_CHANNEL_ID
                }
            assertEquals(1, matching.size)
            assertEquals(Notification.VISIBILITY_PRIVATE, matching.single().notification.visibility)
            assertNotNull(matching.single().notification.contentIntent)
        } finally {
            notificationManager.cancelAll()
        }
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
}
