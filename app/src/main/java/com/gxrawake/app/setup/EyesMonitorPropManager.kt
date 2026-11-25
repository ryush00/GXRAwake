package com.gxrawake.app.setup

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

/**
 * Reads and builds shell commands for Galaxy XR's built-in `EyesMonitor` debug switch
 * (`debug.ix.sysui.eyes_monitor.enabled`).
 *
 * On Samsung Galaxy XR (`SM-I610`), `debug.ix.sysui.eyes_monitor.enabled` is labeled
 * `u:object_r:debug_prop:s0`:
 * - Readable by normal apps (`getprop`)
 * - Writable by ADB `shell` (USB or on-device Wireless Debugging)
 * - In-memory only (resets to default `"true"` after reboot, where GXRAwake's
 *   AccessibilityService auto-click takes over seamlessly).
 */
object EyesMonitorPropManager {
    const val PROP_EYES_MONITOR_ENABLED = "debug.ix.sysui.eyes_monitor.enabled"

    fun buildOneTapShellCommand(packageName: String): String {
        return "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS; " +
            "dumpsys deviceidle whitelist +$packageName; " +
            buildSetEnabledCommand(enabled = false)
    }

    fun buildSetEnabledCommand(enabled: Boolean): String {
        val value = if (enabled) "true" else "false"
        return "setprop $PROP_EYES_MONITOR_ENABLED $value"
    }

    fun isBlockedValue(rawPropValue: String?): Boolean {
        return rawPropValue?.trim()?.lowercase(Locale.ROOT) == "false"
    }

    /**
     * Reads `debug.ix.sysui.eyes_monitor.enabled` via `/system/bin/getprop`.
     * Call from a background thread to avoid main-thread I/O.
     */
    fun readRawValue(): String {
        return try {
            val process = ProcessBuilder("/system/bin/getprop", PROP_EYES_MONITOR_ENABLED)
                .redirectErrorStream(true)
                .start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readLine().orEmpty()
            }
            process.waitFor()
            output.trim()
        } catch (_: Throwable) {
            ""
        }
    }

    fun isBlockedNow(): Boolean = isBlockedValue(readRawValue())
}
