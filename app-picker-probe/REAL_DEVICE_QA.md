# Galaxy S22 Ultra 실기기 검증

기준일: 2026-11-03 (PC 현지 시각, 한국). 아래 6개 기본 흐름을 이 기기에서 검증했다.
사용량은 분 단위 증가만 확인했으며 전체 제품·모든 기기·초 단위 정확도 검증은 아니다.

## 환경과 판정 원칙

- SM-S908N, Android 15/API 35, target 35. USB 디버깅 RSA 승인 후 기기를 명시하여 실행했다.
- 개인 앱 목록·사용량 전체·기기 일련번호·네트워크 주소·휴대폰 캡처는 게시하지 않는다.
- 삭제/재설치는 직접 설치한 `kr.appsai.pickerprobe`만 대상으로 한다.
- 본체는 공유 작업 폴더의 최신 구현이며 기존 원격 커밋과 동일하다고 가정하지 않는다.

## 판정표

| 항목 | 판정 | 근거 |
|---|---|---|
| 앱 목록·이름·아이콘·검색·선택 | 통과 | 독립 계측 테스트 `OK (1 test)`, 명령 exit 0. 본체 실행 앱 49개 및 테스트 앱 검색 결과 1개 확인 |
| 사용량 권한 거부·허용·철회 | 통과 | 최초 `No operations / default`로 본체 통합 테스트 통과. 시스템 앱사이 스위치를 켠 뒤 `GET_USAGE_STATS: allow` 및 사용 시간 조회 확인. 스위치를 끈 뒤 `ignore` 확인, 재조회 시 사용 시간이 아닌 권한 안내가 표시되고 앱이 유지됨 |
| 친구 생성·중복 방지·저장 복원 | 통과 | 실제 `MainFlowTest` 1건 통과. 메시지·경험치·재생성·DB 재열기·기존 행 수 보존 포함 |
| 실제 사용 시간 비교 | 분 단위 증가 확인 | 전용 검증 앱을 Stopwatch 기준 70.034초 전면 사용. 조회 표시가 약 3분에서 약 4분으로 증가. 초 단위 정확도나 하루 경계·모든 앱 정확도를 증명하지 않음 |
| 실제 회전·뒤로가기·백그라운드·프로세스 재실행 | 통과 | 실제 가로/세로 회전에서 초안 유지, 뒤로가기 홈 복귀, 강제 종료 후 친구 유지 확인. 정상 런처 작업에서 HOME 후 같은 런처 인텐트로 복귀 시 대화방·초안 유지 |
| 연결 앱 삭제·재설치 | 통과 | 전용 앱 삭제 Success → 실행 앱 49→48, 검색 0개. 기존 친구·대화 유지. 같은 APK 재설치 Success → 49개, 검색 1개. 재선택 시 기존 대화방을 열고 친구 1개 유지 |

## 설치와 실행

두 application APK 및 두 androidTest APK 설치가 모두 `Success`로 완료됐다.

```bat
gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline
adb -s DEVICE_SERIAL shell am instrument -w -r kr.appsai.pickerprobe.test/android.test.InstrumentationTestRunner
adb -s DEVICE_SERIAL shell am instrument -w -r kr.example.haliltalk.test/android.test.InstrumentationTestRunner
```

계측 결과는 각각 `OK (1 test)`, `INSTRUMENTATION_CODE: -1`, 명령 exit 0이었다.
본체 테스트는 사용량 접근을 아직 허용하지 않은 상태에서 실행했다.
이 테스트의 사용량 안내 캡처만으로 권한 허용·철회나 사용 시간 정확도를 통과 처리하지 않는다.

## 검증 방법과 실제 관측

### 실제 수명 주기 조작과 관측

- `accelerometer_rotation=0`, `user_rotation=0`을 기록했다.
- 대화방에 전송하지 않은 `Galaxy_QA_draft`를 입력한 뒤
  `settings put system user_rotation 1`을 실행했다.
- UI 트리의 `hierarchy rotation=1`, 대화방 유지와 같은 초안 값을 확인했다.
- `user_rotation=0`으로 복원한 뒤 UI 트리의 `rotation=0`과 초안 유지를 확인했다.
- 대화방에서 Android BACK을 누르면 홈으로 돌아가고 테스트 친구가 남았다.
- 본체만 `am force-stop`한 뒤 MAIN/LAUNCHER, flags `0x10200000`으로 다시 열었을 때
  홈과 테스트 친구가 유지됐다.
