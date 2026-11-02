# 앱사이 | AppSai

설치된 앱과 목표를 친구 캐릭터로 만들고, 대화하며 관계를 쌓는 Android 앱 프로젝트입니다.

> 현재는 기술 검증용 프로토타입입니다. AI 자유 대화, 자동 알림, 목표 기록 및 꾸미기는 아직 구현되지 않았습니다. AppSai는 작업용 영문 표기이며 상표 검토가 완료되지 않았습니다.

## 현재 기능

- 실행 앱 목록 표시, 앱 친구 추가 및 성격 선택
- 기기 내부에 채팅과 관계 경험치 저장
- 권한 허용 후 선택한 앱의 사용 시간 수동 조회
- 정해진 문장으로 예시 답장 제공

에뮬레이터에서 기존 프로토타입을 검증했습니다. 공통 XML 틀 전환 후 전체 흐름 회귀 검증과 실제 기기 검증은 남아 있습니다.

## 실행 방법

1. Android Studio에서 저장소 루트 폴더를 엽니다.
2. Android SDK와 JDK 17 이상을 설정하고 Gradle을 동기화합니다.
3. Android 8.0(API 26) 이상 에뮬레이터 또는 휴대폰에서 `app`을 실행합니다.
4. 사용 시간을 확인할 때 기기 설정의 사용 정보 접근 권한을 허용합니다.

터미널 빌드: Windows `gradlew.bat assembleDebug`, macOS/Linux `./gradlew assembleDebug`.

## 현재 저장소 구조

```text
appsai-android/
├─ app/src/main/
│  ├─ java/kr/example/haliltalk/MainActivity.java  # 현재 화면·저장·사용량 로직
│  ├─ res/layout/activity_main.xml                # 공통 화면 틀
│  └─ AndroidManifest.xml                         # 앱 설정과 사용량 권한
├─ docs/
│  ├─ ARCHITECTURE.md  # 현재 구조, 목표 구조와 데이터 흐름
│  ├─ ROADMAP.md       # 개발 순서와 팀 작업
│  ├─ STATUS.md        # 실제 구현·검증 상태
│  └─ SOURCES.md       # 오픈소스·리소스 출처 기록
├─ scripts/verify_mvp.py  # 에뮬레이터 UI 검증, 실행 전 주의사항 확인
├─ GUIDE.md            # 수업 요건·평가·제출 안내
├─ AGENTS.md           # 프로젝트 작업 지침
├─ gradle/ 및 gradlew  # Gradle Wrapper
└─ Gradle 설정 파일
```

현재 `MainActivity.java`에 로직이 모여 있어 화면별 XML과 기능별 Java 패키지로 나눌 계획입니다. 목표 구조는 [설계 문서](docs/ARCHITECTURE.md)에 있으며 아직 구현된 폴더는 아닙니다.

## 협업 문서

- [수업 기준 및 발표 체크리스트](GUIDE.md)
- [개발 계획과 완료 기준](docs/ROADMAP.md)
- [현재 상태와 미확인 사항](docs/STATUS.md)
- [오픈소스와 리소스 출처](docs/SOURCES.md)

팀원은 각자 실제 개발 작업을 진행하고 변경 이유와 테스트 결과를 기록합니다. API 키, 로컬 SDK 설정, 개인정보 및 빌드 산출물은 저장소에 올리지 않습니다.

**검증 스크립트 주의:** `scripts/verify_mvp.py`는 연결된 기기의 이 앱 데이터를 초기화합니다. 실제 사용자 데이터가 없는 테스트 전용 에뮬레이터에서만 실행하세요.
