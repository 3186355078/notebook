package com.worklogai.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.worklogai.app.R
import com.worklogai.app.app.MainActivity
import com.worklogai.app.app.navigation.EXTRA_SUMMARY_PERIOD_END
import com.worklogai.app.app.navigation.EXTRA_SUMMARY_PERIOD_START
import com.worklogai.app.app.navigation.EXTRA_SUMMARY_TYPE
import com.worklogai.app.core.history.DateRange
import com.worklogai.app.core.model.SummaryType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface AutoSummaryNotificationManager {
    fun notifySuccess(
        type: SummaryType,
        period: DateRange,
    )

    fun notifyFailure(
        type: SummaryType,
        period: DateRange,
    )
}

class AndroidAutoSummaryNotificationManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AutoSummaryNotificationManager {
        override fun notifySuccess(
            type: SummaryType,
            period: DateRange,
        ) {
            notify(
                type = type,
                period = period,
                title = if (type == SummaryType.WEEKLY) "周报已生成" else "月报已生成",
                text = "${periodLabel(type, period)}的工作总结已完成",
                isFailure = false,
            )
        }

        override fun notifyFailure(
            type: SummaryType,
            period: DateRange,
        ) {
            notify(
                type = type,
                period = period,
                title = if (type == SummaryType.WEEKLY) "周报生成失败" else "月报生成失败",
                text = "请打开应用检查大模型配置或重试",
                isFailure = true,
            )
        }

        private fun notify(
            type: SummaryType,
            period: DateRange,
            title: String,
            text: String,
            isFailure: Boolean,
        ) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            createChannel()
            val notification =
                NotificationCompat
                    .Builder(context, AUTO_SUMMARY_NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setContentIntent(contentIntent(type, period))
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setPriority(
                        if (isFailure) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW,
                    ).build()
            try {
                NotificationManagerCompat.from(context).notify(notificationId(type, period), notification)
            } catch (_: SecurityException) {
                // Permission may be revoked after the preflight check; notifications are optional.
            }
        }

        private fun createChannel() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel =
                NotificationChannel(
                    AUTO_SUMMARY_NOTIFICATION_CHANNEL_ID,
                    "工作总结",
                    NotificationManager.IMPORTANCE_LOW,
                )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun contentIntent(
            type: SummaryType,
            period: DateRange,
        ): PendingIntent {
            val intent =
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_SUMMARY_TYPE, type.name)
                    .putExtra(EXTRA_SUMMARY_PERIOD_START, period.start.toString())
                    .putExtra(EXTRA_SUMMARY_PERIOD_END, period.end.toString())
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                context,
                notificationId(type, period),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

private fun notificationId(
    type: SummaryType,
    period: DateRange,
): Int = "${type.name}:${period.start}:${period.end}".hashCode()

private fun periodLabel(
    type: SummaryType,
    period: DateRange,
): String =
    if (type == SummaryType.MONTHLY) {
        "${period.start.year}年${period.start.monthValue}月"
    } else {
        "${period.start.monthValue}月${period.start.dayOfMonth}日—${period.end.monthValue}月${period.end.dayOfMonth}日"
    }

const val AUTO_SUMMARY_NOTIFICATION_CHANNEL_ID = "auto_summary"
