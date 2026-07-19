package com.worklogai.app.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.worklogai.app.R
import com.worklogai.app.app.navigation.EXTRA_SUMMARY_PERIOD_END
import com.worklogai.app.app.navigation.EXTRA_SUMMARY_PERIOD_START
import com.worklogai.app.app.navigation.EXTRA_SUMMARY_TYPE
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.SummaryType
import com.worklogai.app.worker.AUTO_SUMMARY_NOTIFICATION_CHANNEL_ID
import dagger.hilt.android.EntryPointAccessors
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class DeviceNotificationFixtureTest {
    @Test
    fun postNotificationFixtureForSystemShadeClick() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancelAll()
        val fixture =
            InstrumentationRegistry
                .getArguments()
                .getString(FIXTURE_ARGUMENT)
                .orEmpty()
                .ifBlank { WEEKLY_SUCCESS }
        val dependencies = EntryPointAccessors.fromApplication(context, Stage9TestEntryPoint::class.java)

        when (fixture) {
            WEEKLY_SUCCESS -> dependencies.notificationManager().notifySuccess(SummaryType.WEEKLY, WEEKLY_PERIOD)
            MONTHLY_SUCCESS -> dependencies.notificationManager().notifySuccess(SummaryType.MONTHLY, MONTHLY_PERIOD)
            WEEKLY_FAILURE -> dependencies.notificationManager().notifyFailure(SummaryType.WEEKLY, WEEKLY_PERIOD)
            MISSING -> postRawFixture(notificationManager, fixture, "Stage 9 缺少参数", null, null, null)
            MALFORMED ->
                postRawFixture(
                    notificationManager,
                    fixture,
                    "Stage 9 非法参数",
                    SummaryType.WEEKLY.name,
                    "not-a-date",
                    WEEKLY_PERIOD.end.toString(),
                )
            FUTURE ->
                postRawFixture(
                    notificationManager,
                    fixture,
                    "Stage 9 未来周期",
                    SummaryType.WEEKLY.name,
                    "2099-01-05",
                    "2099-01-11",
                )
            else -> error("Unknown notification fixture")
        }

        assertTrue(
            notificationManager.activeNotifications.any {
                it.notification.channelId == AUTO_SUMMARY_NOTIFICATION_CHANNEL_ID
            },
        )
    }

    private fun postRawFixture(
        notificationManager: NotificationManager,
        fixture: String,
        title: String,
        type: String?,
        start: String?,
        end: String?,
    ) {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                AUTO_SUMMARY_NOTIFICATION_CHANNEL_ID,
                "工作总结",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java)
        type?.let { intent.putExtra(EXTRA_SUMMARY_TYPE, it) }
        start?.let { intent.putExtra(EXTRA_SUMMARY_PERIOD_START, it) }
        end?.let { intent.putExtra(EXTRA_SUMMARY_PERIOD_END, it) }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                fixture.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, AUTO_SUMMARY_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText("打开工作总结")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
        NotificationManagerCompat.from(context).notify(fixture.hashCode(), notification)
    }

    companion object {
        const val FIXTURE_ARGUMENT = "fixture"
        const val WEEKLY_SUCCESS = "weekly-success"
        const val MONTHLY_SUCCESS = "monthly-success"
        const val WEEKLY_FAILURE = "weekly-failure"
        const val MISSING = "missing"
        const val MALFORMED = "malformed"
        const val FUTURE = "future"
        val WEEKLY_PERIOD = DateRange(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 12))
        val MONTHLY_PERIOD = DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))
    }
}
