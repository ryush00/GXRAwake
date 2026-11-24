# GXRAwake

갤럭시 XR(Galaxy XR)에서 어두운 환경에 눈을 감고 있을 때 나타나는 30초 자동 종료(대기 전환) 경고창을 Android 접근성 서비스(`AccessibilityService`)로 감지하고, 확인 버튼을 자동으로 눌러 기기 종료를 방지하는 앱 프로젝트입니다.

## 문제 배경

갤럭시 XR은 뛰어난 VR/XR 기기이지만, 어두운 곳에서 눈을 감고 있으면 시스템이 비활성 상태로 판단해 경고창을 띄웁니다. 이 경고창에서 **30초 이내에 확인(계속 켜두기) 버튼을 누르지 않으면 XR이 자동으로 종료(대기 상태 전환)**되어 버리는 큰 단점이 있습니다.

## 프로젝트 목표

- Android `AccessibilityService`를 이용해 갤럭시 XR의 대기 전환/종료 경고창을 백그라운드에서 안정적으로 감지
- 경고창이 감지되면 확인 버튼을 자동으로 클릭하여 30초 카운트다운 종료를 방지
- 일반 클릭 액션(`ACTION_CLICK`)이 동작하지 않는 경우 제스처 탭(`dispatchGesture`)으로 폴백하고, 실제 다이얼로그가 닫혔는지 검증
- 사용자가 쉽게 설정하고 상태를 확인할 수 있는 최소한의 설정 UI 제공
  - 접근성 서비스 활성화 상태 확인 및 접근성 설정 바로가기
  - 배터리 최적화 제외 설정 바로가기
  - 자동 클릭 기능 ON/OFF 토글
  - 적용 시 알림 표시(선택) 및 최근 활동 / 상세 디버그 로그 확인

## 참고 APK 분석 (`gxr-keep-awake.apk`)

다운로드된 참고 APK(`/Users/ryush00/Downloads/gxr-keep-awake.apk`, `0.1.0-galaxyxr`, `minSdk 29` / `targetSdk 35`)의 구조를 분석한 핵심 참고 사항입니다.

### 1. 대상 경고창 매칭 방식
- **대상 패키지**: `com.android.systemui`
- **표준 다이얼로그 구조 확인**:
  - `android:id/alertTitle`
  - `android:id/message`
  - `android:id/buttonPanel`
  - `android:id/button1` (확인/긍정 버튼), `android:id/button2`, `android:id/button3`
- **텍스트 힌트 및 오탐 방지**:
  - 다국어 키워드 매칭
    - 한국어: `헤드셋`, `계속 켜`, `켜두기`, `대기 상태`, `전환`
    - 영어: `headset`, `turning off`, `keep on`, `sleep`, `standby`
    - 일본어: `ヘッドセット`, `オンのまま`, `スタンバイ`
  - 카운트다운 숫자 포함 여부 확인
  - 일반 알림창(`알림:`, `시스템 알림`, `notification:`, `notifications`, `통지:`) 컨텍스트 제외

### 2. 접근성 서비스 설정 및 감지 흐름
- **이벤트 타입** (`0x00400820`): `typeWindowStateChanged | typeWindowContentChanged | typeWindowsChanged`
- **접근성 플래그** (`0x00000052`): `flagReportViewIds | flagRetrieveInteractiveWindows | flagIncludeNotImportantViews`
- **권한/기능 속성**: `canRetrieveWindowContent="true"`, `canPerformGestures="true"`, `isAccessibilityTool="true"`
- **스캔 방식**: 접근성 이벤트 수신 시 즉시 검사 + `1000ms` 주기의 보조 폴링으로 활성 윈도우 및 전체 인터랙티브 윈도우(`getWindows()`) 탐색

### 3. 클릭 및 검증 전략
1. 대상 버튼 노드에 `ACTION_CLICK` 수행 후 `350ms` 뒤 다이얼로그가 사라졌는지 재확인
2. 다이얼로그가 여전히 표시되어 있으면 버튼의 화면 영역(`Rect`)을 기준으로 `dispatchGesture` 탭 폴백 수행 후 `450ms` 뒤 재검증
3. 중복 동작 방지를 위한 재시도 간격(`900ms`) 및 쿨다운 처리

### 4. 구현 시 확인이 필요한 Galaxy XR 특이사항
- 참고 APK는 앱 로직이 `dev.yochi.galaxyxr.keepawake.*`에 위치하지만, 매니페스트 패키지명과 접근성 서비스 클래스명을 각각 `com.salab.act` / `com.salab.act.ACTUiAutomationServiceClient`로 선언하고 있습니다.
- 갤럭시 XR OS 정책상 특정 패키지명/서비스명 허용 목록이 필요한지, 일반 패키지명으로도 접근성 서비스가 정상 동작하는지 실제 기기 테스트를 통해 확인할 예정입니다.

## 향후 진행 계획

- [ ] Android 프로젝트(Gradle) 초기 구조 세팅
- [ ] `AccessibilityService` 및 `DialogMatcher` 구현
- [ ] `ACTION_CLICK` + `dispatchGesture` 폴백 및 닫힘 검증 로직 구현
- [ ] 상태 표시 / 설정 바로가기 / 토글 / 로그 확인 UI 구현
- [ ] 갤럭시 XR 실기기 테스트(어두운 환경 30초 경고창 자동 확인 검증)
