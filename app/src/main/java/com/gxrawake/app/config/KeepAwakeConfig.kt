package com.gxrawake.app.config

import android.content.Context
import android.content.SharedPreferences

object KeepAwakeConfig {
    const val PREFS_NAME = "gxr_awake_config"

    private const val KEY_AUTO_CLICK_ENABLED = "auto_click_enabled"
    private const val KEY_AUTO_RESTORE_ON_BOOT = "auto_restore_on_boot"
    private const val KEY_NOTIFY_ON_CLICK = "notify_on_click"
    private const val KEY_VERBOSE_LOGS = "verbose_logs"
    private const val KEY_COOLDOWN_MS = "cooldown_ms"
    private const val KEY_LAST_CLICK_ELAPSED_MS = "last_click_elapsed_ms"
    private const val KEY_LAST_CLICK_WALL_MS = "last_click_wall_ms"
    private const val KEY_DISMISS_COUNT = "dismiss_count"

    const val COOLDOWN_15S_MS = 15_000L
    const val COOLDOWN_60S_MS = 60_000L
    const val COOLDOWN_120S_MS = 120_000L
    const val DEFAULT_COOLDOWN_MS = COOLDOWN_15S_MS

    fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoClickEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CLICK_ENABLED, true)

    fun setAutoClickEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CLICK_ENABLED, enabled).apply()
    }

    fun isAutoRestoreOnBootEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_RESTORE_ON_BOOT, true)

    fun setAutoRestoreOnBootEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_RESTORE_ON_BOOT, enabled).apply()
    }

    fun isNotifyOnClickEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFY_ON_CLICK, true)

    fun setNotifyOnClickEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFY_ON_CLICK, enabled).apply()
    }

    fun isVerboseLoggingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VERBOSE_LOGS, false)

    fun setVerboseLoggingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VERBOSE_LOGS, enabled).apply()
    }

    fun getCooldownMs(context: Context): Long =
        prefs(context).getLong(KEY_COOLDOWN_MS, DEFAULT_COOLDOWN_MS)

    fun setCooldownMs(context: Context, cooldownMs: Long) {
        prefs(context).edit().putLong(KEY_COOLDOWN_MS, cooldownMs.coerceAtLeast(1_000L)).apply()
    }

    fun getLastClickElapsedMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_CLICK_ELAPSED_MS, 0L)

    fun resetLastClickElapsedMs(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_CLICK_ELAPSED_MS, 0L).apply()
    }

    fun getLastClickWallMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_CLICK_WALL_MS, 0L)

    fun getDismissCount(context: Context): Int =
        prefs(context).getInt(KEY_DISMISS_COUNT, 0)

    fun recordSuccessfulDismiss(context: Context, elapsedMs: Long, wallTimeMs: Long) {
        val sp = prefs(context)
        val nextCount = sp.getInt(KEY_DISMISS_COUNT, 0) + 1
        sp.edit()
            .putLong(KEY_LAST_CLICK_ELAPSED_MS, elapsedMs)
            .putLong(KEY_LAST_CLICK_WALL_MS, wallTimeMs)
            .putInt(KEY_DISMISS_COUNT, nextCount)
            .apply()
    }
}
