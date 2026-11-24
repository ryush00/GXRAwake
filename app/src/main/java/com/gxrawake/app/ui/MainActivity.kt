package com.gxrawake.app.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.gxrawake.app.BuildConfig
import com.gxrawake.app.R
import com.gxrawake.app.config.KeepAwakeConfig
import com.gxrawake.app.log.EventLogStore
import com.gxrawake.app.notification.AwakeNotifier
import com.gxrawake.app.service.AccessibilityStateManager
import com.gxrawake.app.service.KeepAwakeAccessibilityService
import com.gxrawake.app.setup.WirelessSetupRunner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_CODE_NOTIFICATIONS = 1001
    }

    private lateinit var mainTabButton: Button
    private lateinit var debugTabButton: Button
    private lateinit var mainTabPage: LinearLayout
    private lateinit var debugTabPage: LinearLayout

    private lateinit var statusBadgeView: TextView
    private lateinit var serviceStatusView: TextView
    private lateinit var bootRestoreStatusView: TextView
    private lateinit var batteryStatusView: TextView
    private lateinit var notificationStatusView: TextView
    private lateinit var statsSummaryView: TextView
    private lateinit var enableNowButton: Button
    private lateinit var notificationPermissionButton: Button

    private lateinit var autoClickSwitch: Switch
    private lateinit var autoRestoreBootSwitch: Switch
    private lateinit var notifySwitch: Switch
    private lateinit var verboseLogSwitch: Switch
    private lateinit var cooldown15Button: Button
    private lateinit var cooldown60Button: Button
    private lateinit var cooldown120Button: Button

    private lateinit var logContentView: TextView

    private lateinit var wirelessBodyLayout: LinearLayout
    private lateinit var wirelessDoneBanner: TextView
    private lateinit var wirelessToggleButton: Button
    private lateinit var wirelessDiscoveryView: TextView
    private lateinit var pairingCodeInput: EditText
    private lateinit var pairingPortInput: EditText
    private lateinit var runWirelessSetupButton: Button
    private lateinit var wirelessProgressView: TextView
    private var wirelessPanelForcedOpen = false
    private var lastAutoFilledPairingPort: String? = null
    private var discoveryActive = false
    private var showingDebugTab = false
    private lateinit var wirelessSetupRunner: WirelessSetupRunner

    private val configPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { refreshUi() }
    }

    private val logPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (showingDebugTab) {
            runOnUiThread { refreshLogsOnly() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wirelessSetupRunner = WirelessSetupRunner(
            context = this,
            listener = object : WirelessSetupRunner.Listener {
                override fun onDiscoveryUpdated(
                    pairingEndpoint: WirelessSetupRunner.Endpoint?,
                    connectEndpoint: WirelessSetupRunner.Endpoint?
                ) {
                    updateDiscoveryUi(pairingEndpoint, connectEndpoint)
                }

                override fun onProgress(message: String) {
                    wirelessProgressView.visibility = View.VISIBLE
                    wirelessProgressView.setTextColor(getColor(R.color.text_secondary))
                    wirelessProgressView.text = message
                }

                override fun onFinished(success: Boolean, message: String) {
                    runWirelessSetupButton.isEnabled = true
                    runWirelessSetupButton.text = getString(R.string.run_wireless_setup)
                    wirelessProgressView.visibility = View.VISIBLE
                    wirelessProgressView.setTextColor(
                        getColor(if (success) R.color.status_ready else R.color.status_error)
                    )
                    wirelessProgressView.text = message
                    if (success) {
                        pairingCodeInput.setText("")
                        wirelessPanelForcedOpen = false
                    }
                    refreshUi()
                }
            }
        )
        setContentView(buildRootView())
        selectTab(showDebug = false)
        refreshUi()
    }

    override fun onStart() {
        super.onStart()
        KeepAwakeConfig.prefs(this).registerOnSharedPreferenceChangeListener(configPrefsListener)
        EventLogStore.prefs(this).registerOnSharedPreferenceChangeListener(logPrefsListener)
        syncWirelessDiscovery()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onStop() {
        stopWirelessDiscovery()
        KeepAwakeConfig.prefs(this).unregisterOnSharedPreferenceChangeListener(configPrefsListener)
        EventLogStore.prefs(this).unregisterOnSharedPreferenceChangeListener(logPrefsListener)
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_NOTIFICATIONS) {
            refreshUi()
        }
    }

    private fun buildRootView(): View {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.app_background))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        container.addView(buildHeader())
        container.addView(buildTabBar())

        mainTabPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = matchWrap()
        }
        mainTabPage.addView(buildStatusCard())
        mainTabPage.addView(buildWirelessSetupCard())
        mainTabPage.addView(buildSetupCard())
        mainTabPage.addView(buildControlsCard())

        debugTabPage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = matchWrap()
            visibility = View.GONE
        }
        debugTabPage.addView(buildDiagnosticsCard())
        debugTabPage.addView(buildLogsCard())

        container.addView(mainTabPage)
        container.addView(debugTabPage)
        scrollView.addView(container)
        return scrollView
    }

    private fun buildHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = matchWrap().apply { bottomMargin = dp(16) }

            addView(TextView(context).apply {
                text = getString(R.string.screen_title)
                setTextColor(getColor(R.color.text_primary))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                typeface = Typeface.DEFAULT_BOLD
            })

            addView(TextView(context).apply {
                text = getString(R.string.screen_subtitle)
                setTextColor(getColor(R.color.text_secondary))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(6), 0, 0)
            })
        }
    }

    private fun buildTabBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBox(getColor(R.color.surface), dp(14), getColor(R.color.surface_border))
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = matchWrap().apply { bottomMargin = dp(16) }
        }

        mainTabButton = createTabButton(getString(R.string.tab_main)) { selectTab(showDebug = false) }
        debugTabButton = createTabButton(getString(R.string.tab_debug)) { selectTab(showDebug = true) }

        bar.addView(mainTabButton, weightParams(endMargin = dp(4)))
        bar.addView(debugTabButton, weightParams())
        return bar
    }

    private fun buildStatusCard(): View {
        val card = createCard()

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = matchWrap().apply { bottomMargin = dp(12) }
        }

        topRow.addView(
            sectionHeader(getString(R.string.section_status)),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        statusBadgeView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(5), dp(12), dp(5))
        }
        topRow.addView(statusBadgeView)
        card.addView(topRow)

        serviceStatusView = bodyText().apply { setPadding(0, 0, 0, dp(6)) }
        bootRestoreStatusView = bodyText().apply { setPadding(0, 0, 0, dp(6)) }
        batteryStatusView = bodyText().apply { setPadding(0, 0, 0, dp(6)) }
        notificationStatusView = bodyText().apply { setPadding(0, 0, 0, dp(10)) }
        statsSummaryView = TextView(this).apply {
            setTextColor(getColor(R.color.accent))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
        }

        card.addView(serviceStatusView)
        card.addView(bootRestoreStatusView)
        card.addView(batteryStatusView)
        card.addView(notificationStatusView)
        card.addView(statsSummaryView)
        return card
    }

    private fun buildWirelessSetupCard(): View {
        val card = createCard()
        card.addView(sectionHeader(getString(R.string.section_wireless_setup)).apply {
            setPadding(0, 0, 0, dp(8))
        })

        wirelessDoneBanner = TextView(this).apply {
            text = getString(R.string.wireless_setup_done_banner)
            setTextColor(getColor(R.color.status_ready))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 0, 0, dp(8))
            visibility = View.GONE
        }
        card.addView(wirelessDoneBanner)

        wirelessToggleButton = createChipButton(getString(R.string.wireless_setup_toggle_show)) {
            wirelessPanelForcedOpen = !wirelessPanelForcedOpen
            refreshUi()
        }.apply {
            visibility = View.GONE
            layoutParams = matchWrap().apply { bottomMargin = dp(8) }
        }
        card.addView(wirelessToggleButton)

        wirelessBodyLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = matchWrap()
        }

        wirelessBodyLayout.addView(TextView(this).apply {
            text = getString(R.string.wireless_setup_guide)
            setTextColor(getColor(R.color.text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, 0, 0, dp(10))
        })

        wirelessDiscoveryView = TextView(this).apply {
            text = getString(R.string.wireless_discovery_waiting)
            setTextColor(getColor(R.color.status_warn))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            background = roundedBox(getColor(R.color.surface_elevated), dp(10))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = matchWrap().apply { bottomMargin = dp(10) }
        }
        wirelessBodyLayout.addView(wirelessDiscoveryView)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = matchWrap().apply { bottomMargin = dp(10) }
        }
        pairingCodeInput = createNumberInput(getString(R.string.wireless_code_hint), maxLen = 6)
        pairingPortInput = createNumberInput(getString(R.string.wireless_port_hint), maxLen = 5)
        inputRow.addView(pairingCodeInput, weightParams(endMargin = dp(8)))
        inputRow.addView(pairingPortInput, weightParams())
        wirelessBodyLayout.addView(inputRow)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = matchWrap()
        }
        val devOptsBtn = createActionButton(
            text = getString(R.string.open_dev_options),
            primary = false
        ) {
            openDeveloperOptions()
        }
        runWirelessSetupButton = createActionButton(
            text = getString(R.string.run_wireless_setup),
            primary = true
        ) {
            if (wirelessSetupRunner.isRunning()) return@createActionButton
            runWirelessSetupButton.isEnabled = false
            runWirelessSetupButton.text = getString(R.string.running_wireless_setup)
            wirelessSetupRunner.runOneTapSetup(
                pairingCodeInput = pairingCodeInput.text?.toString().orEmpty(),
                pairingPortInput = pairingPortInput.text?.toString().orEmpty()
            )
        }
        btnRow.addView(devOptsBtn, weightParams(endMargin = dp(8)))
        btnRow.addView(runWirelessSetupButton, weightParams())
        wirelessBodyLayout.addView(btnRow)

        wirelessProgressView = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(10), 0, 0)
            visibility = View.GONE
        }
        wirelessBodyLayout.addView(wirelessProgressView)

        card.addView(wirelessBodyLayout)
        return card
    }

    private fun buildSetupCard(): View {
        val card = createCard()
        card.addView(sectionHeader(getString(R.string.section_setup)).apply {
            setPadding(0, 0, 0, dp(12))
        })

        enableNowButton = createActionButton(
            text = getString(R.string.enable_service_now),
            primary = true
        ) {
            when (AccessibilityStateManager.ensureServiceEnabled(this, "manual-ui")) {
                AccessibilityStateManager.RestoreResult.ENABLED ->
                    Toast.makeText(this, R.string.service_enabled_now_toast, Toast.LENGTH_SHORT).show()
                AccessibilityStateManager.RestoreResult.ALREADY_ENABLED ->
                    Toast.makeText(this, R.string.service_already_enabled_toast, Toast.LENGTH_SHORT).show()
                AccessibilityStateManager.RestoreResult.MISSING_PERMISSION ->
                    Toast.makeText(this, R.string.write_secure_missing_toast, Toast.LENGTH_LONG).show()
                AccessibilityStateManager.RestoreResult.FAILED ->
                    Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_SHORT).show()
            }
            refreshUi()
        }
        card.addView(enableNowButton, matchWrap().apply { bottomMargin = dp(10) })

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = matchWrap().apply { bottomMargin = dp(10) }
        }
        val accessibilityBtn = createActionButton(
            text = getString(R.string.open_accessibility_settings),
            primary = false
        ) {
            openSettingsIntent(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        val batteryBtn = createActionButton(
            text = getString(R.string.open_battery_settings),
            primary = false
        ) {
            openBatterySettings()
        }
        row1.addView(accessibilityBtn, weightParams(endMargin = dp(8)))
        row1.addView(batteryBtn, weightParams())
        card.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = matchWrap().apply { bottomMargin = dp(12) }
        }
        val appInfoBtn = createActionButton(
            text = getString(R.string.open_app_info),
            primary = false
        ) {
            openAppInfoSettings()
        }
        notificationPermissionButton = createActionButton(
            text = getString(R.string.request_notification_permission),
            primary = false
        ) {
            requestNotificationPermission()
        }
        row2.addView(appInfoBtn, weightParams(endMargin = dp(8)))
        row2.addView(notificationPermissionButton, weightParams())
        card.addView(row2)

        card.addView(TextView(this).apply {
            text = getString(R.string.setup_hint)
            setTextColor(getColor(R.color.text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        })
        return card
    }

    private fun buildControlsCard(): View {
        val card = createCard()
        card.addView(sectionHeader(getString(R.string.section_controls)).apply {
            setPadding(0, 0, 0, dp(8))
        })

        val (autoClickTile, autoSwitch) = createSwitchRow(
            title = getString(R.string.auto_click_title),
            summary = getString(R.string.auto_click_summary),
            checked = KeepAwakeConfig.isAutoClickEnabled(this)
        ) { isChecked ->
            KeepAwakeConfig.setAutoClickEnabled(this, isChecked)
            EventLogStore.append(this, "Auto-click set to $isChecked.")
            refreshUi()
        }
        autoClickSwitch = autoSwitch
        card.addView(autoClickTile)

        val (bootRestoreTile, bootSwitch) = createSwitchRow(
            title = getString(R.string.auto_restore_boot_title),
            summary = getString(R.string.auto_restore_boot_summary),
            checked = KeepAwakeConfig.isAutoRestoreOnBootEnabled(this)
        ) { isChecked ->
            KeepAwakeConfig.setAutoRestoreOnBootEnabled(this, isChecked)
            EventLogStore.append(this, "Boot auto-restore set to $isChecked.")
        }
        autoRestoreBootSwitch = bootSwitch
        card.addView(bootRestoreTile)

        val (notifyTile, nSwitch) = createSwitchRow(
            title = getString(R.string.notify_on_click_title),
            summary = getString(R.string.notify_on_click_summary),
            checked = KeepAwakeConfig.isNotifyOnClickEnabled(this)
        ) { isChecked ->
            KeepAwakeConfig.setNotifyOnClickEnabled(this, isChecked)
        }
        notifySwitch = nSwitch
        card.addView(notifyTile)

        card.addView(TextView(this).apply {
            text = getString(R.string.cooldown_title)
            setTextColor(getColor(R.color.text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(8))
        })

        val cooldownRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = matchWrap()
        }
        cooldown15Button = createChipButton(getString(R.string.cooldown_15s)) {
            KeepAwakeConfig.setCooldownMs(this, KeepAwakeConfig.COOLDOWN_15S_MS)
            refreshUi()
        }
        cooldown60Button = createChipButton(getString(R.string.cooldown_60s)) {
            KeepAwakeConfig.setCooldownMs(this, KeepAwakeConfig.COOLDOWN_60S_MS)
            refreshUi()
        }
        cooldown120Button = createChipButton(getString(R.string.cooldown_120s)) {
            KeepAwakeConfig.setCooldownMs(this, KeepAwakeConfig.COOLDOWN_120S_MS)
            refreshUi()
        }
        cooldownRow.addView(cooldown15Button, weightParams(endMargin = dp(8)))
        cooldownRow.addView(cooldown60Button, weightParams(endMargin = dp(8)))
        cooldownRow.addView(cooldown120Button, weightParams())
        card.addView(cooldownRow)

        return card
    }

    private fun buildDiagnosticsCard(): View {
        val card = createCard()
        card.addView(sectionHeader(getString(R.string.section_diagnostics)).apply {
            setPadding(0, 0, 0, dp(8))
        })

        val (verboseTile, vSwitch) = createSwitchRow(
            title = getString(R.string.verbose_logging_title),
            summary = getString(R.string.verbose_logging_summary),
            checked = KeepAwakeConfig.isVerboseLoggingEnabled(this)
        ) { isChecked ->
            KeepAwakeConfig.setVerboseLoggingEnabled(this, isChecked)
            EventLogStore.append(this, "Verbose diagnostics set to $isChecked.")
        }
        verboseLogSwitch = vSwitch
        card.addView(verboseTile)

        card.addView(TextView(this).apply {
            text = getString(R.string.service_info_title)
            setTextColor(getColor(R.color.text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(10), 0, dp(4))
        })

        val componentName = ComponentName(this, KeepAwakeAccessibilityService::class.java).flattenToString()
        card.addView(TextView(this).apply {
            text = "$componentName\nv${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            setTextColor(getColor(R.color.text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            background = roundedBox(getColor(R.color.surface_elevated), dp(10))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setTextIsSelectable(true)
        })

        return card
    }

    private fun buildLogsCard(): View {
        val card = createCard()

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = matchWrap().apply { bottomMargin = dp(10) }
        }
        headerRow.addView(
            sectionHeader(getString(R.string.section_logs)),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        card.addView(headerRow)

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = matchWrap().apply { bottomMargin = dp(10) }
        }
        actionsRow.addView(
            createChipButton(getString(R.string.refresh)) { refreshUi() },
            weightParams(endMargin = dp(8))
        )
        actionsRow.addView(
            createChipButton(getString(R.string.copy_logs)) { copyLogsToClipboard() },
            weightParams(endMargin = dp(8))
        )
        actionsRow.addView(
            createChipButton(getString(R.string.clear_logs)) {
                EventLogStore.clear(this)
                Toast.makeText(this, R.string.logs_cleared, Toast.LENGTH_SHORT).show()
                refreshUi()
            },
            weightParams()
        )
        card.addView(actionsRow)

        logContentView = TextView(this).apply {
            setTextColor(getColor(R.color.text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.MONOSPACE
            background = roundedBox(getColor(R.color.surface_elevated), dp(10))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            minLines = 8
            setTextIsSelectable(true)
        }
        card.addView(logContentView)
        return card
    }

    private fun refreshUi() {
        val enabledInSettings = isAccessibilityServiceEnabled()
        val connected = KeepAwakeAccessibilityService.isServiceConnected
        val autoClickEnabled = KeepAwakeConfig.isAutoClickEnabled(this)

        when {
            !enabledInSettings -> {
                styleBadge(getString(R.string.status_needs_setup), ready = false)
                serviceStatusView.text = getString(R.string.service_disabled)
                serviceStatusView.setTextColor(getColor(R.color.status_warn))
            }
            !autoClickEnabled -> {
                styleBadge(getString(R.string.status_paused), ready = false)
                serviceStatusView.text = if (connected) {
                    getString(R.string.service_connected)
                } else {
                    getString(R.string.service_enabled_not_connected)
                }
                serviceStatusView.setTextColor(getColor(R.color.text_secondary))
            }
            connected -> {
                styleBadge(getString(R.string.status_ready), ready = true)
                serviceStatusView.text = getString(R.string.service_connected)
                serviceStatusView.setTextColor(getColor(R.color.status_ready))
            }
            else -> {
                styleBadge(getString(R.string.status_ready), ready = true)
                serviceStatusView.text = getString(R.string.service_enabled_not_connected)
                serviceStatusView.setTextColor(getColor(R.color.status_warn))
            }
        }

        val hasSecurePerm = AccessibilityStateManager.hasWriteSecureSettingsPermission(this)
        bootRestoreStatusView.text = if (hasSecurePerm) {
            getString(R.string.boot_restore_ready)
        } else {
            getString(R.string.boot_restore_missing_perm)
        }
        bootRestoreStatusView.setTextColor(
            getColor(if (hasSecurePerm) R.color.status_ready else R.color.status_warn)
        )
        enableNowButton.visibility = if (hasSecurePerm && !enabledInSettings) View.VISIBLE else View.GONE

        val ignoringBattery = isIgnoringBatteryOptimizations()
        val allConfigured = hasSecurePerm && enabledInSettings && ignoringBattery
        wirelessDoneBanner.visibility = if (allConfigured) View.VISIBLE else View.GONE
        wirelessToggleButton.visibility = if (allConfigured) View.VISIBLE else View.GONE
        wirelessToggleButton.text = getString(
            if (wirelessPanelForcedOpen) R.string.wireless_setup_toggle_hide
            else R.string.wireless_setup_toggle_show
        )
        wirelessBodyLayout.visibility =
            if (!allConfigured || wirelessPanelForcedOpen || wirelessSetupRunner.isRunning()) {
                View.VISIBLE
            } else {
                View.GONE
            }
        syncWirelessDiscovery(shouldDiscover = wirelessBodyLayout.visibility == View.VISIBLE)

        batteryStatusView.text = if (ignoringBattery) {
            getString(R.string.battery_unrestricted)
        } else {
            getString(R.string.battery_restricted)
        }
        batteryStatusView.setTextColor(
            getColor(if (ignoringBattery) R.color.text_secondary else R.color.status_warn)
        )

        val hasNotifPerm = AwakeNotifier.hasNotificationPermission(this)
        notificationStatusView.text = if (hasNotifPerm) {
            getString(R.string.notifications_enabled)
        } else {
            getString(R.string.notifications_disabled)
        }
        notificationStatusView.setTextColor(
            getColor(if (hasNotifPerm) R.color.text_secondary else R.color.status_warn)
        )
        notificationPermissionButton.text = if (hasNotifPerm) {
            getString(R.string.notification_permission_granted)
        } else {
            getString(R.string.request_notification_permission)
        }
        notificationPermissionButton.isEnabled = !hasNotifPerm
        notificationPermissionButton.alpha = if (hasNotifPerm) 0.55f else 1.0f

        val count = KeepAwakeConfig.getDismissCount(this)
        val lastWallMs = KeepAwakeConfig.getLastClickWallMs(this)
        val lastFormatted = if (lastWallMs > 0L) {
            SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(lastWallMs))
        } else {
            getString(R.string.stats_never)
        }
        statsSummaryView.text = getString(R.string.stats_summary, count, lastFormatted)

        if (autoClickSwitch.isChecked != autoClickEnabled) {
            autoClickSwitch.isChecked = autoClickEnabled
        }
        val autoRestoreBoot = KeepAwakeConfig.isAutoRestoreOnBootEnabled(this)
        if (autoRestoreBootSwitch.isChecked != autoRestoreBoot) {
            autoRestoreBootSwitch.isChecked = autoRestoreBoot
        }
        val notifyEnabled = KeepAwakeConfig.isNotifyOnClickEnabled(this)
        if (notifySwitch.isChecked != notifyEnabled) {
            notifySwitch.isChecked = notifyEnabled
        }
        val verboseEnabled = KeepAwakeConfig.isVerboseLoggingEnabled(this)
        if (verboseLogSwitch.isChecked != verboseEnabled) {
            verboseLogSwitch.isChecked = verboseEnabled
        }

        val currentCooldown = KeepAwakeConfig.getCooldownMs(this)
        styleChip(cooldown15Button, currentCooldown == KeepAwakeConfig.COOLDOWN_15S_MS)
        styleChip(cooldown60Button, currentCooldown == KeepAwakeConfig.COOLDOWN_60S_MS)
        styleChip(cooldown120Button, currentCooldown == KeepAwakeConfig.COOLDOWN_120S_MS)

        if (showingDebugTab) {
            refreshLogsOnly()
        }
    }

    private fun refreshLogsOnly() {
        val logs = EventLogStore.readNewestFirst(this)
        val nextText = if (logs.isEmpty()) getString(R.string.empty_events) else logs
        if (logContentView.text?.toString() != nextText) {
            logContentView.text = nextText
        }
    }

    private fun selectTab(showDebug: Boolean) {
        showingDebugTab = showDebug
        mainTabPage.visibility = if (showDebug) View.GONE else View.VISIBLE
        debugTabPage.visibility = if (showDebug) View.VISIBLE else View.GONE
        styleTabButton(mainTabButton, selected = !showDebug)
        styleTabButton(debugTabButton, selected = showDebug)
        if (showDebug) {
            refreshLogsOnly()
        }
    }

    private fun syncWirelessDiscovery(shouldDiscover: Boolean = wirelessBodyLayout.visibility == View.VISIBLE) {
        if (shouldDiscover && !discoveryActive) {
            discoveryActive = true
            wirelessSetupRunner.startDiscovery()
        } else if (!shouldDiscover && discoveryActive) {
            stopWirelessDiscovery()
        }
    }

    private fun stopWirelessDiscovery() {
        if (discoveryActive) {
            discoveryActive = false
            wirelessSetupRunner.stopDiscovery()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean =
        AccessibilityStateManager.isServiceEnabledInSettings(this) &&
            AccessibilityStateManager.isAccessibilityMasterEnabled(this)

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openDeveloperOptions() {
        val devIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        if (!tryStartActivity(devIntent)) {
            openSettingsIntent(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        }
    }

    private fun updateDiscoveryUi(
        pairingEndpoint: WirelessSetupRunner.Endpoint?,
        connectEndpoint: WirelessSetupRunner.Endpoint?
    ) {
        when {
            pairingEndpoint != null -> {
                wirelessDiscoveryView.text = getString(
                    R.string.wireless_discovery_pairing_found,
                    pairingEndpoint.host,
                    pairingEndpoint.port
                )
                wirelessDiscoveryView.setTextColor(getColor(R.color.status_ready))
                val currentPort = pairingPortInput.text?.toString().orEmpty()
                if (currentPort.isBlank() || currentPort == lastAutoFilledPairingPort) {
                    val detected = pairingEndpoint.port.toString()
                    pairingPortInput.setText(detected)
                    lastAutoFilledPairingPort = detected
                }
            }
            connectEndpoint != null -> {
                wirelessDiscoveryView.text = getString(
                    R.string.wireless_discovery_connect_found,
                    connectEndpoint.host,
                    connectEndpoint.port
                )
                wirelessDiscoveryView.setTextColor(getColor(R.color.status_ready))
            }
            else -> {
                wirelessDiscoveryView.text = getString(R.string.wireless_discovery_waiting)
                wirelessDiscoveryView.setTextColor(getColor(R.color.status_warn))
            }
        }
    }

    private fun createNumberInput(hintText: String, maxLen: Int): EditText = EditText(this).apply {
        hint = hintText
        setHintTextColor(getColor(R.color.text_secondary))
        setTextColor(getColor(R.color.text_primary))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        inputType = InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(InputFilter.LengthFilter(maxLen))
        background = roundedBox(
            getColor(R.color.surface_elevated),
            dp(10),
            getColor(R.color.surface_border)
        )
        setPadding(dp(12), dp(10), dp(12), dp(10))
        minHeight = dp(46)
    }

    private fun openBatterySettings() {
        val batteryIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        if (!tryStartActivity(batteryIntent)) {
            openAppInfoSettings()
        }
    }

    private fun openAppInfoSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        openSettingsIntent(intent)
    }

    private fun openSettingsIntent(intent: Intent) {
        if (!tryStartActivity(intent)) {
            Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun tryStartActivity(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !AwakeNotifier.hasNotificationPermission(this)
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_NOTIFICATIONS
            )
        }
    }

    private fun copyLogsToClipboard() {
        val logs = EventLogStore.readNewestFirst(this)
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("GXRAwake Logs", logs))
        Toast.makeText(this, R.string.logs_copied, Toast.LENGTH_SHORT).show()
    }

    private fun createCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBox(getColor(R.color.surface), dp(16), getColor(R.color.surface_border))
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = matchWrap().apply { bottomMargin = dp(14) }
    }

    private fun sectionHeader(title: String): TextView = TextView(this).apply {
        text = title
        setTextColor(getColor(R.color.text_primary))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun bodyText(): TextView = TextView(this).apply {
        setTextColor(getColor(R.color.text_secondary))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
    }

    private fun createTabButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.DEFAULT_BOLD
        minHeight = dp(44)
        setOnClickListener { onClick() }
    }

    private fun styleTabButton(button: Button, selected: Boolean) {
        if (selected) {
            button.background = roundedBox(getColor(R.color.accent), dp(10))
            button.setTextColor(getColor(R.color.accent_on))
        } else {
            button.background = roundedBox(android.graphics.Color.TRANSPARENT, dp(10))
            button.setTextColor(getColor(R.color.text_secondary))
        }
    }

    private fun createActionButton(
        text: String,
        primary: Boolean,
        onClick: () -> Unit
    ): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.DEFAULT_BOLD
        minHeight = dp(48)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        if (primary) {
            background = roundedBox(getColor(R.color.accent), dp(12))
            setTextColor(getColor(R.color.accent_on))
        } else {
            background = roundedBox(
                getColor(R.color.surface_elevated),
                dp(12),
                getColor(R.color.surface_border)
            )
            setTextColor(getColor(R.color.text_primary))
        }
        setOnClickListener { onClick() }
    }

    private fun createChipButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        minHeight = dp(40)
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = roundedBox(
            getColor(R.color.surface_elevated),
            dp(10),
            getColor(R.color.surface_border)
        )
        setTextColor(getColor(R.color.text_primary))
        setOnClickListener { onClick() }
    }

    private fun styleChip(button: Button, selected: Boolean) {
        if (selected) {
            button.background = roundedBox(getColor(R.color.accent), dp(10))
            button.setTextColor(getColor(R.color.accent_on))
            button.typeface = Typeface.DEFAULT_BOLD
        } else {
            button.background = roundedBox(
                getColor(R.color.surface_elevated),
                dp(10),
                getColor(R.color.surface_border)
            )
            button.setTextColor(getColor(R.color.text_primary))
            button.typeface = Typeface.DEFAULT
        }
    }

    private fun styleBadge(label: String, ready: Boolean) {
        statusBadgeView.text = label
        statusBadgeView.background = roundedBox(
            getColor(if (ready) R.color.status_ready_bg else R.color.status_warn_bg),
            dp(999)
        )
        statusBadgeView.setTextColor(
            getColor(if (ready) R.color.status_ready else R.color.status_warn)
        )
    }

    private fun createSwitchRow(
        title: String,
        summary: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ): Pair<View, Switch> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBox(getColor(R.color.surface_elevated), dp(12))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = matchWrap().apply { bottomMargin = dp(10) }
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(12)
            }
        }
        textColumn.addView(TextView(this).apply {
            text = title
            setTextColor(getColor(R.color.text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
        })
        textColumn.addView(TextView(this).apply {
            text = summary
            setTextColor(getColor(R.color.text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(2), 0, 0)
        })

        val toggle = Switch(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
        }

        row.addView(textColumn)
        row.addView(toggle)
        row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
        return row to toggle
    }

    private fun roundedBox(fillColor: Int, radiusPx: Int, strokeColor: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx.toFloat()
            setColor(fillColor)
            if (strokeColor != null) {
                setStroke(dp(1), strokeColor)
            }
        }
    }

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

    private fun weightParams(endMargin: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = endMargin
        }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
}
