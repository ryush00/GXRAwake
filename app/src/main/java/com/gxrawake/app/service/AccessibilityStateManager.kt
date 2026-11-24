package com.gxrawake.app.service

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.TextUtils
import com.gxrawake.app.log.EventLogStore

object AccessibilityStateManager {

    enum class RestoreResult {
        ALREADY_ENABLED,
        ENABLED,
        MISSING_PERMISSION,
        FAILED
    }

    fun serviceComponent(context: Context): ComponentName =
        ComponentName(context.applicationContext, KeepAwakeAccessibilityService::class.java)

    fun hasWriteSecureSettingsPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isServiceEnabledInSettings(context: Context): Boolean {
        val target = serviceComponent(context)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return containsComponent(enabled, target)
    }

    fun isAccessibilityMasterEnabled(context: Context): Boolean =
        Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1

    fun ensureServiceEnabled(context: Context, reason: String): RestoreResult {
        if (!hasWriteSecureSettingsPermission(context)) {
            EventLogStore.append(
                context,
                "Auto-restore skipped ($reason): WRITE_SECURE_SETTINGS not granted."
            )
            return RestoreResult.MISSING_PERMISSION
        }

        val target = serviceComponent(context)
        val targetFlat = target.flattenToString()
        val resolver = context.contentResolver
        val existing = Settings.Secure.getString(
            resolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        val alreadyListed = containsComponent(existing, target)
        val masterEnabled = isAccessibilityMasterEnabled(context)

        if (alreadyListed && masterEnabled) {
            return RestoreResult.ALREADY_ENABLED
        }

        val merged = mergeEnabledServices(existing, targetFlat)
        return try {
            Settings.Secure.putString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                merged
            )
            Settings.Secure.putInt(
                resolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                1
            )
            EventLogStore.append(
                context,
                "Restored AccessibilityService ($reason)."
            )
            RestoreResult.ENABLED
        } catch (e: SecurityException) {
            EventLogStore.append(
                context,
                "Failed to restore AccessibilityService ($reason): ${e.message}"
            )
            RestoreResult.FAILED
        } catch (e: Exception) {
            EventLogStore.append(
                context,
                "Error restoring AccessibilityService ($reason): ${e.message}"
            )
            RestoreResult.FAILED
        }
    }

    internal fun containsComponent(enabledServices: String?, target: ComponentName): Boolean {
        if (enabledServices.isNullOrBlank()) return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val raw = splitter.next().trim()
            if (raw.isEmpty()) continue
            val parsed = ComponentName.unflattenFromString(raw)
            if (parsed == target) {
                return true
            }
        }
        return false
    }

    /**
     * Pure string merge preserving all existing enabled accessibility services
     * without introducing duplicate entries or empty colon segments.
     */
    internal fun mergeEnabledServices(existing: String?, targetFlat: String): String {
        val entries = LinkedHashSet<String>()
        if (!existing.isNullOrBlank()) {
            for (part in existing.split(':')) {
                val trimmed = part.trim()
                if (trimmed.isNotEmpty() && !sameFlattenedComponent(trimmed, targetFlat)) {
                    entries.add(trimmed)
                }
            }
        }
        entries.add(targetFlat)
        return entries.joinToString(":")
    }

    private fun sameFlattenedComponent(a: String, b: String): Boolean {
        if (a == b) return true
        return normalizeFlattenedComponent(a) == normalizeFlattenedComponent(b)
    }

    internal fun normalizeFlattenedComponent(flat: String): String {
        val slash = flat.indexOf('/')
        if (slash <= 0 || slash >= flat.length - 1) return flat
        val pkg = flat.substring(0, slash)
        val cls = flat.substring(slash + 1)
        val fullCls = if (cls.startsWith(".")) "$pkg$cls" else cls
        return "$pkg/$fullCls"
    }
}
