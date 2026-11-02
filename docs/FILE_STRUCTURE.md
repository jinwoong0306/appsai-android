# 저장소 파일 구조

기준: 2026-11-02, GitHub `main`의 `36e6720` 커밋과 이번 문서 변경. 아래 트리는 **문서 게시 후 main**의 구조다. 다른 브랜치와 로컬 작업은 main에 올라온 파일처럼 취급하지 않는다.

## GitHub main: 문서 게시 후 파일

```text
project/ (GitHub: appsai-android)
├─ app/                         # 실제 실행하는 Android 앱
│  ├─ build.gradle
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/kr/example/haliltalk/MainActivity.java
│     └─ res/layout/activity_main.xml
├─ docs/
│  ├─ ARCHITECTURE.md           # 현재 구조와 장기 목표
│  ├─ FILE_STRUCTURE.md         # main과 다른 작업의 파일 위치
│  ├─ PROJECT_STRUCTURE_PLAN.md # 실제 재배치 순서와 검증 조건
│  ├─ ROADMAP.md                # 기능 개발 순서와 완료 기준
│  ├─ SOURCES.md                # 외부 리소스의 출처
│  └─ STATUS.md                 # 실제 확인한 구현·검증 상태
├─ scripts/verify_mvp.py        # 테스트 전용 에뮬레이터 검증
├─ .github/
│  ├─ CODEOWNERS
│  ├─ ISSUE_TEMPLATE/           # 버그·기능 이슈 양식
│  ├─ pull_request_template.md
│  └─ workflows/android-build.yml
├─ gradle/wrapper/              # Wrapper 실행 파일과 버전 설정
├─ gradlew, gradlew.bat
├─ build.gradle, settings.gradle, gradle.properties
├─ README.md                    # 시작점과 실행법
├─ GUIDE.md                     # 수업 필수 조건·평가·발표
├─ CONTRIBUTING.md              # 팀 협업·리뷰 절차
├─ AGENTS.md                    # 작업 지침
└─ .gitignore
```

현재 main의 `settings.gradle`에는 `:app`만 포함된다. `MainActivity.java`는 화면·저장·앱 목록·사용량 로직이 모인 기존 구조이고, `activity_main.xml`은 공통 화면 틀이다. `docs/ARCHITECTURE.md`의 여러 패키지는 **계획**이지 main에 존재하는 디렉터리가 아니다.

## main과 다른 작업 구분

| 위치 | 확인한 내용 | GitHub main 반영 여부 |
|---|---|---|
| `feature/app-picker-probe` (`fefe3ff`) | 독립 `app-picker-probe/` 모듈: Java/XML 화면, 앱 조회·검색·선택, Android 테스트 및 README | 아직 아님 |
| 현재 공유 로컬 작업 폴더 | 본체의 `data/FriendsDatabase.java`, 화면별 XML·문자열·테마, 본체 Android 테스트 등 미커밋 변경 | 아직 아님 |
| 현재 공유 로컬 작업 폴더 | `docs/INTEGRATION.md`, `docs/PROJECT_PLAN.md`, `docs/TEAM_SCHEDULE.md` 등 미커밋 문서 | 아직 아님 |
| 현재 공유 로컬 작업 폴더 | `캐릭터_에셋/`, `generated-images/`의 제작·참고 파일 | 아직 아님 |

위의 로컬 파일은 존재하더라도 **현재 공개된 제품 코드나 GitHub main의 구조가 아니다.** 공유 폴더의 작업은 담당자별 검증·리뷰 후 별도 변경 단위로 반영한다. 원본 파일과 빌드 결과를 한꺼번에 업로드하지 않는다.

## 파일을 찾는 기준

| 찾는 것 | 현재 위치 / 기준 |
|---|---|
| 실행 앱·앱 권한 | `app/src/main/`, `app/src/main/AndroidManifest.xml` |
| 빌드 설정·실행 | 루트 `settings.gradle`, `build.gradle`, `gradle/`, `gradlew*` |
| 개발 이슈·리뷰·CI | `.github/`, GitHub Issues·Pull requests |
| 구현 여부와 시험 근거 | `docs/STATUS.md` (미검증을 완료로 표시하지 않음) |
| 계획된 기능 순서 | `docs/ROADMAP.md` |
| 소스·이미지 라이선스 | `docs/SOURCES.md` |
| 수업 요구·제출 | `GUIDE.md`; 후속 공지는 e-class 원문 확인 |

`local.properties`, API 키·토큰, 앱 사용 기록·대화·SQLite 원본, `build/`·APK·임시 캡처는 공개 저장소에 올리지 않는다. `scripts/verify_mvp.py`는 연결된 앱 데이터를 초기화하므로 **데이터가 없는 테스트 전용 에뮬레이터에서만** 실행한다.

실제 폴더 재배치와 반영 순서는 [프로젝트 구조 정리 계획](PROJECT_STRUCTURE_PLAN.md)을 따른다. 이 문서의 트리는 새 구조를 이미 구현했다는 뜻이 아니다.
