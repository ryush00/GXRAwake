# GXRAwake

[![한국어](https://img.shields.io/badge/Language-%ED%95%9C%EA%B5%AD%EC%96%B4-1A2C33?style=for-the-badge)](README.md)
[![日本語](https://img.shields.io/badge/Language-%E6%97%A5%E6%9C%AC%E8%AA%9E-5CE1D6?style=for-the-badge)](README.ja.md)
[![English](https://img.shields.io/badge/Language-English-1A2C33?style=for-the-badge)](README.en.md)

**Galaxy XR での V睡眠（VR睡眠）向けに、30秒スタンバイ移行警告ダイアログを自動確認してスリープを防ぐ常駐 Android アプリ**

Galaxy XR を装着したまま暗い環境で目を閉じて **V睡眠** をしていると、システムが非アクティブ状態と判断して **30秒後の自動電源 OFF 警告ダイアログ** を表示します。`GXRAwake` は Android の `AccessibilityService` でこの警告を検出し、**ONを維持** ボタンを自動で押してセッションを維持します。

---

## 背景

Galaxy XR（`SM-I610`）では、開発者向けオプションの「スリープモードにしない（Stay Awake）」や長めの画面消灯時間を設定していても、暗い場所で装着中に目が検出されない状態が続くと `XrSystemUI` の **`EyesMonitor`** が独自に動作します。

1. 目が検出されない状態が **60秒** 続くと、システム警告ダイアログ（`com.android.systemui`）が表示される:
   - タイトル: **30秒後にヘッドセットの電源がOFF**
   - 本文: **ヘッドセットの電源がOFFにならないようにするには、[ONを維持]を選択してください。**
   - ボタン: **ONを維持**
2. **30秒以内**（合計 90秒到達時）にボタンを押さないと、`PowerManager.goToSleep()` が呼ばれてヘッドセットが自動的にスリープ状態になります。

`GXRAwake` はこのダイアログが表示された瞬間に確認ボタンを自動クリックし、VRChat などでの **V睡眠** 中にヘッドセットが切れてしまう問題を防ぎます。

---

## 主な機能

- **低負荷・イベント駆動の警告検出**
  - `com.android.systemui` 専用パッケージフィルタと専用バックグラウンドスレッド `GXRAwakeScanner` でイベント発生時に即座にスキャン
  - `flagIncludeNotImportantViews` を無効化してシステム全体のアクセシビリティツリー負荷を削減し、画面 ON 時のみ 5 秒間隔で `rootInActiveWindow` と `TYPE_SYSTEM` ウィンドウを補助確認します。画面 OFF 時は自動停止し、通常の VR / 2D アプリの UI スレッドには IPC を行いません。
- **誤検出を防ぐ高精度マッチング**
  - **1次判定**: `com.android.systemui` の標準 `AlertDialog` 構造（`alertTitle`, `message`, `button1`）に対し、`XrSystemUIRRO.apk` から抽出した **全 97 公式ロケール** テーブルから **現在のシステム言語（＋英語フォールバック）のみを有効化** して照合
  - **2次フォールバック**: 標準ビュー ID が変更されても現在のシステム言語の公式メッセージ・ボタンの組み合わせのみを検証して安全に検出
  - **誤クリック防止**: 通知領域やバッテリー・温度・ストレージ・アップデート等の無関係な警告を除外
- **2段階クリック＆クローズ検証**
  1. `ACTION_ACCESSIBILITY_FOCUS` → `ACTION_CLICK` 実行後、350ms 後にダイアログが閉じたか再検証
  2. 閉じていない場合はボタン中心座標へ `dispatchGesture` 120ms タップでフォールバックし、450ms 後に再検証
  3. 900ms 再試行ガードおよび `15秒` / `60秒` / `120秒` 選択式クールダウンに対応
- **再起動後の常駐自動復元**
  - 初回 1 回 `WRITE_SECURE_SETTINGS` 権限を付与すれば、ヘッドセットを再起動・アプリ更新しても PC / ADB 接続なしで自動再有効化
  - Galaxy XR 装着時の発熱状態でも遅延しないよう `AlarmManager.setAndAllowWhileIdle` ベースで動作
  - 起動直後のアンバインド時 +5 秒復元と `BOOT_COMPLETED` 基準 `+15秒 / +35秒 / +65秒 / +120秒` チェックポイントを搭載し、TalkBack 等の既存サービスも保持
- **アプリ内ワンタップ初期設定 & 診断 UI**
  - サービス接続状態、再起動自動復元権限、バッテリー最適化除外、通知権限、適用回数・最終時刻を表示
  - 自動クリック、再起動後の自動復元、成功通知のトグルとクールダウン選択
  - リアルタイム詳細診断ログの表示・コピー・消去、日本語 / 韓国語 / 英語対応

---

## インストールと初回セットアップ

Galaxy XR 独自ファームウェアの仕様上、**初回 1 回だけ** 権限付与と初期有効化が必要です。一度完了すれば、以後は **再起動しても PC や ADB 接続なし** で常時動作します。

### 方法 1. GXR 単体ワンタップ設定

`GXRAwake` 本体にワイヤレスデバッグのペア設定・自動セットアップクライアントを内蔵しているため、PC や外部ターミナルアプリなしでヘッドセット内のボタンだけで初期設定を完了できます。

1. Galaxy XR のブラウザから `GXRAwake` APK をダウンロードしてインストールし、起動します。
2. 初回のみ **開発者向けオプションを有効化**: 設定 → デバイス情報 → ソフトウェア情報 → **ビルド番号を 7 回タップ**
3. `GXRAwake` の **[ワンタップ初期設定]** カードから **[開発者向けオプション]** を押し、設定ウィンドウをアプリの横に並べて表示します。
4. **ワイヤレスデバッグ** を ON にし、**[ペア設定コードによるデバイスのペア設定]** を開きます。ペア設定ポートは mDNS で自動検出・入力されます。
5. 表示された **6桁のペア設定コード** のみを `GXRAwake` に入力して **[ワンタップ自動設定を実行]** を押すと、以下 3 つがまとめて自動完了します。
   - `WRITE_SECURE_SETTINGS` 権限の付与
   - バッテリー最適化の除外登録
   - アクセシビリティサービスの有効化および接続確認

### 方法 2. PC / Mac からの USB 接続

PC / Mac と USB 接続してコマンドで一括セットアップすることもできます。

```bash
# 1. APK インストール
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 2. 再起動後の自動復元用 WRITE_SECURE_SETTINGS 権限を付与（再起動後も維持）
adb shell pm grant com.gxrawake.app android.permission.WRITE_SECURE_SETTINGS

# 3. 長時間の V睡眠 中のバックグラウンド停止を防ぐバッテリー最適化除外（再起動後も維持）
adb shell dumpsys deviceidle whitelist +com.gxrawake.app

# 4. アクセシビリティサービスの初期有効化
adb shell settings put secure enabled_accessibility_services com.gxrawake.app/com.gxrawake.app.service.KeepAwakeAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

### 設定完了の確認

セットアップ後はヘッドセットを **再起動してロック解除** するだけで約 5 秒以内に `GXRAwake` が自動的にサービスを復元・接続し、アプリ画面に **`準備完了`** と表示されます。（確認通知を受け取る場合はアプリ内の **[通知を許可]** を 1 回押してください。）

---

## 実機検証結果

検証端末: **Samsung Galaxy XR `SM-I610`**, Android 14 / API 34, ビルド `UML2.260301.001.I610UEU2AZF3`

1. **暗所での V睡眠 30秒警告ダイアログ自動クリック検証**
   - `EyesMonitor` 60秒到達 → `showSleepWarningDialog` 表示から **`0.199秒（199ms）`** で `GXRAwake` が `ACTION_CLICK` により確認ボタンをクリック
   - `EyesMonitor` 側で `Dismiss SleepWarningDialog` → `stopEyesMonitor` → `dismissed: true` の正常完了および確認通知の表示を確認
2. **実再起動後の PC 未接続状態での自動復元検証**
   - 再起動・ロック解除後に `SecSettings`（`SimCompleteBootReceiver`）がアクセシビリティ設定を初期化すると、`RestoreAlarmReceiver(post-unbind+5s)` が **5秒後** に自動再有効化
   - `dumpsys accessibility` にて `Bound services`（`capabilities=33`, `retrieveInteractiveWindows=true`, `MotionEventInjector` 有効）の継続接続を確認
3. **PC なしの GXR 単体ワンタップ無線設定の実地検証**
   - アプリデータ・権限・バッテリー除外・アクセシビリティ設定をすべて初期化した初回インストール状態から検証
   - mDNS でペア設定先（`192.168.x.x:<port>`）を自動検出 → 6桁コード入力から **1秒** でペア設定・無線 ADB 接続・`WRITE_SECURE_SETTINGS` 付与・バッテリー最適化除外・アクセシビリティ接続まで自動完了を確認

---

## Galaxy XR ファームウェア技術解析メモ

実機（`SM-I610`）のシステムパッケージ（`SecSettings.apk`, `XrSystemUI.apk`, `services.jar`）解析で確認された仕様です。

- **Galaxy XR の「設定 → アクセシビリティ → インストール済みアプリ」に一般アプリが表示されない理由**
  - `/system_ext/priv-app/SecSettings/SecSettings.apk` 内の `SecAccessibilityUtils.isValidPreInstalledAccessibilityComponent(ComponentName)` が以下 3 コンポーネントのみ許可するようハードコードされています。
    - `com.google.android.marvin.talkback/.TalkBackService`
    - `com.samsung.android.accessibility.talkback/com.samsung.android.marvin.talkback.TalkBackService`
    - `com.salab.act/com.salab.act.ACTUiAutomationServiceClient`（Samsung 社内 QA 自動化ツール ACT）
  - `InstalledAppsManager.checkValidApp()` が上記 3 種以外を設定画面から非表示にし、さらに起動時には `SimCompleteBootReceiver` → `cleanupInvalidAccessibilityShortcuts()` が上記 3 種以外の有効なサービスを自動で OFF にします。
- **`GXRAwake` の設計方針**
  - Samsung 社内パッケージ（`com.salab.act`）へのなりすましは行わず、正規パッケージ名 **`com.gxrawake.app`** を使用しています。
  - OS 本体の `AccessibilityManagerService` 自体は一般パッケージを制限なく許可しているため、Android 公式の `development` 権限である `WRITE_SECURE_SETTINGS` と `BOOT_COMPLETED` + `AlarmManager.setAndAllowWhileIdle` の組み合わせで再起動後の常駐を実現しています。
- **30秒警告ダイアログのシステム実装（`XrSystemUI.apk`）**
  - `com.android.systemui.xr.poorconditions.EyesMonitor`: `EYES_MONITOR_TIME_SHOW_DIALOG = 60` 秒、`EYES_MONITOR_TIMEOUT_SEC = 90` 秒
  - 標準 `SystemUIDialog`（`AlertDialog` サブクラス）+ `plurals/sec_inactivity_auto_sleep_dialog_title`, `string/sec_inactivity_auto_sleep_dialog_message`, `string/sec_inactivity_auto_sleep_dialog_button`

---

## プロジェクト構成

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
    └── setup/SelfSignedCertGeneratorTest.kt
```

---

## ローカルビルド＆テスト

必要環境: **JDK 21**、**Android SDK**（`compileSdk 36`, `targetSdk 35`, `minSdk 29`）

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew testDebugUnitTest assembleDebug
```

- ユニットテスト: `DialogMatcherTest`（19件）+ `AccessibilityStateManagerTest`（4件）+ `SelfSignedCertGeneratorTest`（2件）— 合計 25 件
- 出力 APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## V睡眠での利用上の注意

- 画面を点灯したまま長時間就寝する場合は、OLEDoS パネルの焼き付き防止および発熱対策として VR アプリ・本体の輝度を下げ、純正充電器と十分な通気を確保してご利用ください。
- 一時的に止めたい場合は、アプリメイン画面の **[スリープ防止の自動クリック]** トグルからいつでもオフにできます。
