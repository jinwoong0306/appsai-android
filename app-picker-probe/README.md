# 앱 선택 기술 검증 모듈

기존 `app`과 별도 설치되는 Java/XML Android 앱이다. 사용량, 인터넷,
`QUERY_ALL_PACKAGES` 권한 없이 현재 프로필의 조회 가능한 실행 앱을
이름·아이콘·패키지명으로 표시하고 검색·선택할 수 있는지 검증한다.
캐릭터 생성, AI, 사용량 수집, 영구 저장은 포함하지 않는다.

## 실행

Android Studio에서 저장소 루트를 연 뒤 실행 모듈을 `app-picker-probe`로
선택한다. 휴대폰 또는 에뮬레이터에 다른 실행 앱이 하나 이상 있어야 한다.

```bat
gradlew.bat :app-picker-probe:assembleDebug :app-picker-probe:lintDebug
gradlew.bat :app-picker-probe:connectedDebugAndroidTest
```

인터넷 없이 기존 Gradle/SDK 캐시로 실행할 때만 `--offline`을 붙인다.
APK는 `build/outputs/apk/debug/`에 생성된다. 이 모듈의 applicationId는
`kr.appsai.pickerprobe`이므로 기존 앱사이 데이터와 분리된다.

## 기기에서 확인할 항목

1. 화면에 표시된 Android/API/target 정보와 조회 개수를 기록한다.
2. 휴대폰 앱 서랍과 비교해 이름·아이콘·패키지명이 맞는지 확인한다.
   시스템 내부 패키지, 다른 프로필, 숨긴 앱까지 일치한다고 기대하지 않는다.
3. 설치된 YouTube/Instagram 등으로 검색한다. 설치되지 않은 앱은
   추천 목록으로 꾸며서 표시하지 않는다.
4. 항목을 눌렀을 때 선택된 패키지명이 표시되는지 확인한다.
5. 없는 이름으로 검색하면 빈 결과가 나오고, 검색어를 지우면 복원되는지 확인한다.
6. 선택 후 화면 회전 시 선택값이 유지되는지 확인한다.
7. 사용 정보 접근 설정에서 이 검증 앱에 접근을 허용하지 않은 상태로도
   위 동작이 가능한지 확인한다.

자동 기기 테스트는 실제 PackageManager 반환값으로 중복 제거·이름·아이콘 로딩,
패키지 검색, 목록 선택, 빈 결과, 검색 초기화를 검증한다.
조회 결과를 mock으로 대체하지 않으며 기기에 다른 실행 앱이 없으면 실패한다.

## 판정 범위

- 앱 선택은 앱 내부에서 패키지명을 선택하는 동작이다. 상대 앱 계정 로그인이나
  사용량 권한 부여가 아니다.
- 이 모듈은 전체 설치 패키지 조회와 Google Play 승인 가능성을 증명하지 않는다.
- 검증 결과는 실행한 기기와 OS에만 해당한다. 실제 휴대폰, 업무 프로필,
  비공개 공간 등은 별도 검증이 필요하다.
- 앱 목록과 스크린샷에는 개인의 설치 정보가 포함될 수 있으므로 공개 저장소에
  실제 휴대폰의 전체 목록을 올리지 않는다.

## 2026-11-02 로컬 검증

- Pixel 7 AVD, `sdk_gphone64_x86_64`, Android 17 / API 37, 앱 target 35.
  target은 기존 앱사이와 동일하게 고정했다. Android 15/16 실기기 검증은 아니다.
- 실제 조회 결과 23개. Chrome, Calendar, Camera 등의 이름·아이콘·패키지명
  표시를 실제 화면에서 확인했다. 개수와 앱 구성은 기기마다 다르다.
- 실제 화면의 Chrome 행을 눌러 `선택됨: com.android.chrome`과 선택 강조를
  확인했다. 선택 화면 증거는 `build/picker-selected.png`와
  `build/picker-selected.xml`이다.
- 실제 PackageManager를 사용하는 기기 테스트 1건 통과: 중복 제거, 이름,
  아이콘, 검색, 선택, 빈 결과 및 초기화. 모의 목록이 아니다.
- 사용량 app-op 확인 결과 `No operations / Default mode: default`.
  Manifest에는 사용량·인터넷·전체 패키지 조회 권한을 선언하지 않았다.
- 로컬 증거: `build/picker-probe.png`, `build/picker-probe.xml`,
  `build/reports/androidTests/connected/debug/`, `build/reports/lint-results-debug.txt`.
  이 파일들은 Git 제외 대상이며 팀원이 같은 명령으로 재생성한다.
- lint는 오류 0건. 기존 앱과 맞춘 target 35, 구버전 백업 설정 안내,
  행 선택 배경의 overdraw 추정 경고는 숨기지 않고 남긴다.
  SDK 내장 legacy 테스트 runner와 시스템 바 API의 deprecated 안내도 남는다.
- YouTube/Instagram이 설치된 실제 휴대폰에서의 결과, 회전 시 상태 복원,
  업무·비공개 프로필과 Play 심사는 아직 검증하지 않았다.

## 공식 참고

- https://developer.android.com/training/package-visibility
- https://developer.android.com/training/package-visibility/declaring
- https://support.google.com/googleplay/android-developer/answer/10158779

## GitHub 전달 범위

`app-picker-probe/`의 소스·설명서와 `settings.gradle`의
`include ':app-picker-probe'` 한 줄, `docs/STATUS.md`의 이 검증 기록이
이번 작업 범위다. 기존 작업 폴더의 다른 미커밋 변경을 함께 올리지 않는다.
검증 모듈은 `feature/app-picker-probe` 브랜치로 공유한다.
팀원은 이 브랜치를 내려받고 저장소 루트를 열어 위 실행 절차를 따른다.
