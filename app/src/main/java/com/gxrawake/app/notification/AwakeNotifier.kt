package com.gxrawake.app.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.gxrawake.app.R
import com.gxrawake.app.config.KeepAwakeConfig
import com.gxrawake.app.log.EventLogStore
import com.gxrawake.app.ui.MainActivity

object AwakeNotifier {
    private const val CHANNEL_ID = "gxrawake_status_visible"
    private const val NOTIFICATION_ID = 1001

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun showDismissedNotification(context: Context, method: String) {
        if (!KeepAwakeConfig.isNotifyOnClickEnabled(context)) {
            return
        }
        if (!hasNotificationPermission(context)) {
            EventLogStore.append(context, context.getString(R.string.notification_skipped_permission))
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, manager)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_awake)
            .setContentTitle(context.getString(R.string.notification_clicked_title))
            .setContentText(context.getString(R.string.notification_clicked_text, method))
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context, manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
