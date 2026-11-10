# U1 공개 기준선 인수인계

## 2026-11-10 1차 업로드 범위

이 문서의 아래쪽은 11월 5일 **로컬 U1 작업 기록**이다. 이번 공개 1차는
기존 `release/v0.5.0-ui`의 작동하는 Android 앱을 유지하면서, 고장 난 Android
SDK 설정을 고친 CI와 독립 Worker 코드·잠금파일·합성 테스트를 함께 올린다.
아래의 로컬 변경 파일 표 전체가 이번 원격 커밋에 포함된다는 뜻이 아니다.

이번 원격 추가 파일은 `.github/workflows/android-build.yml`, `worker/`의
`package.json`, `bun.lock`, `wrangler.jsonc`, `src/` 세 파일,
`test/` 두 파일과 이 문서다. 현재 공개 앱은 기존 Firebase/Gemini 경로를
사용하며 **Worker가 앱의 실제 채팅에 연결되었다고 주장하지 않는다**.
로컬 작업 폴더의 OpenAI Android 연결, 인증, 목표, 한국어 UI와 읽음 배지 등
후속 변경은 이번 업로드에 포함하지 않는다. 이 차이를 해소하는 통합 작업과
추가 업로드는 별도 검증·커밋으로 진행한다. 두 CI job의 원격 실행 결과는
실제로 확인한 뒤 기록한다.

기준일: 2026-11-05. 이 문서는 기존 로컬 구현을 팀이 실제 개발·검토할 수 있도록
CI와 공개 설명을 정리한 U1 범위다. 같은 작업 폴더의 후속 인증·목표 기능 등
병렬 개발 완료를 선언하지 않는다. 커밋·푸시·병합·배포를 수행한 기록도 아니다.

## 이번 U1에서 수정한 파일

| 파일 | 변경 내용 |
|---|---|
| `.github/workflows/android-build.yml` | Ubuntu 24.04 고정, Node 24 기반 공식 액션 사용, SDK 필수 패키지 명시, Firebase 비공개 구성 없는 본체·계측 APK 및 독립 검증 앱 빌드, 별도 Worker 테스트·번들 dry-run |
| `GUIDE.md` | 현재 XML 화면·행, 픽셀 이미지·소품, 데모 상점·알림 검증, Cloudflare/OpenAI와 과거 Gemini 이력 구분, 공개 체크아웃 실행법과 실제 팀 개발 원칙 |
| `docs/ARCHITECTURE.md` | 실제 클래스·XML·Worker 흐름을 기준선으로 기록하고 미검증·후속 설계를 분리 |
| `docs/SOURCES.md` | 선언된 의존성·라이선스와 공식 문서, 현재 128px 이미지의 생성 시안 기반 제작 이력, 이전 64px 좌표 제작물과의 구분 |
| `docs/handoff/U1_BASELINE.md` | 변경 경계, 기존 로컬 구현, 검사 결과와 남은 원격 확인 |

앱 소스·Manifest·Gradle 설정·Worker 소스/테스트·이미지·Git 설정은 U1에서 수정하지 않았다.
`STATUS.md`는 공유 작업의 담당자가 관리한다. 아래 결과를 그 기록에 반영할 수 있으나
U1 작업자가 해당 파일을 덮어쓰지는 않았다.

## CI 실패 확인과 수정 근거

