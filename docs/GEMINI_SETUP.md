# Gemini 대화 설정과 검증

## 연결 구성

Android Java/XML 본체에서 Firebase AI Logic의 Gemini Developer API 백엔드와
`gemini-3.1-flash-lite`를 사용한다. Vertex AI나 온디바이스 모델이 아니다.
Firebase BoM `35.1.0`, `firebase-ai`(BoM 관리), Java 비동기 Guava
`31.0.1-android`, Google Services Gradle 플러그인 `4.4.4`를 사용한다.
AndroidX 의존성을 위해 루트 `gradle.properties`에 `android.useAndroidX=true`가 필요하다.

`AiChatService`와 실제 Firebase 구현을 분리했다. 테스트는 가짜 비동기 전송을
주입하며 실제 모델 응답 성공을 뜻하지 않는다.

## Firebase 설정

1. Firebase 프로젝트 `appsai-ai-test`에 Android 앱을 **정확히**
   `kr.example.haliltalk`로 등록한다. applicationId 변경이나 데이터 초기화는 필요 없다.
2. 해당 Android 앱의 `google-services.json`을 로컬 `app/`에 둔다.
   이 파일은 Firebase 공개 클라이언트 설정이며 Gemini 비밀 키가 아니다.
   이 프로젝트에서는 로컬 파일을 커밋하지 않는다. 서비스 계정·Gemini 비밀 키·
   App Check 디버그 토큰을 소스, APK 상수, 문서나 공유 로그에 넣지 않는다.
3. Firebase AI Logic에서 Gemini Developer API를 설정하고 모델 가용성과 무료 한도를
   확인한다. 결제 연결·Blaze 전환은 이 구현의 설정 단계에 포함하지 않는다.
4. 디버그 빌드는 `firebase-appcheck-debug`만, 릴리스 빌드는
   `firebase-appcheck-playintegrity`만 포함한다. `ChatApplication`에서 각각
   공급자를 초기화한다. 디버그 토큰은 로컬 비공개 절차로 Firebase에 등록한다.
   SDK의 디버그 토큰이 포함될 수 있는 원시 logcat을 공개하거나 저장소에 올리지 않는다.
5. 릴리스는 Play Integrity 앱 등록·서명·배포 조건을 별도로 검증한다.
   디버그 공급자를 릴리스에 넣거나 App Check 강제 적용을 끄는 우회는 없다.
   강제 적용 여부는 Firebase 콘솔에서 확인해야 하며 앱 코드만으로 보장되지 않는다.

설정 파일이 없으면 빌드는 가능하지만 채팅에 “AI 연결이 설정되지 않았어요”가
표시되며 전송은 비활성화된다. 가짜 AI 답장으로 대체하지 않는다.
설정 파일을 추가한 뒤에는 Gradle 동기화와 재빌드가 필요하다.

## 전송, 저장, 실패 규칙

- 최초 전송 전 별도 동의를 받는다. Google 무료 서비스에서 입력·응답이 제품 개선에
  사용될 수 있음을 안내하며, 가상 내용만 테스트하도록 알린다.
  채팅 상단의 전송 안내에서 동의를 철회할 수 있다. 이미 전송한 내용을 회수하지는 못한다.
- 선택한 성격과 이 방의 최근 실제 AI 대화만 보낸다. 현재 메시지를 포함해 최대
  13개 메시지·12,000자이며 메시지 하나는 최대 2,000자다. 다른 방, 설치 목록,
  패키지명, 친구 이름, 사용량은 자동 전송하지 않는다. 사용자가 본문에 적은 내용은
  그대로 전송한다. 옛 예시 답장과 기기 인사, 실패·취소한 대화는 외부 맥락에서 제외한다.
- DB 버전 3은 `chat_requests`와 열린 요청 유일 인덱스만 추가한다.
  버전 1·2의 친구·메시지·XP·외형은 유지한다.
- 사용자 메시지 저장과 XP `+1`은 한 트랜잭션으로 최초 전송 때만 처리한다.
  실패·빈 응답·안전 차단 등에서는 AI 메시지를 만들지 않는다.
  응답이 도착한 성공 요청만 답장과 완료 상태를 한 트랜잭션으로 저장한다.
