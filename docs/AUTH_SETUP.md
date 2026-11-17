# U4 이메일 인증과 선택형 클라우드 백업

## 현재 구현

Firebase 이메일/비밀번호 계정 생성·로그인과 명시적 게스트 진입을 제공한다. Firebase 설정이 없는 공개 빌드에서는 로그인 버튼을 비활성화하고 게스트 진입을 유지한다. Firebase SDK의 인증 세션만 신뢰하며 비밀번호를 자체 저장하지 않는다.

기존 게스트 데이터는 `friends.db`에 그대로 둔다. 로그인한 계정은 UID의 SHA-256 해시로 이름을 만든 별도의 `account_<hash>.db`를 사용한다. 계정 전환 때 진행 중 대화 요청과 DB 연결을 닫고 화면을 다시 열며, 로그아웃은 저장소를 삭제하지 않는다. AI 동의도 계정별 로컬 설정으로 분리한다. U5 목표 알림과 파일 내보내기는 이 U4 변경에 포함하지 않는다.

설정 화면에서 저장/복원을 직접 선택하고 확인해야 Firebase Firestore를 사용한다. 자동 업로드·자동 복원·게스트 데이터 이전은 없다. 서버 경로는 `users/{UID}/backups/current`로 계정당 최근 한 건만 유지한다. 저장은 트랜잭션으로 실행하여 오프라인 쓰기를 대기열에 쌓지 않으며, 복원은 서버에서 직접 읽는다. Firestore의 디스크 캐시는 사용하지 않는다.

백업 허용 필드: 친구 ID·패키지·이름·성격·목적, 현재 캐릭터와 소품, 목표 ID·친구 ID·제목·기한·완료·생성 시각. 대화·사용량·경험치·코인·구매/보유권·비밀번호·AI 동의·목표 공유 동의는 포함하지 않는다. 복원 후 XP와 목표 공유 동의는 0이며 기본 지갑·무료 소품은 현재 계정 DB의 기본값을 유지한다. 외형에 유료 소품이 있어도 구매 권한은 복원되지 않는다.

복원은 친구·목표·메시지·외형·진행 중 대화 요청이 모두 비어 있는 계정 DB에서만 허용한다. 필드·참조·중복·수치·외형을 전부 검증한 뒤 SQLite 트랜잭션으로 추가한다. 제한은 친구 100명, 목표 500개, UTF-8 400,000바이트, 목표 제목 120자이며 실패 시 일부만 복원하지 않는다. 클라우드 요청 도중 계정이 바뀌면 성공으로 처리하지 않는다.

## 실행 전 설정

1. Android 패키지와 일치하는 Firebase 프로젝트의 `app/google-services.json`을 로컬에만 둔다. 저장소에는 올리지 않는다.
2. Firebase Authentication의 Email/Password 제공자를 활성화하고 비밀번호 정책을 확인한다.
3. 같은 프로젝트에 Cloud Firestore 기본 데이터베이스를 만들고 `firestore.rules`의 본인 UID 한 건 규칙을 검토·게시한다. `firebase.json`은 규칙 파일의 로컬 연결이다. 규칙 파일을 GitHub에 올리는 것만으로 서버에 배포되지는 않는다.
4. 기존 Firebase App Check 설정을 사용하는 경우 해당 프로젝트의 디버그/운영 제공자를 확인한다. 키·토큰을 코드나 저장소에 넣지 않는다.

공개 구성 빌드: `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PfirebaseDisabled`. Firebase 설정 없이도 Auth/Firestore SDK 의존성은 해석해야 하므로 오프라인 빌드는 해당 아티팩트가 캐시에 있어야 한다.

## 검증 범위

`auth.AccountIsolationTest`는 별도 메모리 SQLite에서 파일명 분리, 백업 허용 목록, 복원 거절/보존, 입력 검증과 트랜잭션 롤백을 검사한다. `OnboardingFlowTest`는 명시적 게스트 진입·재진입·잘못된 로그인 입력을 검사한다. UI 계측은 실제 로그인 사용자가 없는 기기에서만 게스트 상태를 설정하고 기존 값을 되돌린다.

로컬 빌드·합성 계측은 실제 Firebase 계정 생성, Firestore 규칙 게시, 다른 기기 복원을 증명하지 않는다. 2026-11-05 통합 작업에서 합성 A/B 계정의 인증·본인 UID 규칙·서버 백업/복원과 정리를 확인한 이력은 있지만, 해당 별도 실연결 진단과 U5 연동은 이 U4 단독 커밋에 포함되지 않는다. 이번 분리 빌드와 실행 결과는 `docs/STATUS.md`에서 구분한다.

## 공식 참고

- https://firebase.google.com/docs/auth/android/password-auth
- https://firebase.google.com/docs/firestore/manage-data/transactions
- https://firebase.google.com/docs/firestore/manage-data/enable-offline
- https://firebase.google.com/docs/firestore/security/rules-conditions
- Firebase Android SDK: Apache 2.0, https://github.com/firebase/firebase-android-sdk
