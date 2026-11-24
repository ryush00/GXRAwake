package com.gxrawake.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityStateManagerTest {

    private val target = "com.gxrawake.app/com.gxrawake.app.service.KeepAwakeAccessibilityService"

    @Test
    fun mergeWhenEmpty() {
        assertEquals(target, AccessibilityStateManager.mergeEnabledServices(null, target))
        assertEquals(target, AccessibilityStateManager.mergeEnabledServices("", target))
        assertEquals(target, AccessibilityStateManager.mergeEnabledServices("   ", target))
    }

    @Test
    fun mergePreservesExistingOtherServices() {
        val talkback = "com.google.android.marvin.talkback/.TalkBackService"
        val merged = AccessibilityStateManager.mergeEnabledServices(talkback, target)
        assertEquals("$talkback:$target", merged)
    }

    @Test
    fun mergeDeduplicatesFullAndShortenedForms() {
        val shortened = "com.gxrawake.app/.service.KeepAwakeAccessibilityService"
        val talkback = "com.google.android.marvin.talkback/.TalkBackService"
        val merged = AccessibilityStateManager.mergeEnabledServices("$talkback:$shortened:", target)
        assertEquals("$talkback:$target", merged)
    }

    @Test
    fun normalizeFlattenedComponentExpandsLeadingDot() {
        assertEquals(
            target,
            AccessibilityStateManager.normalizeFlattenedComponent(
                "com.gxrawake.app/.service.KeepAwakeAccessibilityService"
            )
        )
        assertEquals(
            target,
            AccessibilityStateManager.normalizeFlattenedComponent(target)
        )
    }
}
