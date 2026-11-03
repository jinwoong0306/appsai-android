# 앱사이 | AppSai

설치된 앱과 목표를 친구 캐릭터로 만들고, 대화하며 관계를 쌓는 Android 앱 프로젝트입니다.

> 현재 버전은 `0.5.0-ui` 프로토타입입니다. 로그인과 상점 재화는 목업이며, 사용시간 초과 자동 감시와 목표 기록은 미구현입니다. AppSai는 작업용 영문 표기이며 상표 검토가 완료되지 않았습니다.

## 현재 기능

- 목업 로그인·기기 내 세션 유지, 첫 실행 환영 알림과 알림 설정
- 실행 앱 목록·이름 검색, 앱 친구 추가 및 성격 선택
- 기기 내부에 채팅과 관계 경험치 저장
- 권한 허용 후 선택한 앱의 사용 시간 수동 조회
- 선택 앱 동의 후 1분 예약 알림·백그라운드 사용시간 조회 검증
- 캐릭터 9종, 액세서리 장착·저장·취소와 세로 다중 행 선택
- 데모 코인 상점, 구매 확인과 보유품 관리
- 별도 Firebase 구성과 전송 동의 후 Gemini 채팅, 실패·재시도 처리

에뮬레이터 및 실제 휴대폰의 기능별 검증 기록은 [진행 상태](docs/STATUS.md)와 [버전 기록](docs/releases/v0.5.0-ui.md)에 있습니다. 전체 기기 호환성 검증 완료를 뜻하지 않습니다.

## 실행 방법

1. Android Studio에서 저장소 루트 폴더를 엽니다.
2. Android SDK와 JDK 17 이상을 설정하고 Gradle을 동기화합니다.
3. Android 8.0(API 26) 이상 에뮬레이터 또는 휴대폰에서 `app`을 실행합니다.
4. 사용 시간을 확인할 때 기기 설정의 사용 정보 접근 권한을 허용합니다.

터미널 빌드: Windows `gradlew.bat assembleDebug`, macOS/Linux `./gradlew assembleDebug`. Gradle Wrapper가 배포본을 처음 다운로드하므로 네트워크가 필요합니다. SDK 경로는 Android Studio에서 설정하며 `local.properties`는 각 PC 전용입니다.

**VS Code / Android Studio:** VS Code와 Android Studio 모두 상위 수업 폴더가 아닌 `project` 저장소 폴더(이 README와 `settings.gradle`이 있는 폴더)를 엽니다. 에뮬레이터 실행은 Android Studio에서 Gradle 동기화 후 `app` 구성을 선택합니다. Gradle 다운로드 오류나 SDK 경로 오류는 아래 실행 환경 안내를 확인하세요.

- Windows의 `GRADLE_USER_HOME`이 별도 경로를 가리키면 그 경로에 Gradle 배포본이 필요합니다. 첫 다운로드가 막히면 네트워크와 프록시를 확인하세요.
- SDK가 없거나 잘못 지정되면 Android Studio의 SDK Manager에서 설치하고 프로젝트의 로컬 SDK 경로를 지정하세요. `local.properties`는 공유하지 않습니다.
- Firebase 로컬 구성 없이도 빌드할 수 있습니다. `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PfirebaseDisabled`로 공개 구성 빌드를 확인할 수 있습니다. 실제 AI 연결은 [Gemini 설정](docs/GEMINI_SETUP.md)을 따릅니다.

## 현재 저장소 구조

```text
appsai-android/
├─ app/src/main/
│  ├─ java/kr/example/haliltalk/MainActivity.java  # 현재 화면·저장·사용량 로직
│  ├─ res/layout/                                # 홈·채팅 및 공통 화면 XML
│  └─ AndroidManifest.xml                         # 앱 설정과 사용량 권한
├─ docs/
│  ├─ ARCHITECTURE.md  # 현재 구조, 목표 구조와 데이터 흐름
│  ├─ ROADMAP.md       # 개발 순서와 팀 작업
│  ├─ STATUS.md        # 실제 구현·검증 상태
│  └─ SOURCES.md       # 오픈소스·리소스 출처 기록
├─ scripts/verify_mvp.py  # 에뮬레이터 UI 검증, 실행 전 주의사항 확인
├─ .github/            # 이슈·PR 양식, CODEOWNERS, Android 빌드 작업
├─ CONTRIBUTING.md     # 3인 팀 협업과 관리자 확인 목록
├─ GUIDE.md            # 수업 요건·평가·제출 안내
├─ AGENTS.md           # 프로젝트 작업 지침
├─ gradle/ 및 gradlew  # Gradle Wrapper
└─ Gradle 설정 파일
```

홈·채팅의 틀과 데이터베이스 생성 코드를 분리했으며, 나머지 로직도 화면과 기능별 Java 패키지로 나눌 계획입니다. 목표 구조는 [설계 문서](docs/ARCHITECTURE.md)에 있으며 아직 구현된 폴더는 아닙니다.

## 협업 문서

- [팀 Notion 공간](https://app.notion.com/p/3e95d12a101880bd90f3da5a1df7250b?source=copy_link) (접근 및 내부 구성 확인 필요)
- [전체 프로젝트 설계안](docs/PROJECT_PLAN.md)
- [수업 기준 및 발표 체크리스트](GUIDE.md)
- [개발 계획과 완료 기준](docs/ROADMAP.md)
- [3인 팀 협업 가이드](CONTRIBUTING.md)
- [현재 상태와 미확인 사항](docs/STATUS.md)
- [오픈소스와 리소스 출처](docs/SOURCES.md)

팀원은 각자 실제 개발 작업을 진행하고 변경 이유와 테스트 결과를 기록합니다. API 키, 로컬 SDK 설정, 개인정보 및 빌드 산출물은 저장소에 올리지 않습니다.

**검증 스크립트 주의:** `scripts/verify_mvp.py`는 연결된 기기의 이 앱 데이터를 초기화합니다. 실제 사용자 데이터가 없는 테스트 전용 에뮬레이터에서만 실행하세요.
