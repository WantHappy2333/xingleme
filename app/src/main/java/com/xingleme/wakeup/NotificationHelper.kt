package com.xingleme.wakeup

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {
    const val CHANNEL_GUARDIAN = "guardian_status"
    const val CHANNEL_ALERT = "wakeup_alert"
    const val ID_GUARDIAN = 1001
    const val ID_ALERT = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val guardian = NotificationChannel(
            CHANNEL_GUARDIAN,
            context.getString(R.string.notif_channel_guardian),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_guardian_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val alert = NotificationChannel(
            CHANNEL_ALERT,
            context.getString(R.string.notif_channel_alert),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_alert_desc)
            setShowBadge(true)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(guardian)
        manager.createNotificationChannel(alert)
    }

    fun buildGuardian(
        context: Context,
        intervalText: String,
        thresholdText: String,
        scheduleText: String?,
        waitingForWindow: Boolean,
        waitingStartText: String?,
        screenOffMonitoring: Boolean
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            101,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            context,
            102,
            Intent(context, GuardianService::class.java)
                .setAction(GuardianService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val palette = Palettes.byId(SettingsStore(context).themeId)
        val builder = NotificationCompat.Builder(context, CHANNEL_GUARDIAN)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(ContextCompat.getColor(context, palette.primaryRes))
            .addAction(0, context.getString(R.string.notif_stop), stopIntent)

        if (waitingForWindow) {
            builder.setContentTitle(context.getString(R.string.notif_waiting_title))
            builder.setContentText(
                context.getString(
                    R.string.notif_waiting_text,
                    waitingStartText ?: "",
                    scheduleText ?: ""
                )
            )
        } else if (screenOffMonitoring) {
            builder.setContentTitle(context.getString(R.string.notif_screen_off_title))
            builder.setContentText(
                context.getString(R.string.notif_screen_off_text, thresholdText)
            )
            if (scheduleText != null) {
                builder.setSubText(context.getString(R.string.notif_schedule_sub, scheduleText))
            }
        } else {
            builder.setContentTitle(context.getString(R.string.notif_guardian_title))
            builder.setContentText(
                context.getString(R.string.notif_guardian_text, intervalText, thresholdText)
            )
            if (scheduleText != null) {
                builder.setSubText(context.getString(R.string.notif_schedule_sub, scheduleText))
            }
        }
        return builder.build()
    }

    @SuppressLint("MissingPermission")
    fun showAlert(context: Context, idleText: String) {
        val openIntent = PendingIntent.getActivity(
            context,
            103,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ackIntent = PendingIntent.getService(
            context,
            104,
            Intent(context, GuardianService::class.java)
                .setAction(GuardianService.ACTION_ACK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val palette = Palettes.byId(SettingsStore(context).themeId)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(context.getString(R.string.notif_alert_title))
            .setContentText(context.getString(R.string.notif_alert_text, idleText))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(ContextCompat.getColor(context, palette.primaryRes))
            .addAction(0, context.getString(R.string.notif_ack), ackIntent)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(ID_ALERT, notification)
        }
    }

    fun cancelAlert(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(ID_ALERT) }
    }
}
