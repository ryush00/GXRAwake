package com.gxrawake.app.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EyesMonitorPropManagerTest {

    @Test
    fun buildOneTapShellCommandIncludesGrantBatteryAndEyesMonitorDisable() {
        val cmd = EyesMonitorPropManager.buildOneTapShellCommand("com.gxrawake.app")
        assertEquals(
            "pm grant com.gxrawake.app android.permission.WRITE_SECURE_SETTINGS; " +
                "dumpsys deviceidle whitelist +com.gxrawake.app; " +
                "setprop debug.ix.sysui.eyes_monitor.enabled false",
            cmd
        )
    }

    @Test
    fun buildSetEnabledCommandFormatsTrueAndFalse() {
        assertEquals(
            "setprop debug.ix.sysui.eyes_monitor.enabled false",
            EyesMonitorPropManager.buildSetEnabledCommand(enabled = false)
        )
        assertEquals(
            "setprop debug.ix.sysui.eyes_monitor.enabled true",
            EyesMonitorPropManager.buildSetEnabledCommand(enabled = true)
        )
    }

    @Test
    fun isBlockedValueRecognizesFalseCaseInsensitivelyOnly() {
        assertTrue(EyesMonitorPropManager.isBlockedValue("false"))
        assertTrue(EyesMonitorPropManager.isBlockedValue(" FALSE \n"))
        assertFalse(EyesMonitorPropManager.isBlockedValue("true"))
        assertFalse(EyesMonitorPropManager.isBlockedValue(""))
        assertFalse(EyesMonitorPropManager.isBlockedValue(null))
    }
}
