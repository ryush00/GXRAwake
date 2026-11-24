package com.gxrawake.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gxrawake.app.config.KeepAwakeConfig
import com.gxrawake.app.log.EventLogStore

class BootRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        if (!KeepAwakeConfig.isAutoClickEnabled(context) ||
            !KeepAwakeConfig.isAutoRestoreOnBootEnabled(context)
        ) {
            return
        }

        val trigger = if (action == Intent.ACTION_BOOT_COMPLETED) "boot" else "app-update"
        EventLogStore.append(context, "Received $action; scheduling accessibility restore.")

        AccessibilityStateManager.ensureServiceEnabled(context, "$trigger-immediate")
        RestoreAlarmReceiver.scheduleBootCheckpoints(context, trigger)
    }
}
