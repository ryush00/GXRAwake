package com.gxrawake.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.gxrawake.app.R
import com.gxrawake.app.config.KeepAwakeConfig
import com.gxrawake.app.log.EventLogStore
import com.gxrawake.app.matcher.DialogMatcher
import com.gxrawake.app.notification.AwakeNotifier

class KeepAwakeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "GXRAwake"
        // Event-driven detection handles dialogs in ~200ms; backup poll only needs to be low-frequency
        // since the Galaxy XR warning dialog stays visible for 30 seconds.
        private const val BACKUP_POLL_INTERVAL_MS = 5_000L
        private const val ATTEMPT_RETRY_MS = 900L
        private const val ACTION_VERIFY_DELAY_MS = 350L
        private const val GESTURE_TAP_DURATION_MS = 120L
        private const val GESTURE_VERIFY_DELAY_MS = 450L
        private const val VERBOSE_LOG_THROTTLE_MS = 3_000L
        private const val POST_BOOT_REBIND_WINDOW_MS = 180_000L

        @Volatile
        var isServiceConnected: Boolean = false
            private set
    }

    private var scannerThread: HandlerThread? = null
    private var scannerHandler: Handler? = null
    private var screenReceiverRegistered = false

    private var clickAttemptInProgress = false
    private var lastAttemptElapsedMs = 0L
    private var lastVerboseNoMatchMs = 0L
    private var lastSystemUiDumpMs = 0L

    @Volatile
    private var screenInteractive = true

    @Volatile
    private var pendingEventSource: String = "event"

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenInteractive = false
                    scannerHandler?.removeCallbacks(pollRunnable)
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenInteractive = true
                    scheduleBackupPoll(immediate = false)
                }
            }
        }
    }

    private val eventScanRunnable = Runnable {
        scanCurrentWindows(pendingEventSource, includeSystemWindowFallback = true)
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!screenInteractive) return
            scanCurrentWindows("poll", includeSystemWindowFallback = true)
            scannerHandler?.postDelayed(this, BACKUP_POLL_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceConnected = true

        serviceInfo?.let { info ->
            info.eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = (info.flags and AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.inv()) or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            info.packageNames = arrayOf(DialogMatcher.SYSTEMUI_PACKAGE)
            info.notificationTimeout = 100L
            serviceInfo = info
        }

        screenInteractive = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        ensureScannerThread()
        registerScreenStateReceiver()
        EventLogStore.append(this, "Accessibility service connected.")
        if (screenInteractive) {
            scheduleBackupPoll(immediate = false)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !screenInteractive) return
        val pkg = event.packageName?.toString()
        if (pkg != null && pkg != DialogMatcher.SYSTEMUI_PACKAGE) {
            return
        }
        val handler = scannerHandler ?: return
        pendingEventSource = "event:${AccessibilityEvent.eventTypeToString(event.eventType)}"
        handler.removeCallbacks(eventScanRunnable)
        handler.post(eventScanRunnable)
    }

    override fun onInterrupt() {
        EventLogStore.append(this, "Accessibility service interrupted.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown("Accessibility service unbound.")
        if (SystemClock.elapsedRealtime() < POST_BOOT_REBIND_WINDOW_MS &&
            KeepAwakeConfig.isAutoClickEnabled(this) &&
            KeepAwakeConfig.isAutoRestoreOnBootEnabled(this) &&
            AccessibilityStateManager.hasWriteSecureSettingsPermission(this)
        ) {
            RestoreAlarmReceiver.schedulePostUnbindCheck(this)
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown("Accessibility service destroyed.")
        super.onDestroy()
    }

    private fun ensureScannerThread() {
        if (scannerThread?.isAlive == true && scannerHandler != null) return
        val thread = HandlerThread("GXRAwakeScanner", Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()
        scannerThread = thread
        scannerHandler = Handler(thread.looper)
    }

    private fun registerScreenStateReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenStateReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    private fun unregisterScreenStateReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { unregisterReceiver(screenStateReceiver) }
        screenReceiverRegistered = false
    }

    private fun scheduleBackupPoll(immediate: Boolean) {
        val handler = scannerHandler ?: return
        handler.removeCallbacks(pollRunnable)
        if (immediate) {
            handler.post(pollRunnable)
        } else {
            handler.postDelayed(pollRunnable, BACKUP_POLL_INTERVAL_MS)
        }
    }

    private fun teardown(reason: String) {
        unregisterScreenStateReceiver()
        scannerHandler?.removeCallbacksAndMessages(null)
        scannerHandler = null
        scannerThread?.quitSafely()
        scannerThread = null
        clickAttemptInProgress = false
        if (isServiceConnected) {
            isServiceConnected = false
            EventLogStore.append(this, reason)
        }
    }

    private fun scanCurrentWindows(source: String, includeSystemWindowFallback: Boolean) {
        if (!screenInteractive) return
        if (!KeepAwakeConfig.isAutoClickEnabled(this)) return
        if (clickAttemptInProgress) return

        val now = SystemClock.elapsedRealtime()
        var lastClick = KeepAwakeConfig.getLastClickElapsedMs(this)
        if (lastClick > now) {
            KeepAwakeConfig.resetLastClickElapsedMs(this)
            lastClick = 0L
        }

        val cooldownMs = KeepAwakeConfig.getCooldownMs(this)
        if (lastClick > 0L && now - lastClick < cooldownMs) {
            return
        }
        if (lastAttemptElapsedMs > 0L && now - lastAttemptElapsedMs < ATTEMPT_RETRY_MS) {
            return
        }

        // Fast path: check rootInActiveWindow first (single IPC; where Galaxy XR SystemUIDialog appears)
        val activeRoot = rootInActiveWindow
        if (isPotentiallySystemUi(activeRoot) &&
            inspectRoot(activeRoot, "$source:active-window", now)
        ) {
            return
        }

        // Secondary path: scan only TYPE_SYSTEM windows so foreground VR/2D apps are never queried via IPC
        if (includeSystemWindowFallback) {
            inspectSystemWindows(now)
        }
    }

    private fun inspectSystemWindows(now: Long): Boolean {
        val windowList = windows ?: return false
        if (windowList.isEmpty()) return false

        for (window in windowList) {
            if (window.type != AccessibilityWindowInfo.TYPE_SYSTEM) continue
            val root = window.root ?: continue
            if (!isPotentiallySystemUi(root)) continue
            val source = "window-type-${window.type}"
            if (inspectRoot(root, source, now)) {
                return true
            }
        }
        return false
    }

    private fun isPotentiallySystemUi(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val pkg = root.packageName?.toString()
        return pkg == null || pkg == DialogMatcher.SYSTEMUI_PACKAGE
    }

    private fun inspectRoot(root: AccessibilityNodeInfo?, source: String, now: Long): Boolean {
        if (root == null) return false

        val match = DialogMatcher.findKeepOnButton(root)
        if (!match.dialogMatched) {
            logVerboseNonMatch(root, source, now)
            return false
        }

        val button = match.button
        if (button == null) {
            EventLogStore.append(
                this,
                "Matched dialog (${match.reason}) but target button was unavailable. Source=$source"
            )
            maybeVerbose("Dialog summary: ${match.summary}")
            return false
        }

        return startVerifiedClickAttempt(button, now, source, match.reason)
    }

    private fun startVerifiedClickAttempt(
        button: AccessibilityNodeInfo,
        now: Long,
        source: String,
        matchReason: String
    ): Boolean {
        val bounds = Rect()
        button.getBoundsInScreen(bounds)
        if (bounds.isEmpty) {
            EventLogStore.append(
                this,
                "Target button had empty bounds. Source=$source Reason=$matchReason"
            )
            return false
        }

        lastAttemptElapsedMs = now
        clickAttemptInProgress = true

        button.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        val actionAccepted = button.isClickable && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)

        EventLogStore.append(
            this,
            "Click attempt ($matchReason). Source=$source Method=action Accepted=$actionAccepted Bounds=${bounds.toShortString()}"
        )

        val verifyDelay = if (actionAccepted) ACTION_VERIFY_DELAY_MS else 0L
        scannerHandler?.postDelayed(
            { verifyActionOrFallback(bounds, now, source) },
            verifyDelay
        )
        return true
    }

    private fun verifyActionOrFallback(bounds: Rect, attemptElapsedMs: Long, source: String) {
        if (!isTargetDialogVisible()) {
            finishSuccessfulClick(attemptElapsedMs, source, "action")
            return
        }

        EventLogStore.append(
            this,
            "ACTION_CLICK did not dismiss dialog; trying dispatchGesture fallback. Source=$source"
        )
        if (!dispatchFallbackTap(bounds, attemptElapsedMs, source)) {
            finishFailedClick("Failed to dispatch fallback tap. Source=$source")
        }
    }

    private fun dispatchFallbackTap(bounds: Rect, attemptElapsedMs: Long, source: String): Boolean {
        if (bounds.isEmpty) return false

        val tapPath = Path().apply {
            moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(tapPath, 0L, GESTURE_TAP_DURATION_MS)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                scannerHandler?.postDelayed(
                    { verifyFallbackResult(attemptElapsedMs, source) },
                    GESTURE_VERIFY_DELAY_MS
                )
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                finishFailedClick("Fallback tap cancelled. Source=$source")
            }
        }

        val dispatched = dispatchGesture(gesture, callback, scannerHandler)
        if (dispatched) {
            EventLogStore.append(
                this,
                "Dispatched fallback tap. Source=$source Bounds=${bounds.toShortString()}"
            )
        }
        return dispatched
    }

    private fun verifyFallbackResult(attemptElapsedMs: Long, source: String) {
        if (!isTargetDialogVisible()) {
            finishSuccessfulClick(attemptElapsedMs, source, "gesture")
            return
        }
        finishFailedClick("Fallback tap completed but warning dialog is still visible. Source=$source")
    }

    private fun finishSuccessfulClick(attemptElapsedMs: Long, source: String, method: String) {
        clickAttemptInProgress = false
        KeepAwakeConfig.recordSuccessfulDismiss(this, attemptElapsedMs, System.currentTimeMillis())
        val baseMsg = getString(R.string.clicked_event)
        EventLogStore.append(this, "$baseMsg Source=$source Method=$method")
        AwakeNotifier.showDismissedNotification(this, method)
    }

    private fun finishFailedClick(message: String) {
        clickAttemptInProgress = false
        EventLogStore.append(this, message)
    }

    private fun isTargetDialogVisible(): Boolean {
        val activeRoot = rootInActiveWindow
        if (isPotentiallySystemUi(activeRoot) &&
            DialogMatcher.findKeepOnButton(activeRoot).dialogMatched
        ) {
            return true
        }
        val windowList = windows
        if (windowList != null) {
            for (window in windowList) {
                if (window.type != AccessibilityWindowInfo.TYPE_SYSTEM) continue
                val root = window.root ?: continue
                if (!isPotentiallySystemUi(root)) continue
                if (DialogMatcher.findKeepOnButton(root).dialogMatched) {
                    return true
                }
            }
        }
        return false
    }

    private fun logVerboseNonMatch(root: AccessibilityNodeInfo, source: String, now: Long) {
        if (!KeepAwakeConfig.isVerboseLoggingEnabled(this)) return

        val systemUiDesc = DialogMatcher.describeSystemUiTree(root)
        if (systemUiDesc != null && now - lastSystemUiDumpMs >= VERBOSE_LOG_THROTTLE_MS) {
            lastSystemUiDumpMs = now
            val msg = "SystemUI snapshot ($source): $systemUiDesc"
            Log.d(TAG, msg)
            EventLogStore.append(this, msg)
            return
        }

        if (now - lastVerboseNoMatchMs >= VERBOSE_LOG_THROTTLE_MS) {
            lastVerboseNoMatchMs = now
            val msg = "No target dialog in $source."
            Log.d(TAG, msg)
            EventLogStore.append(this, msg)
        }
    }

    private fun maybeVerbose(message: String) {
        if (!KeepAwakeConfig.isVerboseLoggingEnabled(this)) return
        Log.d(TAG, message)
        EventLogStore.append(this, message)
    }
}