- 방마다 미해결 요청은 하나다. 전송 중에는 중복 전송을 막으며,
  실패 뒤 “답장 재시도”는 기존 사용자 메시지·요청을 재사용한다.
  “재시도 안 함”은 사용자 메시지를 보존하고 새 메시지를 보낼 수 있게 한다.
  재시도나 AI 응답으로 XP·재화·목표 완료를 변경하지 않는다.
- 앱 범위의 요청 소유자가 회전·화면 이동을 견딘다. 프로세스 종료 뒤 남은 pending은
  실패로 복구하며 자동 재전송하지 않는다. 재시도 번호와 상태를 확인하므로
  이전 요청의 늦은 결과는 새 답장을 중복 저장하지 않는다.
- 로컬 요청 제한은 60초다. 실패 메시지는 공급자의 원시 예외·키·응답 본문을
  표시하거나 로그로 남기지 않는다. 설정 누락·시간 초과·중단은 구분하며,
  그 외 네트워크·할당량·안전 차단·App Check 거절은 일반 실패 안내와 명시적 재시도를 제공한다.
  원격 요청이 처리된 뒤 연결이 끊기면 재시도는 공급자 호출을 다시 할 수 있다.
  로컬 답장·XP 중복 방지와 공급자 측 과금·호출 중복 방지는 다른 문제다.

## 로컬 검증 명령

