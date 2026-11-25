# GXRAwake

[![한국어](https://img.shields.io/badge/Language-%ED%95%9C%EA%B5%AD%EC%96%B4-1A2C33?style=for-the-badge)](README.md)
[![日本語](https://img.shields.io/badge/Language-%E6%97%A5%E6%9C%AC%E8%AA%9E-1A2C33?style=for-the-badge)](README.ja.md)
[![English](https://img.shields.io/badge/Language-English-5CE1D6?style=for-the-badge)](README.en.md)

**An always-on Android Accessibility app for Samsung Galaxy XR VR Sleep that automatically confirms the 30-second standby countdown warning.**

When wearing a Galaxy XR headset in a dark room with your eyes closed for **VR Sleep**, the system detects inactivity and pops up a **30-second auto-standby warning dialog**. `GXRAwake` monitors for this dialog via Android's `AccessibilityService` and automatically presses **Keep on** before the headset shuts down.

---

## Why Is This Needed?

Even with Developer Options **Stay awake** enabled and long screen-off timeouts configured on Galaxy XR (`SM-I610`), `XrSystemUI`'s **`EyesMonitor`** independently monitors eye detection while the headset is worn in low light:

1. After **60 seconds** with no eyes detected, a system warning dialog (`com.android.systemui`) appears:
   - Title: **Headset turning off in 30 seconds**
   - Message: **Select Keep on to prevent your headset from turning off.**
   - Button: **Keep on**
2. If the button is not pressed within **30 seconds** (at 90 seconds total), `EyesMonitor` calls `PowerManager.goToSleep()` and puts the headset to sleep.

`GXRAwake` dismisses this dialog automatically as soon as it appears so VRChat and other VR Sleep sessions stay connected uninterrupted.

---

## Key Features

- **Two-Layer Hybrid Sleep Prevention**
  - **Layer 1 — Root-Cause Suppression**: One-tap Wireless Debugging setup also runs `setprop debug.ix.sysui.eyes_monitor.enabled false`, preventing the 60s warning dialog and 90s auto-sleep timer from triggering at all until reboot (zero impact on eye tracking or sensors)
  - **Layer 2 — Reboot-Persistent Safety Net**: Even after a reboot clears `debug.*`, `GXRAwake` automatically restores its AccessibilityService via `WRITE_SECURE_SETTINGS` + `AlarmManager.setAndAllowWhileIdle` and dismisses the dialog within ~0.2s
- **Low-Overhead Event-Driven Detection**
  - Scoped to `com.android.systemui` at the OS level and runs all scanning on a dedicated background `HandlerThread` (`GXRAwakeScanner`) to eliminate UI jank
  - Drops `flagIncludeNotImportantViews` to minimize system-wide accessibility tree overhead, and runs backup polling only while the screen is interactive at a 5-second interval on `rootInActiveWindow` and `TYPE_SYSTEM` windows—pausing automatically on screen-off and never querying foreground VR/2D apps
- **High-Precision Dialog Matching**
  - **Tier 1**: Matches `com.android.systemui` standard `AlertDialog` structure (`alertTitle`, `message`, `button1`) using firmware-verified signatures from `XrSystemUIRRO.apk` (**all 97 real Galaxy XR locales**), dynamically activating **only the current system language (+ English fallback)**
  - **Tier 2 Fallback**: Safely handles custom SystemUI layouts by pairing only the active system language's official dialog context and button text
  - **False-Positive Protection**: Excludes notification contexts and unrelated system alerts such as battery, temperature, storage, and updates
- **Two-Stage Verified Click Strategy**
  1. Sends `ACTION_ACCESSIBILITY_FOCUS` → `ACTION_CLICK` and verifies after 350ms that the dialog is gone
  2. Falls back to a `dispatchGesture` 120ms tap at the button center if still visible, then re-verifies after 450ms
  3. Includes a 900ms retry guard and selectable `15s` / `60s` / `120s` cooldown
- **Automatic Reboot Recovery**
  - With a one-time `WRITE_SECURE_SETTINGS` grant, `GXRAwake` automatically re-enables itself after every reboot or app update without a PC or ADB
  - Uses `AlarmManager.setAndAllowWhileIdle` instead of `JobScheduler` so Galaxy XR's worn thermal state never defers boot recovery
  - Combines post-unbind +5s recovery with `BOOT_COMPLETED` checkpoints at `+15s / +35s / +65s / +120s` while preserving existing accessibility services like TalkBack
- **Built-In One-Tap Wireless Setup & Diagnostics UI**
  - Live service status, reboot auto-restore permission, battery exemption, notification permission, and dismiss counters
  - Toggles for auto-click, reboot auto-restore, confirmation notifications, cooldown selector, and live logs in English, Korean, and Japanese

---

## Installation & One-Time Setup

Because of Galaxy XR's `SecSettings` firmware restriction, a **one-time** permission grant and initial enablement is required. Once configured, `GXRAwake` persists automatically **across reboots without a PC or ADB**.

### Option 1. Standalone On-Device One-Tap Setup

`GXRAwake` embeds a local Wireless Debugging pairing and setup client with mDNS auto-discovery, so you can complete setup directly inside Galaxy XR with no PC or external terminal apps.

1. Download and install the `GXRAwake` APK directly in Galaxy XR, then open the app.
2. Enable **Developer Options** once: Settings → About device → Software information → tap **Build number 7 times**.
3. Inside `GXRAwake`, tap **[Developer options]** in the **[One-tap Setup]** card so Settings opens beside the app in spatial multi-window.
4. Enable **Wireless debugging**, open it, and tap **[Pair device with pairing code]**. The pairing port is discovered and filled automatically via mDNS.
5. Enter only the **6-digit pairing code** shown in Settings and tap **[Run one-tap setup]** to complete all four steps automatically:
   - Grant `WRITE_SECURE_SETTINGS`
   - Exempt `com.gxrawake.app` from battery optimization
   - Suppress the EyesMonitor warning dialog directly (`setprop debug.ix.sysui.eyes_monitor.enabled false`)
   - Enable and bind `KeepAwakeAccessibilityService`

### Option 2. PC / Mac USB Setup

Alternatively, connect Galaxy XR via USB and run once:

```bash
# 1. Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 2. Grant WRITE_SECURE_SETTINGS for automatic reboot recovery (persists across reboots)
adb shell pm grant com.gxrawake.app android.permission.WRITE_SECURE_SETTINGS

# 3. Exempt from battery optimization for overnight VR Sleep (persists across reboots)
adb shell dumpsys deviceidle whitelist +com.gxrawake.app

# 4. Suppress the 60s warning dialog / 90s auto-sleep directly (active until reboot)
adb shell setprop debug.ix.sysui.eyes_monitor.enabled false

# 5. Enable AccessibilityService (reboot-persistent safety net)
adb shell settings put secure enabled_accessibility_services com.gxrawake.app/com.gxrawake.app.service.KeepAwakeAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

### Verification

After setup, simply **reboot and unlock** your headset: within ~5 seconds `GXRAwake` automatically restores and binds itself, showing **`Ready`** in the app. (Tap **[Allow notifications]** once in the app if you want confirmation notifications.)

---

## Real-Hardware Verification

Tested on **Samsung Galaxy XR `SM-I610`**, Android 14 / API 34, build `UML2.260301.001.I610UEU2AZF3`:

1. **Dark-Room VR Sleep 30s Warning Auto-Dismiss**
   - When `EyesMonitor` reached 60s and invoked `showSleepWarningDialog`, `GXRAwake` matched and confirmed the dialog via `ACTION_CLICK` in **`199ms` (`0.199s`)**
   - Verified `Dismiss SleepWarningDialog` → `stopEyesMonitor` → `dismissed: true` in `EyesMonitor` logcat plus confirmation notification delivery
2. **PC-Free Real Reboot Recovery**
   - After a full reboot and unlock, `SecSettings` (`SimCompleteBootReceiver`) wiped `enabled_accessibility_services`; `RestoreAlarmReceiver(post-unbind+5s)` restored and bound `GXRAwake` automatically **5 seconds later**
   - Confirmed `Bound services` active (`capabilities=33`, `retrieveInteractiveWindows=true`, `MotionEventInjector` enabled) via `dumpsys accessibility`
3. **Standalone On-Device One-Tap Wireless Setup**
   - Tested from a clean freshly-wiped install (`WRITE_SECURE_SETTINGS=false`, not battery-exempt, accessibility disabled)
   - mDNS auto-detected the pairing endpoint (`192.168.x.x:<port>`); after entering the 6-digit code, pairing, wireless ADB connection, permission grant, battery exemption (`Added: com.gxrawake.app`), and accessibility binding all completed in **~1 second**

---

## Galaxy XR Firmware Technical Notes

Findings inspected directly from `SM-I610` system packages (`SecSettings.apk`, `XrSystemUI.apk`, `services.jar`):

- **Why third-party Accessibility apps do not appear in Galaxy XR Settings → Accessibility → Installed apps**
  - `SecAccessibilityUtils.isValidPreInstalledAccessibilityComponent(ComponentName)` in `SecSettings.apk` hardcodes an allowlist of only 3 components:
    - `com.google.android.marvin.talkback/.TalkBackService`
    - `com.samsung.android.accessibility.talkback/com.samsung.android.marvin.talkback.TalkBackService`
    - `com.salab.act/com.salab.act.ACTUiAutomationServiceClient` (Samsung's internal QA automation tool ACT)
  - `InstalledAppsManager.checkValidApp()` hides every other app from the Settings UI, and on every boot `SimCompleteBootReceiver` → `cleanupInvalidAccessibilityShortcuts()` turns off any enabled service not in those 3 entries.
- **`GXRAwake`'s Approach**
  - Instead of spoofing Samsung's internal `com.salab.act` package, `GXRAwake` uses its own clean package name (**`com.gxrawake.app`**).
  - Because `AccessibilityManagerService` itself permits standard third-party packages, `GXRAwake` uses Android's official `development` permission (`WRITE_SECURE_SETTINGS`, explicitly authorized once by the device owner) together with `BOOT_COMPLETED` + `AlarmManager.setAndAllowWhileIdle` for reliable reboot persistence.
- **30-Second Warning Dialog Implementation (`XrSystemUI.apk`)**
  - `com.android.systemui.xr.poorconditions.EyesMonitor`: `EYES_MONITOR_TIME_SHOW_DIALOG = 60`s, `EYES_MONITOR_TIMEOUT_SEC = 90`s
  - Standard `SystemUIDialog` (`AlertDialog` subclass) using `plurals/sec_inactivity_auto_sleep_dialog_title`, `string/sec_inactivity_auto_sleep_dialog_message`, and `string/sec_inactivity_auto_sleep_dialog_button`

---

## Project Structure

```text
app/src/
├── main/
│   ├── AndroidManifest.xml
│   ├── java/com/gxrawake/app/
│   │   ├── config/KeepAwakeConfig.kt
│   │   ├── log/EventLogStore.kt
│   │   ├── matcher/
│   │   │   ├── AccessibilityNodeAdapter.kt
│   │   │   ├── DialogMatcher.kt
│   │   │   ├── InactivityDialogLocales.kt
│   │   │   └── UiNode.kt
│   │   ├── notification/AwakeNotifier.kt
│   │   ├── service/
│   │   │   ├── AccessibilityStateManager.kt
│   │   │   ├── BootRestoreReceiver.kt
│   │   │   ├── KeepAwakeAccessibilityService.kt
│   │   │   └── RestoreAlarmReceiver.kt
│   │   ├── setup/
│   │   │   ├── EyesMonitorPropManager.kt
│   │   │   ├── LocalAdbConnectionManager.kt
│   │   │   ├── SelfSignedCertGenerator.kt
│   │   │   └── WirelessSetupRunner.kt
│   │   └── ui/MainActivity.kt
│   └── res/
│       ├── drawable/
│       ├── mipmap-anydpi-v26/
│       ├── values/, values-ko/, values-ja/
│       └── xml/
│           ├── keep_awake_accessibility_service.xml
│           └── locales_config.xml
└── test/java/com/gxrawake/app/
    ├── matcher/DialogMatcherTest.kt
    ├── service/AccessibilityStateManagerTest.kt
    └── setup/
        ├── EyesMonitorPropManagerTest.kt
        └── SelfSignedCertGeneratorTest.kt
```

---

## Local Build & Tests

Requirements: **JDK 21**, **Android SDK** (`compileSdk 36`, `targetSdk 35`, `minSdk 29`)

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew testDebugUnitTest assembleDebug
```

- Unit tests: `DialogMatcherTest` (19) + `AccessibilityStateManagerTest` (4) + `EyesMonitorPropManagerTest` (3) + `SelfSignedCertGeneratorTest` (2) — 28 tests total
- Output APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## Safety Tips for VR Sleep

- When sleeping with the headset display awake for extended periods, lower brightness to reduce OLEDoS burn-in risk and heat, and use an official charger with proper ventilation.
- You can pause auto-clicking anytime from the **[Auto-confirm warning]** switch on the main screen.
