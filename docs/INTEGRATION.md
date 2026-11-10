# 메인 앱 통합 작업 기준

기준: 2026-11-02. 이 문서는 현재 병렬 에이전트 작업의 파일 편집 경계다.
운영체제 파일 권한이나 GitHub CODEOWNERS 설정이 아니며, 사람 팀원 3명의
담당 합의를 대신하지 않는다. 사용자 요청에 따라 메인 제작은 이 세션이 맡는다.

## 2026-11-02 UI·캐릭터 설정 확장

사용자가 시안 반영과 앱 선택·캐릭터 설정 구현을 승인했다. 아래의
"이번 통합 단위"와 DB 버전 1 유지 제한은 이전 기능 통합의 기록이다.
이번에는 기존 friends/messages를 삭제하거나 교체하지 않고 DB 버전 2에서
appearance 테이블만 추가한다. 친구 생성과 외형·첫 메시지는 같은 트랜잭션이다.
캐릭터 ID와 모자·얼굴·손소품 ID를 분리해 저장하며 원본 제작 폴더는 수정하지 않는다.
앱의 Gradle 생성 에셋 단계에서 선정된 실제 PNG만 포함하고 CharacterCatalog에서
원본 경로를 관리한다. 디자인 계약은 루트 DESIGN.md다.
본체 Java/XML, character 패키지, FriendsDatabase, 본체 테스트가 이번 편집 범위다.
독립 검증 앱과 캐릭터 제작 소스, 저장소 담당의 파일은 변경하지 않는다.
구현·검증 완료 여부는 docs/STATUS.md의 후속 결과로 구분한다.

## 파일 담당 범위

| 작업 | 편집 범위 | 다른 작업에 전달할 결과 |
|---|---|---|
| 메인 제작 | `app/src/main/java/kr/example/haliltalk/MainActivity.java`, `app/src/main/res/layout/`, `app/src/main/res/values*/`, `app/src/androidTest/`, `app/build.gradle`, 이 문서 | 실행 가능한 본체와 통합 테스트 결과 |
| 검증 모듈 | `app-picker-probe/` | 검증한 동작, 기기/API/target, 재현 명령, 실패와 미검증 범위 |
| 캐릭터 시안·에셋 | `generated-images/`, `.senpi/html-pages/`의 시안, `캐릭터_에셋/` | 선택 원본과 64px PNG·레이어·목록·검증 결과. Android 리소스 편입은 본체 담당 |
| 저장소 관리 | `.github/`, `CONTRIBUTING.md`, `README.md`, 브랜치·PR·원격 설정 | 검토·공유·병합 상태 |

위 역할은 사용자 지시와 로컬 작업 기록에 근거한다. 다른 세션의 편집 경계
수락은 미확인이다. 세션 조회 RPC가 `host_unavailable`을 반환하여 직접 조율하지
못했다. 메인 제작은 다른 작업 범위를 수정하지 않는다.

- `settings.gradle`, 루트 Gradle, Manifest, DB 스키마는 공통 계약이다.
  이번 본체 작업에서는 변경하지 않는다.
- `docs/STATUS.md`는 각 작업의 결과를 별도 제목 아래 추가한다.
  기존 기록을 교체하지 않으며, 반영 직전 최신 내용을 확인한다.
- 공유 작업 폴더의 브랜치는 시작 시 `feature/app-picker-probe`였다.
  미커밋 본체·문서·이미지가 함께 있어 여기서 브랜치를 전환하거나 커밋하지 않는다.
  저장소 담당은 본체 변경을 검증 모듈 PR에 섞지 않고 별도 통합 단위로 다룬다.

## 검증에서 본체로 가져오는 계약

`app-picker-probe/README.md`, `ProbeActivity.java`, `PickerProbeTest.java`와
로컬 JUnit XML을 확인했다. 2026-11-02 Pixel 7 AVD, Android 17/API 37,
target 35에서 1건 통과, failures/errors/skipped 모두 0이다.

- `MAIN`/`LAUNCHER` 조회, 패키지 중복 제거, 실제 이름·아이콘 로딩,
  이름·패키지 검색, 항목 선택, 빈 검색과 초기화가 검증 대상이다.
- 실행 앱 선택은 계정 연결이나 사용량 권한 허용이 아니다.
  본체도 앱 선택·로컬 채팅을 사용량 권한과 분리한다.
- 전체 설치 앱·다른 프로필·Play 심사·실제 휴대폰은 검증되지 않았다.
- 회전 복원 코드는 있으나 기존 테스트는 검증하지 않았다.
- 캐릭터·친구 생성·SQLite·대화·사용량 조회·AI는 검증 대상이 아니다.
  본체에서 별도로 확인해야 한다.
- lint의 target 35, 구버전 백업 설정, overdraw 경고 3건은 기존 모듈의
  잔여 항목이며 본체 성공의 근거로 사용하지 않는다.

## 이번 통합 단위

홈 → 실행 앱 검색·선택 → 성격 선택 → 친구 저장 → 로컬 예시 대화 →
홈 복귀·재실행 복원까지 연결한다. 선택 목록은 검증 모듈에서 확인한
XML 행과 ListView 어댑터 방식을 본체 내부에 적용한다. 검증 앱에 의존하지 않는다.
기존 applicationId와 `friends.db` 버전 1·테이블을 유지한다.
사용량은 선택 친구의 수동 조회만 유지하며, 데이터가 없으면 0분으로 단정하지 않는다.
AI·자동 알림·시안의 제품 리소스 편입은 이번 통합 단위가 아니다.

실무 진행 순서는 작업 범위와 완료 조건 → 작은 기능 구현 → 본체 빌드 및
실행 검증 → 근거 기록 → 저장소 담당의 변경 검토·별도 PR → 팀 리뷰 후 통합이다.
독립 실험 성공을 제품 전체 성공으로 바꾸어 기록하지 않는다.

## 본체 검증 재현

```bat
gradlew.bat :app:assembleDebug :app:connectedDebugAndroidTest --offline
gradlew.bat :app:lintDebug --offline
```

`--offline`은 SDK와 Gradle 도구가 이미 캐시된 PC에서만 사용한다.
기기에는 친구로 추가하지 않은 실행 앱이 하나 이상 필요하다.
`MainFlowTest`는 실제 앱 목록에서 미등록 앱을 골라 친구와 대화를 만들고,
중복 선택·성격 선택 취소·빈 전송·검색 초기화·Activity 재생성과 다시 열기를
확인한다. 마지막에 테스트가 만든 친구/대화만 삭제하고 원래 행 수와
첫 실행 안내 설정을 복구한다. 기존 DB 전체를 초기화하지 않는다.

실제 기기 스크린샷은 앱 외부 전용 폴더의 `files/main-qa/`에 저장된다.
JUnit 결과는 `app/build/outputs/androidTest-results/connected/debug/`,
회수한 화면은 로컬 `app/build/main-qa/`에 보관한다.
기존 친구가 보이는 캡처는 공개 저장소에 포함하지 않는다.

작업 중 캐릭터 담당이 `캐릭터_에셋/README.md`와 픽셀 원본을 전달한 것을
확인했다. 제작 파일과 미리보기, Android 적용 전 조건을 구분하며 이 작업에서는
해당 파일을 수정하거나 앱에 복사하지 않았다. 앱 연결은 별도 통합 단위다.
동시에 여러 에뮬레이터가 실행될 수 있으므로 ADB 작업에는 확인한 기기의
`-s` 식별자를 지정한다. 다른 담당의 기기나 앱 데이터를 초기화하지 않는다.