설정 없이 또는 콘솔 설정 중에도 실제 API를 호출하지 않는 APK를 만들 수 있다.
`firebaseDisabled`는 로컬 빌드 전용이며 앱에 비밀 키나 설정 대체값을 넣지 않는다.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:compileReleaseJavaWithJavac -PfirebaseDisabled=true -Pandroid.useAndroidX=true
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e class kr.example.haliltalk.MainFlowTest,kr.example.haliltalk.ai.ChatControllerTest,kr.example.haliltalk.data.FriendsDatabaseMigrationTest kr.example.haliltalk.test/android.test.InstrumentationTestRunner
```

기존 플랫폼 `InstrumentationTestRunner`가 늘어난 Firebase 전이 의존성 APK 전체를
자동 검색하면 테스트 실행 전 클래스 탐색에서 ANR이 날 수 있어 세 테스트 클래스를
명시한다. 이는 테스트를 제외하는 설정이 아니며 현재 본체의 모든 테스트 클래스다.

`MainFlowTest`는 실제 Java/XML 화면의 동의 거절·허용·철회, 전송 중 회전,
화면 이동 뒤 실패 표시, 중복 재시도 방지, 성공 후 1회 XP와 기존 UI 흐름을 확인한다.
`ChatControllerTest`는 구성/동의/길이 제한, 실패·빈 답장, 재시도와 늦은 결과,
프로세스 재생성 상태 복구, 맥락 범위·크기, 메인 스레드 알림을 검증한다.
`FriendsDatabaseMigrationTest`는 메모리 DB의 1→3·2→3 보존과 요청 유일성/정리를 검증한다.
모두 실제 네트워크를 쓰지 않는 결정적 회귀다. 개인 휴대폰에 명령을 실행하지 않는다.

콘솔 설정 후 `firebaseDisabled` 없이 재빌드한 앱에서 동의 안내를 읽고
**가상 텍스트 한 건**으로 실제 응답·저장·App Check 허용을 확인해야 한다.
실제 호출, 무료 한도, 모델 접근, 릴리스 Play Integrity는 이 로컬 회귀만으로
검증됐다고 보고하지 않는다.

## 2026-11-02 로컬 검증 결과

- 실제 Android 클라이언트 설정을 사용한 `assembleDebug`,
  `assembleDebugAndroidTest`, `compileReleaseJavaWithJavac`가 exit 0으로 완료됐다.
  설정을 명시적으로 제외한 `assembleDebug -PfirebaseDisabled=true`도 exit 0이다.
  두 모드 모두 `-Pandroid.useAndroidX=true`로 실행했다.
- `emulator-5554`에서 명시적으로 선택한 전체 3개 클래스가
  **`OK (9 tests)`**, 9.223초로 통과했다. 실제 API 대신 제어 가능한 비동기 가짜 전송만
  사용했다. 테스트 DB는 메서드별 접두사를 사용하고, 기반 Context의 정확한 파일명으로
  제거해 반복 실행 시 남은 fixture와 충돌하지 않도록 했다.
- 360dp 폭·글꼴 1.3배·야간 모드에서 새 AI 채팅 UI 테스트도
  **`OK (1 test)`**, 2.883초로 통과했다. 밝은/어두운 화면의 로딩·실패/재시도·
  성공 상태를 직접 확인했다. 캡처는 로컬 `app/build/gemini-qa-light/`,
  `app/build/gemini-qa-small-dark/`에 있다.
- 에뮬레이터는 크기 1080×2400, 글꼴 1.0, 야간 모드 no로 복원했다.
  개인 휴대폰이나 원래 대화 데이터를 초기화하지 않았다.
- `lintDebug`는 기존 Manifest의 `PACKAGE_USAGE_STATS` / `ProtectedPermissions`
  오류로 실패했다. 경고는 총 11건이며 선택한 Guava 버전의 업데이트 알림도 포함한다.
  억제하지 않았다. Java LSP(`jdtls`) 미설치, XML LSP 미설정으로 해당 진단은
  실행하지 못했고 Java/리소스 컴파일과 기기 계측으로 확인했다.
- 위 자동 회귀는 실제 공급자 호출 증거와 구분한다. 후속 실제 연결 검증 결과는
  아래에 기록했다. 릴리스 Play Integrity와 무료 한도 소진 시 동작은 검증하지 않았다.

## 수동 실제 연결 진단

`GeminiConnectionProbe`는 자동 회귀에 포함되지 않는 별도 instrumentation이다.
명시적으로 선택한 경우에만 고정된 가상 인사말을 실제 공급자에 전송한다.
대화 DB나 사용자 동의 상태는 수정하지 않고 인증 단계, 답변 글자 수 또는 예외
클래스 이름만 출력한다. 토큰, 원시 예외 메시지와 답변 본문은 출력하지 않는다.

```powershell
.\gradlew.bat :app:assembleDebugAndroidTest -PgeminiProbe=true
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w kr.example.haliltalk.test/kr.example.haliltalk.GeminiConnectionProbe
```

2026-11-02 실제 에뮬레이터에서 `APP_CHECK_ACCEPTED`와
`GEMINI_RESPONSE_RECEIVED`, `response_characters=38`,
`INSTRUMENTATION_CODE: -1` 및 명령 exit 0을 확인했다.
이후 실제 `Gemini test` 채팅방에서 시간 초과한 합성 메시지를 재시도해
한국어 답장이 표시되는 것을 확인했다. 재시도 전후 경험치는 2로 유지됐고,
앱 강제 종료·콜드 스타트 후 같은 답장이 복원됐다.
증거는 `app/build/gemini-live/real-reply.png`와 `restored-reply.png`다.
해당 검증 APK는 `app/build/gemini-live/outputs/apk/debug/app-debug.apk`다.
기존 친구와 대화 데이터를 초기화하지 않았으며 테스트 친구도 검증 흔적으로 남겼다.

일반 회귀로 돌아갈 때는 `-PgeminiProbe` 없이 테스트 APK를 다시 빌드한다.
`-PfirebaseDisabled=true`는 오프라인 검증용이므로 실제 연결 시 사용하지 않는다.

## 의존성 출처와 라이선스

Firebase Android SDK와 Guava는 Apache License 2.0으로 배포된다.
해결된 `firebase-ai:18.1.0` 및 `guava-parent:31.0.1-android` POM의
라이선스 선언을 확인했다. SDK 자체는 Kotlin 및 네트워크 구성요소를 전이 의존성으로
포함하지만 앱의 동작 구현은 Java이며 Kotlin 소스·온디바이스 추론 SDK를 추가하지 않는다.
Firebase SDK의 전이 `firebase-ai-ondevice-interop`는 호환 인터페이스이며 이 앱에서
온디바이스 백엔드나 추론을 선택하지 않는다. 배포 시 전이 의존성 고지는 별도로 확인한다.

- Firebase SDK 소스: https://github.com/firebase/firebase-android-sdk
- Guava 소스: https://github.com/google/guava
- Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0

## 공식 출처

- https://firebase.google.com/docs/ai-logic/get-started?api=dev&platform=android&hl=en
- https://firebase.google.com/docs/ai-logic/system-instructions?api=dev&platform=android&hl=en
- https://firebase.google.com/docs/ai-logic/app-check
- https://firebase.google.com/docs/app-check/android/debug-provider
- https://firebase.google.com/docs/app-check/android/play-integrity-provider
- https://firebase.google.com/docs/ai-logic/models
- https://ai.google.dev/gemini-api/docs/pricing
