# GXRAwake

[![한국어](https://img.shields.io/badge/Language-%ED%95%9C%EA%B5%AD%EC%96%B4-5CE1D6?style=for-the-badge)](README.md)
[![日本語](https://img.shields.io/badge/Language-%E6%97%A5%E6%9C%AC%E8%AA%9E-1A2C33?style=for-the-badge)](README.ja.md)
[![English](https://img.shields.io/badge/Language-English-1A2C33?style=for-the-badge)](README.en.md)

**갤럭시 XR(Galaxy XR) 브수면(VR 수면)을 위한 30초 대기 전환 경고창 자동 확인 & 상시 유지 Android 앱**

갤럭시 XR을 착용한 채 어두운 환경에서 눈을 감고 **브수면**을 할 때, 시스템이 비활성 상태로 판단해 띄우는 **30초 자동 종료(대기 전환) 경고창**을 Android `AccessibilityService`로 감지해 **계속 켜두기** 버튼을 자동으로 눌러 줍니다.

---

## 왜 필요한가요?

갤럭시 XR(`SM-I610`)은 개발자 옵션의 `화면 켜짐 유지(Stay Awake)`나 긴 화면 꺼짐 시간을 설정해 두더라도, 어두운 환경에서 헤드셋 착용 중 눈이 감지되지 않으면 `XrSystemUI`의 **`EyesMonitor`**가 별도로 작동합니다.

1. 눈 미감지 **60초** 경과 시 시스템 경고창(`com.android.systemui`) 표시:
   - 제목: **헤드셋이 30초 후에 대기 상태로 전환됨**
   - 본문: **대기 상태로 전환하지 않으려면 계속 켜두기 버튼을 누르세요.**
   - 버튼: **계속 켜두기**
2. **30초 이내**(총 90초 도달)에 버튼을 누르지 않으면 `PowerManager.goToSleep()`이 호출되어 헤드셋이 자동으로 대기 상태(종료)로 전환됩니다.

이로 인해 VRChat 등 VR 환경에서 눈을 감고 쉬거나 **브수면**을 유지하기 어려운 문제가 있으며, `GXRAwake`는 이 경고창이 뜰 때마다 즉시 확인 버튼을 눌러 헤드셋 연결과 세션을 끊김 없이 유지합니다.

---

## 주요 기능

- **2중 하이브리드 꺼짐 방지**
  - **1차 근본 차단**: 원클릭 무선 디버깅 실행 시 `setprop debug.ix.sysui.eyes_monitor.enabled false`를 함께 적용해 **재부팅 전까지 경고창 자체가 아예 뜨지 않게 차단** (아이트래킹·센서에는 영향 없음)
  - **2차 재부팅 안전망**: 재부팅으로 `debug.*` 프로퍼티가 초기화되더라도 `WRITE_SECURE_SETTINGS` + `AlarmManager.setAndAllowWhileIdle` 기반으로 접근성 서비스가 자동 복구되어 경고창을 0.2초 만에 자동 확인
- **저부하 이벤트 중심 경고창 감지**
  - `com.android.systemui` 전용 패키지 필터와 전용 백그라운드 스레드 `GXRAwakeScanner`로 이벤트 즉시 스캔
  - `flagIncludeNotImportantViews`를 제거해 전체 앱 접근성 트리 부하를 최소화하고, 화면이 켜져 있을 때만 5초 간격으로 `rootInActiveWindow` 및 `TYPE_SYSTEM` 윈도우를 보조 검사합니다. 화면이 꺼지면 폴링을 자동 중단하며 일반 VR/2D 앱 윈도우는 조회하지 않습니다.
- **오탐 없는 정밀 다이얼로그 매칭**
  - **1차 매칭**: `com.android.systemui` 표준 `AlertDialog` 구조(`alertTitle`, `message`, `button1`)와 `XrSystemUIRRO.apk`에서 추출한 **97개 전체 공식 로캘** 테이블 중 **현재 시스템 언어(+ 영문 폴백)만 활성화**해 매칭
  - **2차 폴백**: 표준 뷰 ID가 바뀌더라도 현재 시스템 언어의 공식 문구·버튼 쌍(클릭 가능한 부모 래퍼 포함)만 교차 검증해 안전하게 매칭
  - **오탐 차단**: 일반 알림창 및 배터리·온도·저장공간·업데이트 등 비관련 시스템 경고 제외
- **2단계 클릭 및 닫힘 검증**
  1. `ACTION_ACCESSIBILITY_FOCUS` → `ACTION_CLICK` 실행 후 350ms 뒤 경고창이 실제로 닫혔는지 검증
  2. 닫히지 않았으면 버튼 중심 좌표에 `dispatchGesture` 120ms 탭 폴백을 수행하고 450ms 뒤 재검증
  3. 900ms 재시도 가드 및 `15초` / `60초` / `120초` 선택형 쿨다운 지원
- **재부팅 후 상시 자동 복구**
  - 최초 1회 `WRITE_SECURE_SETTINGS` 권한을 부여해 두면 기기를 **재부팅**하거나 앱을 업데이트해도 PC/ADB 연결 없이 접근성 서비스를 자동 재활성화
  - Galaxy XR 착용 시 발열 상태에서도 지연되지 않도록 `AlarmManager.setAndAllowWhileIdle` 기반으로 동작
  - 부팅 초기 언바인드 시 +5초 복구와 `BOOT_COMPLETED` 기준 `+15초 / +35초 / +65초 / +120초` 체크포인트 제공, 기존 TalkBack 등 활성 서비스 보존
- **원클릭 초기 설정 및 진단 UI**
  - 접근성 연결 상태, 재부팅 자동 복구 권한, 배터리 최적화 제외, 알림 권한, 누적 적용 횟수/시각 표시
  - 자동 클릭, 재부팅 자동 복구, 성공 알림 토글 및 쿨다운 선택
  - 실시간 상세 진단 로그 조회·복사·초기화 및 한/영/일 다국어 지원

---

## 설치 및 1회 초기 설정

Galaxy XR 펌웨어의 `SecSettings` 제한으로 인해 **최초 1회**만 권한 부여 및 초기 활성화가 필요합니다. 한 번 설정해 두면 이후에는 **재부팅을 해도 PC나 ADB 연결 없이** 상시 작동합니다.

### 방법 1. GXR 단독 원클릭 설정

`GXRAwake` 앱에 무선 디버깅 페어링 및 자동 설정 클라이언트가 내장되어 있어, PC나 별도 터미널 앱 없이 헤드셋 안에서 버튼 한 번으로 초기 설정을 끝낼 수 있습니다.

1. GXR 브라우저에서 `GXRAwake` APK를 내려받아 설치하고 실행합니다.
2. 최초 1회 **개발자 옵션 활성화**: 설정 → 기기 정보 → 소프트웨어 정보 → **빌드번호 7회 탭**
3. `GXRAwake` 앱의 **[원클릭 자동 설정]** 카드에서 **[개발자 옵션 열기]**를 눌러 설정 창을 앱 옆에 띄웁니다.
4. **무선 디버깅**을 켠 뒤 **[페어링 코드로 기기 페어링]**을 누릅니다. 페어링 포트는 mDNS로 자동 감지되어 입력됩니다.
5. 화면에 표시된 **6자리 페어링 코드**만 `GXRAwake`에 입력하고 **[원클릭 자동 설정 실행]** 버튼을 누르면 아래 4가지가 자동으로 완료됩니다.
   - `WRITE_SECURE_SETTINGS` 권한 부여
   - 배터리 최적화 제외 등록
   - 경고창 근본 차단(`setprop debug.ix.sysui.eyes_monitor.enabled false`) 적용
   - 접근성 서비스 자동 활성화 및 연결 확인

### 방법 2. PC / Mac USB 연결

PC/Mac에 USB로 연결해 한 번에 설치·설정할 수도 있습니다.

```bash
# 1. APK 설치
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 2. 재부팅 후 자동 복구를 위한 WRITE_SECURE_SETTINGS 권한 부여 (재부팅 후 유지)
adb shell pm grant com.gxrawake.app android.permission.WRITE_SECURE_SETTINGS

# 3. 장시간 브수면 중 백그라운드 종료 방지를 위한 배터리 최적화 제외 (재부팅 후 유지)
adb shell dumpsys deviceidle whitelist +com.gxrawake.app

# 4. 경고창 근본 차단 (재부팅 전까지 60초 경고창·90초 강제 슬립 자체 미발생)
adb shell setprop debug.ix.sysui.eyes_monitor.enabled false

# 5. 접근성 서비스 초기 활성화 (재부팅 후에도 자동 복구되는 안전망)
adb shell settings put secure enabled_accessibility_services com.gxrawake.app/com.gxrawake.app.service.KeepAwakeAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

### 설정 완료 확인

설정 후에는 헤드셋을 **재부팅하고 잠금 해제**만 해도 약 5초 이내에 `GXRAwake`가 자동으로 서비스를 복구·연결하며, 앱 화면에서 **`준비됨`** 상태를 확인할 수 있습니다. (성공 알림을 받으려면 앱 내 **[알림 권한 허용]** 버튼을 1회 눌러 주세요.)

---

## 실기기 검증 결과

테스트 기기: **Samsung Galaxy XR `SM-I610`**, Android 14 / API 34, 빌드 `UML2.260301.001.I610UEU2AZF3`

1. **어두운 환경 브수면 30초 경고창 자동 클릭 검증**
   - `EyesMonitor` 60초 도달 → `showSleepWarningDialog` 표시 후 **`0.199초(199ms)` 만에** `GXRAwake`가 `ACTION_CLICK`으로 `계속 켜두기` 클릭
   - `EyesMonitor`에서 `Dismiss SleepWarningDialog` → `stopEyesMonitor` → `dismissed: true` 정상 처리 및 성공 알림 표시 확인
2. **실제 재부팅 후 PC 미연결 상태 자동 복구 검증**
   - 재부팅 및 잠금 해제 후 `SecSettings`(`SimCompleteBootReceiver`)가 접근성 설정을 초기화하자, `RestoreAlarmReceiver(post-unbind+5s)`가 **5초 만에** 자동 재활성화
   - `dumpsys accessibility`에서 `Bound services` (`GXRAwake 꺼짐 방지`, `capabilities=33`, `retrieveInteractiveWindows=true`, `MotionEventInjector` 활성) 정상 유지 확인
3. **PC 없는 GXR 단독 원클릭 무선 자동 설정 실전 검증**
   - 앱 데이터·권한·배터리 예외·접근성 설정을 모두 초기화한 최초 설치 상태에서 테스트
   - mDNS로 페어링 주소(`192.168.x.x:<port>`) 자동 감지 → 6자리 코드 입력 후 **1초 만에** 페어링 → 무선 ADB 연결 → `WRITE_SECURE_SETTINGS` 부여 → 배터리 최적화 제외(`Added: com.gxrawake.app`) → 접근성 서비스 바인딩까지 원클릭 완료 확인

---

## Galaxy XR 펌웨어 기술 분석 메모

실기기(`SM-I610`)의 시스템 패키지(`SecSettings.apk`, `XrSystemUI.apk`, `services.jar`) 분석으로 확인한 플랫폼 동작입니다.

- **왜 Galaxy XR 설정 → 접근성 → 설치된 앱에 서드파티 앱이 보이지 않는가?**
  - `/system_ext/priv-app/SecSettings/SecSettings.apk`의 `SecAccessibilityUtils.isValidPreInstalledAccessibilityComponent(ComponentName)`가 아래 3개 컴포넌트만 허용하도록 하드코딩되어 있습니다.
    - `com.google.android.marvin.talkback/.TalkBackService`
    - `com.samsung.android.accessibility.talkback/com.samsung.android.marvin.talkback.TalkBackService`
    - `com.salab.act/com.salab.act.ACTUiAutomationServiceClient` (삼성 내부 QA 자동화 도구 ACT)
  - `InstalledAppsManager.checkValidApp()`가 위 3종 외 앱을 설정 목록에서 숨기며, 부팅 시 `SimCompleteBootReceiver` → `cleanupInvalidAccessibilityShortcuts()`가 위 3종 외 활성 서비스를 자동으로 OFF 합니다.
- **`GXRAwake`의 설계 선택**
  - 삼성 내부 패키지명(`com.salab.act`)을 사칭하지 않고 정식 패키지명 **`com.gxrawake.app`**을 사용합니다.
  - 핵심 프레임워크(`AccessibilityManagerService`) 자체는 일반 패키지명의 접근성 서비스를 제한 없이 허용하므로, Android 공식 `development` 권한인 `WRITE_SECURE_SETTINGS`(사용자가 ADB로 직접 1회 부여)와 `BOOT_COMPLETED` + `AlarmManager.setAndAllowWhileIdle` 조합으로 재부팅 영속성을 깔끔하게 해결했습니다.
- **30초 경고창의 시스템 구현 (`XrSystemUI.apk`)**
  - `com.android.systemui.xr.poorconditions.EyesMonitor`: `EYES_MONITOR_TIME_SHOW_DIALOG = 60`초, `EYES_MONITOR_TIMEOUT_SEC = 90`초
  - 표준 `SystemUIDialog`(`AlertDialog` 서브클래스) + `plurals/sec_inactivity_auto_sleep_dialog_title`, `string/sec_inactivity_auto_sleep_dialog_message`, `string/sec_inactivity_auto_sleep_dialog_button`

---

## 프로젝트 구조

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

## 로컬 빌드 및 테스트

요구 환경: **JDK 21**, **Android SDK** (`compileSdk 36`, `targetSdk 35`, `minSdk 29`)

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew testDebugUnitTest assembleDebug
```

- 단위 테스트: `DialogMatcherTest` (19개) + `AccessibilityStateManagerTest` (4개) + `EyesMonitorPropManagerTest` (3개) + `SelfSignedCertGeneratorTest` (2개) — 총 28개 테스트
- 빌드 산출물: `app/build/outputs/apk/debug/app-debug.apk`

---

## 브수면 사용 시 유의사항

- 장시간 화면을 켜 둔 상태로 수면할 경우 OLEDoS 패널 번인 방지 및 발열 관리를 위해 VR 앱/기기 밝기를 낮추고, 정품 충전기 및 통풍이 확보된 환경에서 사용해 주세요.
- 원치 않을 때는 앱 메인 화면의 **[꺼짐 방지 자동 클릭]** 토글만으로 즉시 일시 중지할 수 있습니다.
