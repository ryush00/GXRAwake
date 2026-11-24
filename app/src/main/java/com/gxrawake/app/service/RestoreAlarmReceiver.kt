package com.gxrawake.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.gxrawake.app.config.KeepAwakeConfig
import com.gxrawake.app.log.EventLogStore

/**
 * Receives delayed AlarmManager checkpoints after boot or early post-boot unbind.
 * Uses AlarmManager.setAndAllowWhileIdle instead of JobScheduler so Galaxy XR's
 * normal headset thermal state (Thermal status >= 2) never defers boot recovery.
 */
class RestoreAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION_CHECK_RESTORE = "com.gxrawake.app.action.CHECK_RESTORE"
        private const val EXTRA_REASON = "reason"
        private const val REQ_CODE_BASE = 3000
        private const val REQ_CODE_POST_UNBIND = 3010

        private val BOOT_CHECKPOINT_DELAYS_MS = longArrayOf(
            15_000L,
            35_000L,
            65_000L,
            120_000L
        )

        fun scheduleBootCheckpoints(context: Context, trigger: String) {
            BOOT_CHECKPOINT_DELAYS_MS.forEachIndexed { index, delayMs ->
                scheduleSingleAlarm(
                    context = context,
                    requestCode = REQ_CODE_BASE + index,
                    delayMs = delayMs,
                    reason = "$trigger+${delayMs / 1000}s"
                )
            }
        }

        fun schedulePostUnbindCheck(context: Context, delayMs: Long = 5_000L) {
            scheduleSingleAlarm(
                context = context,
                requestCode = REQ_CODE_POST_UNBIND,
                delayMs = delayMs,
                reason = "post-unbind+${delayMs / 1000}s"
            )
        }

        private fun scheduleSingleAlarm(
            context: Context,
            requestCode: Int,
            delayMs: Long,
            reason: String
        ) {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = Intent(context.applicationContext, RestoreAlarmReceiver::class.java).apply {
                action = ACTION_CHECK_RESTORE
                putExtra(EXTRA_REASON, reason)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context.applicationContext,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAtElapsed = SystemClock.elapsedRealtime() + delayMs
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtElapsed,
                pendingIntent
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CHECK_RESTORE) return
        if (!KeepAwakeConfig.isAutoClickEnabled(context) ||
            !KeepAwakeConfig.isAutoRestoreOnBootEnabled(context)
        ) {
            return
        }

        val reason = intent.getStringExtra(EXTRA_REASON) ?: "alarm"
        val result = AccessibilityStateManager.ensureServiceEnabled(context, reason)
        if (KeepAwakeConfig.isVerboseLoggingEnabled(context) &&
            result == AccessibilityStateManager.RestoreResult.ALREADY_ENABLED
        ) {
            EventLogStore.append(context, "Boot checkpoint ($reason): already enabled.")
        }
    }
}