- 초기 단순 컴포넌트 실행으로 만든 작업과 실제 런처 인텐트를 혼용한 복귀 결과는
  제품 실패로 단정하지 않는다. `dumpsys activity recents`에서 본체의 baseIntent가
  MAIN/LAUNCHER, flags `0x10200000`임을 확인한 새 작업에서 재검증했다.
- 그 작업에서 `Galaxy_QA_launcher` 초안 입력을 확인하고 HOME을 누른 뒤
  같은 MAIN/LAUNCHER 인텐트로 복귀했다. 실제 UI 트리에서 대화방과
  같은 초안이 모두 유지됐다. 초기 혼합 인텐트 결과는 제품 실패 판정에서 제외한다.

### 사용 시간 비교

사용량 측정 구간은 한국 시각 2026-11-03 01:31:17.836~01:32:27.875였다.
측정 중 화면 꺼짐을 막기 위해 충전 중 화면 유지 설정을 임시 변경했고,
측정 직후 원래 값 `0`으로 복원한 것을 확인했다. 개인 앱 대신 직접 설치한
검증 앱만 측정했다. 기준값은 당일 기존 테스트 사용분을 포함한 3분이며
이번 70초 측정이 전체 4분을 만들었다는 뜻이 아니다.

### 삭제와 재설치

1. 직접 설치한 `kr.appsai.pickerprobe`만 `adb uninstall`했다. 결과는 `Success`.
2. 본체 홈에서 기존 친구가 남아 있는지 확인하고 앱 선택 화면을 새로 열었다.
   실행 앱 수는 49개에서 48개로 줄었고 해당 패키지 검색은 0개였다.
3. 앱이 없는 상태에서도 기존 친구 대화방과 첫 메시지를 열 수 있었다.
4. 같은 검증 APK를 다시 설치했다. 결과는 `Success`.
5. 앱 선택 화면을 새로 열면 실행 앱 49개, 해당 패키지 검색 1개로 복원됐다.
6. 항목을 선택하면 성격 선택 대화상자 없이 기존 대화방이 열렸으며
   첫 메시지가 유지됐다. 홈으로 돌아가 테스트 친구 버튼이 정확히 1개임을 확인했다.

삭제된 앱의 친구 데이터는 자동 삭제되지 않는다. 다만 삭제된 앱임을 알리는
별도 표시도 현재 없으므로 이 UX 제한을 결과와 구분한다. 개인 앱 삭제는 하지 않았다.

## 검증 종료 상태와 제한

- 사용량 접근은 최초의 `default`(미허용)로 복원했다.
- `accelerometer_rotation`, `user_rotation`, `stay_on_while_plugged_in`은
  각각 원래 값 `0`, `0`, `0`임을 확인했다.
- 본체는 홈에 있으며 테스트 친구 1개와 재설치한 검증 앱을 남겼다.
  개인 앱·기존 데이터를 초기화하지 않았고 전송하지 않은 검증 초안은 남기지 않았다.
- 기본 실행 앱만 대상이다. 업무/비공개 프로필, 자정 경계·시간대 변경,
  초 단위 사용량 정확도, 모든 기기, Google Play 심사, AI/자동 알림/캐릭터
  장착 저장은 이 결과로 검증됐다고 주장하지 않는다.
- UI 캡처 중 한 번 ADB 종료 코드 255와 재인증 대기가 발생했다.
  인증을 복구하고 새 UI 트리로 재개했으며 이를 앱 기능 실패로 분류하지 않았다.
- 검증 중 단순 컴포넌트 인텐트와 런처 인텐트를 혼용한 복귀 결과는 제외하고,
  실제 baseIntent가 일치하는 작업에서 백그라운드 복귀를 다시 검증했다.

## 설치한 APK 식별

| 파일 | SHA-256 |
|---|---|
| app-picker-probe-debug.apk | `d64ef21c46503465b44667a071cdb169601fcfb67f3229c1668f93a922940981` |
| app-picker-probe-debug-androidTest.apk | `3893adf5da3a64e928ff28956df3525c0519b73ba5aa7e134a4cad8aa02df1cd` |
| app-debug.apk | `85691230863db0dcb6fd3d4e317d6b48cb5b1d2ca29adda1de2e7dad2c9de168` |
| app-debug-androidTest.apk | `37753adf126defa7695bc705287e624983eaa146bb30c4e91ddf0197b8467ce6` |

## 기록 위치

Notion: https://app.notion.com/p/40c5d12a101881e39ff4caaec0529e84

로컬 원시 UI XML은 Git 제외 경로인 `app-picker-probe/build/galaxy-ui.xml`에 보관한다.