공개 실행 [41531326039](https://github.com/jinwoong0306/appsai-android/actions/runs/41531326039),
커밋 `6b861d7b77761dea96bafa9436b5cf2381ec243c`의 job `125571975040`은
`Set up Android SDK`에서 실패했고 `Build debug APK`는 실행되지 않았다.
공개 jobs/check annotations API에서 이를 확인했다. 익명 job 로그 요청은 HTTP 403이어서
그 실행의 치명적 오류 원문까지 확인했다고 주장하지 않는다.

기존 `android-actions/setup-android@v3`는 `packages`를 생략해
`tools platform-tools` 기본값을 사용했다. upstream의
[동일 기본값 실패 보고와 수정](https://github.com/android-actions/setup-android/issues/718)은
`Failed to find package 'tools'`와 폐기된 `tools` 패키지 요청 제거를 설명한다.
따라서 폐기 패키지 요청을 없애는 것이 설정에서 확인한 원인 수정이다.
이 원인과 해당 원격 실행의 오류 원문 직접 확인은 구분한다.

- `android-actions/setup-android@v4`와
  `platform-tools platforms;android-36 build-tools;35.0.0`을 명시했다.
  Platform 36은 두 앱의 `compileSdk 36`, Build Tools 35.0.0과 JDK 17은
  [AGP 8.13 요구값](https://developer.android.com/build/releases/past-releases/agp-8-13-0-release-notes)에 맞춘다.
- runner를 `ubuntu-24.04`로 고정해 `ubuntu-latest`의 OS 변경과 분리했다.
  공개 주석의 Ubuntu 26 전환 안내를 실패 원인으로 단정하지 않는다.
- checkout v7, setup-java v6, setup-node v6, setup-android v4, setup-bun v2의 실제 `action.yml`
  존재와 `runs.using: node24`, 사용하는 입력 이름을 공식 저장소에서 확인했다.
- Worker는 Bun 1.4.3, `worker/bun.lock`, `bun install --frozen-lockfile`을 사용한다.
  Wrangler 4.157.0의 `engines.node`는 `>=22.0.0`이므로 Node 24도 명시했다.
  `bun test`와 `bun run check`는 별도 job에서 실행하며 키·계정 로그인·배포는 필요 없다.
- Android는 `-PfirebaseDisabled`로 비공개 Firebase 파일 없이 APK를 컴파일한다.
  계측 APK 빌드는 기기에서 계측 테스트를 실행했다는 뜻이 아니다.

## U1 시작 전에 이미 있던 공개 대상 로컬 변경

다음은 작업 시작 시 `git status --short`로 확인한 기존 변경이다.
U1이 새로 작성하거나 검증 완료한 기능으로 귀속하지 않는다. 후속 병렬 작업은
이후 상태를 더 바꿀 수 있으므로 실제 공유 전 diff를 다시 검토한다.

기존 추적 파일 수정:

```text
.gitignore
README.md
app/build.gradle
app/src/androidTest/java/kr/example/haliltalk/MainFlowTest.java
app/src/androidTest/java/kr/example/haliltalk/ai/ChatControllerTest.java
app/src/main/java/kr/example/haliltalk/ChatApplication.java
app/src/main/java/kr/example/haliltalk/MainActivity.java
app/src/main/java/kr/example/haliltalk/ai/ChatController.java
app/src/main/res/values/strings.xml
docs/GEMINI_SETUP.md
docs/STATUS.md
```

기존 Gemini 구현 삭제:

```text
app/src/androidTest/java/kr/example/haliltalk/GeminiConnectionProbe.java
app/src/main/java/kr/example/haliltalk/ai/FirebaseAiChatService.java
```

기존 미추적 OpenAI/Worker 파일:

```text
app/src/androidTest/java/kr/example/haliltalk/OpenAiConnectionProbe.java
app/src/androidTest/java/kr/example/haliltalk/ai/OpenAiChatServiceTest.java
app/src/main/java/kr/example/haliltalk/ai/OpenAiChatService.java
docs/OPENAI_SETUP.md
worker/README.md
worker/package.json
worker/bun.lock
worker/wrangler.jsonc
worker/src/index.js
worker/src/chat.js
worker/src/app-check.js
worker/test/chat.test.js
worker/test/app-check.test.js
```

따라서 기존 추적 파일의 diff만 공유하면 OpenAI 전환본이 완성되지 않는다.
삭제와 새 구현·테스트·잠금파일·설정·문서를 함께 검토해야 한다.
`worker/node_modules/`, `worker/build/`, `.wrangler/` 등 설치·검사 출력이나
로컬 비밀 파일은 공개 대상이 아니다.

캐릭터 제작 폴더에도 기존 미추적 원본·미리보기·스크립트·문서가 다수 있었다.
U1은 이 파일들을 수정·삭제·일괄 추가하지 않았다. 현재 APK가 참조하는 128px
캐릭터와 64px 소품 경로는 `app/build.gradle`과 `docs/SOURCES.md`에서 확인한다.
공유 대상과 보관용 제작 원본·개인 캡처·영상은 구분해서 검토한다.

로컬 작업 폴더의 채팅은 Android → Cloudflare Worker → OpenAI이며 Firebase App Check를 유지한다.
이번 공개 기준선의 Android 채팅은 기존 Firebase/Gemini 구현이다.
`docs/GEMINI_SETUP.md`와 과거 Gemini 실응답 기록은 로컬 OpenAI 전환에서는
이력이지만, 이번 공개 앱에는 아직 Gemini 경로가 남아 있다.
기존 `STATUS.md`의 배포·실연결 기록을 이 U1에서 다시 실행한 결과로 표시하지 않는다.

## 이번 로컬 검사 결과

Windows, Bun `1.4.3`, Node `v24.14.0`에서 다음을 한 번의 순차 실행으로 확인했다.

```text
cd worker
bun install --frozen-lockfile
  Checked 35 installs across 93 packages (no changes)
bun test
  125 pass / 0 fail / 373 expect() calls / 2 files
bun run check
  wrangler deploy --dry-run --outdir build
  Wrangler 4.157.0
  Total Upload: 43.49 KiB / gzip: 12.13 KiB
  --dry-run: exiting now.
전체 명령 exit_code: 0
```

테스트는 합성 요청·로컬 서명 키·주입한 공급자 응답을 사용한다.
실제 OpenAI 호출·원격 Worker 배포·비밀 등록을 실행한 것이 아니다.
설치 때 잠금파일 변경은 없었다.

워크플로는 Bun YAML 파서로 파싱하고 두 job·세 이벤트·읽기 전용 권한과
공식 액션 메타데이터를 확인했다. `git diff --check`는 exit 0이었다.
Git의 기존 LF→CRLF 변환 안내는 남아 있으며 Git 설정을 변경하지 않았다.
전용 actionlint 검사는 실행하지 않았다.

Gradle·Android 설치·기기 테스트는 이 U1 범위에서 실행하지 않았다.
기존 Android lint 실패를 고치거나 숨기지 않았으며 본체 빌드·영향 화면·권한·저장
검증은 통합 담당자의 별도 결과와 `STATUS.md`를 따른다.

## 팀 인수와 남은 확인

팀원에게는 실제 소스와 검사 절차를 공유하고, 각자가 직접 개발·리뷰할 범위를
합의한다. 각자의 실제 변경은 본인 계정의 브랜치·커밋·리뷰로 남긴다.
타인 작성자 지정·대리 작성·빈 커밋·완료하지 않은 담당 수락으로 참여를 꾸미지 않는다.
U1 공유는 팀원별 개발이나 승인·병합 완료를 대신하지 않는다.

원격에서 남은 확인은 승인된 공유 후 GitHub Actions의 두 job이 실제 runner에서
성공하는지 확인하는 것이다. 특히 SDK 다운로드·라이선스·Linux 잠금파일 설치와
Android APK 빌드 결과는 로컬 YAML 검사나 Windows Worker 결과만으로 확정하지 않는다.
필요하면 인증된 GitHub 로그에서 이전 SDK 실패의 원문도 대조한다.
이 문서는 커밋·푸시·원격 실행·새 배포가 이미 수행됐다고 주장하지 않는다.
