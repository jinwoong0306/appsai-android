# project 파일 구조 정리 계획

기준: 2026-11-02. **계획 문서이며 폴더 이동이나 기능 구현은 아직 하지 않았다.** [현재 게시 구조](FILE_STRUCTURE.md)는 `main` 기준이고, 검증 모듈·본체 변경·캐릭터 파일은 다른 브랜치 또는 공유 로컬 작업에 있다. 이를 main의 완성 기능으로 표시하지 않는다.

## 정리 원칙

1. Android Studio의 Java/XML 네이티브 앱과 `:app` 모듈을 유지한다. `kr.example.haliltalk` 패키지·applicationId·기존 SQLite 파일명/버전은 재설계와 마이그레이션 검증 없이 바꾸지 않는다.
2. 실행 앱(`app/`), 격리 검증(`app-picker-probe/`), 승인된 제품 리소스, 디자인 참고, 문서·빌드 산출물을 구분한다. 검증 모듈을 제품 앱의 런타임 의존성으로 넣지 않는다.
3. 실제 기능을 분리하는 PR에서만 패키지·레이아웃을 만든다. 빈 `ui/`, `ai/`, `notification/` 디렉터리를 먼저 만들거나 전체 파일을 한 번에 옮기지 않는다.
4. 기존 친구·채팅·사용량 권한 흐름과 저장 데이터의 회귀가 구조 변경의 완료 조건이다. 팀원별 실제 담당은 합의된 GitHub Issue에서만 확정한다.

## 목표 배치 (구현할 때만 생성)

```text
project/
├─ app/                                      # 실제 배포·시연할 제품 앱
│  ├─ src/main/
│  │  ├─ java/kr/example/haliltalk/
│  │  │  ├─ MainActivity.java                # 화면 연결과 생명주기만 담당
│  │  │  ├─ ui/                              # 홈·앱 선택·채팅 화면 동작
│  │  │  ├─ data/                            # SQLite 접근과 트랜잭션
│  │  │  ├─ model/                           # 필요한 시점의 데이터/규칙 모델
│  │  │  ├─ usage/                           # 선택 앱 사용량·권한
│  │  │  ├─ notification/                    # 구현 시점의 알림 규칙
│  │  │  ├─ ai/                              # 실제 연동 시점의 대화 경계
│  │  │  └─ character/                       # 실제 적용 시점의 관계·장착 규칙
│  │  ├─ res/
│  │  │  ├─ layout/                          # 화면·목록 행 XML
│  │  │  ├─ values/                          # 문구·스타일·색
│  │  │  └─ drawable-nodpi/                  # 승인·변환된 제품용 픽셀 PNG만
│  │  └─ AndroidManifest.xml
│  └─ src/androidTest/                       # 실제 앱 흐름·저장 회귀
├─ app-picker-probe/                         # 별도 검증 앱; 필요성 검토 후 유지/정리
├─ 캐릭터_에셋/                              # 제작 원본 후보; 공개 범위 결정 전 로컬
├─ docs/                                     # 설계·계획·상태·출처 (현재는 평면 구조 유지)
├─ scripts/                                  # 검증 스크립트, 데이터 초기화 주의
├─ .github/                                  # 이슈·PR·빌드 워크플로
└─ Gradle Wrapper 및 루트 설정
```

이 트리는 **폴더 생성 지시가 아닌 도착 형태**다. `data/FriendsDatabase.java`, `screen_home.xml`, `screen_apps.xml`, `screen_chat.xml`, `row_app.xml`, `MainFlowTest.java`는 공유 로컬 작업에서 이미 확인됐지만 main에는 없다. 이 파일은 정상적인 본체 통합·검증 후에만 해당 위치가 현재 구조가 된다. 캐릭터 원본을 `app/res/`에 통째로 넣지 않는다.

## 단계별 변경 단위

| 순서 | 작업 단위 | 완료·검증 기준 |
|---|---|---|
| 0 | 문서로 main/feature/로컬 차이를 먼저 명시 | 실제 Git 추적 파일과 문서 경로가 일치하고 코드·에셋은 커밋하지 않음 |
| 1 | 기존 로컬 본체 통합 변경을 **별도 PR**로 정리: `MainActivity`, `data/`, `res/layout/`, `res/values*/`, `src/androidTest/`, 필요한 Gradle·Manifest 변경 | 실제 앱 빌드, 앱 선택→친구→대화→재시작 복원, 권한 거부/허용, 기존 데이터 보존 확인. 실패한 lint는 억제하지 않고 원인·상태 기록 |
| 2 | 화면마다 독립적인 Java 코드를 `ui/`로 분리하고 XML·어댑터를 연결 | 화면 이동·검색·입력 초안·친구 중복 방지·채팅 회귀; 한 화면씩 PR |
| 3 | 사용량 조회·권한 동작을 `usage/`로, 저장 코드를 `data/`로 구분 | 선택한 앱만 조회, 권한 철회/데이터 없음/재시작 검증. DB 스키마가 바뀌면 마이그레이션 테스트 |
| 4 | 캐릭터 원본·라이선스와 공개 범위를 확인하고 필요한 64px PNG만 영문 소문자 이름으로 `drawable-nodpi/`에 편입 | 파일별 출처, 실제 기기 렌더링, 장착/복원 테스트. 참고 이미지·확대 미리보기는 제품 리소스와 분리 |
| 5 | 알림·AI·목표/관계 기능이 실제 구현될 때 각 패키지 추가 | 알림 중복/권한, AI 키 미노출·전송 범위, 사용자 확인 기반 목표 처리 등 기능별 검증 |
| 6 | 문서·README 구조와 상태를 실제 병합 결과에 맞춰 갱신 | `FILE_STRUCTURE.md`와 `README.md`가 main의 `git ls-files`와 일치, 팀원 실제 커밋/PR과 검증 근거 반영 |

## 게시·협업 경계

- `main`에는 문서만 먼저 게시한다. 현재 `feature/app-picker-probe`의 검증 앱과 공유 작업 폴더의 본체·에셋을 이 문서 커밋에 섞지 않는다.
- 독립 검증 모듈의 성공은 본체 기능 완료가 아니다. 제품 본체의 통합 테스트를 통과하고 팀 리뷰 후 별도 PR로 반영한다.
- `generated-images/`와 `캐릭터_에셋/`의 공개·이동은 파일별 출처, 저장 용량, 앱 사용 여부를 확인한 뒤 정한다. 원본 PNG와 생성 스크립트의 상대경로를 함께 검증하기 전에는 이동하지 않는다.
- `docs/`는 현재 규모에서 세부 폴더로 쪼개지 않는다. 문서 수나 담당 경계가 실제로 늘 때만 재분류한다.
- `local.properties`, 비밀 키·토큰, 개인 사용량/대화/DB, 빌드 폴더·APK·테스트 캡처는 업로드하지 않는다. 다른 작업의 미커밋 변경을 정리 목적으로 되돌리거나 함께 커밋하지 않는다.

변경 후 기본 확인 명령은 `gradlew.bat :app:assembleDebug :app:connectedDebugAndroidTest --offline`과 `gradlew.bat :app:lintDebug --offline`(캐시가 준비된 PC 기준)이다. 현재 로컬 기록의 본체 lint에는 기존 `PACKAGE_USAGE_STATS` 관련 오류가 남아 있으므로, 구조 변경만으로 통과했다고 주장하지 않는다. 데이터 초기화 가능성이 있는 `scripts/verify_mvp.py`는 개인 데이터가 없는 테스트 전용 에뮬레이터에서만 실행한다.
