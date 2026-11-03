# 앱사이 프로젝트 전체 설계안

> 작성 기준: 2026-11-02. 팀 논의를 위한 제안이며, 구현 완료나 팀원별 담당 확정을 뜻하지 않는다. 현재 상태는 [STATUS.md](STATUS.md), 수업 요건은 [GUIDE.md](../GUIDE.md)를 기준으로 한다.

## 1. 무엇을 만드는가

설치된 앱을 친구 캐릭터로 추가하고 채팅하면서 사용 습관을 돌아보는 Android 앱. 사용자가 유튜브를 선택하고 성격을 고르면 대화방이 만들어진다. 사용 시간 기준에 도달하면 친구가 말을 걸고, 사용자가 “강의 봤어”라고 직접 알려 주면 그 맥락을 대화에 반영한다. 시스템은 사용 시간만으로 강의·쇼츠 여부를 알 수 없다.

**사용 흐름:** 첫 실행 안내 → 실행 가능한 앱 목록 → 앱 선택 → 성격 선택(기본값 제공) → 친구 목록과 채팅 → 사용량 권한 안내(필요할 때) → 선택 앱의 사용 시간 확인 → 조건에 맞으면 알림 → 대화와 관계 기록. 권한을 거절해도 사용량 기반 기능을 제외한 앱은 이용할 수 있도록 설계한다.

## 2. 수업과 개발 도구

| 구분 | 선택·제안 | 용도 |
|---|---|---|
| 앱 개발 | Android Studio, Java, XML | 화면과 앱의 핵심 동작. Kotlin·Flutter·Unity·웹 하이브리드 사용 안 함 |
| 실행 확인 | Android 에뮬레이터 + 실제 Android 기기 | 기기별 권한·사용량·알림 검증 |
| 버전 관리 | Git, GitHub Issues·PR·Actions | 3명 협업, 변경 기록, 자동 빌드 |
| 기기 데이터 | Android SQLite | 친구·대화·관계와 설정을 기기 안에 저장 |
| 앱 목록 | PackageManager 및 실행 앱 쿼리 | 표시 가능한 실행 앱 목록 제공. 모든 설치 패키지를 보장하지 않음 |
| 사용 시간 | UsageStatsManager | 별도 사용 정보 접근 권한을 받은 후 선택 앱 조회 |
| 알림 | Notification + WorkManager 후보 | 사용자가 설정한 조건을 주기적으로 확인. 실제 시각은 시스템이 조정 |
| AI | Firebase AI Logic Java SDK + Gemini Developer API + Gemini 3.1 Flash-Lite | 외부 AI와 캐릭터 대화. 실제 프로젝트·비용·권한 설정과 검증 필요 |
| 디자인 | XML 및 직접 제작·라이선스 확인한 2D 도트 이미지 | 기존 Android 화면 위에 캐릭터·소품 표시. 제작 도구는 팀에서 선택 |

현재 개발환경은 JDK 17, minSdk 26, compileSdk 36, targetSdk 35이며 변경 시 빌드와 호환성을 확인한다.

## 3. 현재 구현과 개발 범위

| 기능 | 현재 상태 | 다음 목표 |
|---|---|---|
| 앱 친구 | 실행 앱 목록, 친구 추가·성격 선택 | 목록 검색, 화면별 XML 전환, 삭제·예외 처리 |
| 대화 | SQLite 저장과 정해진 예시 답장 | 비동기 AI 자유 대화, 전송 상태·재시도 |
| 사용량 | 권한 허용 후 수동 조회 | 사용자 선택 기준과 자동 확인, 권한 철회 대응 |
| 관계 | 로컬 관계 경험치 저장 | 누적 점수·단계 및 명확한 증감 규칙 설계 |
| 알림 | 미구현 | 지연·중복 방지와 알림 권한 처리 |
| 꾸미기·재화 | 미구현 | 기본 도트 캐릭터와 로컬 장착부터 검토 |
| 계정·서버·결제·랭킹 | 미구현 | 학기 핵심 범위 밖의 장래 검토. 로컬 점수는 공정한 랭킹·결제를 보장하지 않음 |

기존 프로토타입은 공통 XML 틀 외의 화면을 Java로 동적 생성한다. 전체 회귀 및 실제 기기 검증도 남아 있다. 앱 표시명은 현재 코드에서 `할 일톡 MVP`이며 앱사이로의 변경은 별도 작업이다. 패키지 `kr.example.haliltalk` 변경은 데이터·설치 호환성을 검토한 뒤 별도로 결정한다.

## 4. 전체 구성과 데이터 흐름

```text
Android 앱 (Java + XML)
 ├─ 화면: 안내 / 친구 목록 / 앱 선택 / 성격 / 채팅 / 설정 / 꾸미기
 ├─ 기기 기능: 앱 목록 · 사용 정보 접근 · 사용 시간 · 백그라운드 확인 · 알림
 ├─ 앱 규칙: 조건 확인 · 알림 중복 방지 · 사용자 확인 · 관계 점수
 ├─ SQLite: 친구 · 메시지 · 사용량 설정 · 관계 · 장착 상태
 └─ AI 대화 인터페이스
         │ 최근 대화 일부 + 선택한 성격 + 사용자가 허용한 최소 맥락
         ▼
   Firebase AI Logic → Gemini 모델 → 응답 → SQLite 저장 → 화면 표시
```

AI 요청은 비동기로 실행한다. 성공하면 친구 답장을 저장하고, 실패하면 원래 사용자 메시지는 보존한 채 실패 표시와 재시도를 제공한다. 중복 요청과 화면 회전·앱 종료를 고려해 메시지별 상태 및 요청 ID를 설계한다. 관계·재화는 AI의 출력 텍스트가 직접 수정하지 않고 앱의 규칙과 필요한 사용자 확인으로만 변경한다.

## 5. AI 연결 결정: Gemini 3.1 Flash-Lite (미구현)

선택: **Gemini Developer API의 `gemini-3.1-flash-lite`**를 Firebase AI Logic Java SDK로 호출한다. 현재 프로젝트에는 아직 연결하지 않았다. Android Java 공식 지원, Firebase의 App Check와 호출량 관리, 간단한 캐릭터 텍스트 대화에 맞는 경량 모델, 결제 계정 없이 제한된 무료 테스트가 가능한 점을 고려했다. 유료 등급 전환과 실제 출시 결정은 별도다. 모델 품질·한국어 캐릭터 유지·속도는 실제 테스트로 확인한다.

직접 Gemini REST를 APK에서 호출하면 서버 비밀 키 취급이 어려우며 별도 서버를 만들면 인증·배포·운영 부담이 늘어난다. 다른 API나 오픈소스 모델도 가능하지만 각자의 백엔드·키 관리나 호스팅 검증이 필요하므로 학기 MVP에는 이 구성을 선택한다. 무료가 무제한이거나 다른 모델보다 대화 품질이 우수하다고 가정하지 않는다.

**개인정보:** Gemini Developer API 무료 서비스는 전송한 입력과 응답이 Google 제품 개선에 사용될 수 있다. 실제 대화·실명·사용량 등 민감한 데이터로 무료 테스트하지 않고 가상 데이터만 사용한다. 외부 사용자 대상 배포 전 적용 약관·개인정보 처리와 동의, 유료 방식의 조건을 별도 검토한다.

1. 팀이 사용할 Firebase 프로젝트와 권한을 정하고 Android 앱을 등록한다. 현재 applicationId를 사용하며 이후 변경 시 등록도 조정한다.
2. Firebase AI Logic Java SDK를 적용하고 Gemini Developer API와 `gemini-3.1-flash-lite`를 선택한다. Java에서 필요한 비동기 의존성은 공식 가이드를 확인한다.
3. Firebase App Check를 설정한다. 개발 환경은 디버그 공급자와 등록된 테스트 토큰을 사용하고, 배포는 Play Integrity 등 실제 기기에 맞는 공급자를 검증한다. 디버그 토큰은 외부 공유·커밋하지 않는다. App Check는 남용 위험을 낮추지만 비용·호출 남용을 완전히 막지는 않는다.
4. 친구 성격, 최근 메시지 일부와 사용자가 제공한 맥락을 조합한다. 사용량 전체나 다른 앱 목록을 보내지 않는다. 사용 시간이 필요할 때만 선택된 요약 정보를 고지 후 포함한다. 자유 대화도 외부 AI에 전송된다고 안내하고 동의를 받는다. 비밀번호·개인정보를 입력하지 않도록 안내한다.
5. 응답 성공·오프라인·타임아웃·할당량 초과·안전 차단을 처리하고, 오류 시 고정 문구는 AI 응답으로 가장하지 않는다.
6. AI 공급자의 데이터 처리·보관 정책, 과금 여부·무료 한도, 지역, 모델 가용성을 실제 설정 시 확인한다. 예산 경고와 호출 제한을 설정하고 무료가 무제한이라고 가정하지 않는다.

Firebase 설정 파일은 제품 설정값과 비밀 키를 구분하고 공식 지침 및 팀 보안 정책을 따른다. Gemini Developer API 비밀 키, 서비스 계정 키, 디버그 토큰을 Git·APK에 직접 넣지 않는다. 다른 공급자로 전환할 수 있도록 `AiChatService` 인터페이스와 Firebase 구현체를 분리한다. 장래 결제·사용자별 유료 한도는 신뢰 가능한 인증·서버 검증이 필요하며 현재 구조에선 보장하지 않는다.

## 6. 로컬 데이터 설계 초안

| 데이터 | 예시 필드 | 규칙 |
|---|---|---|
| Friend | id, packageName, displayName, personality, createdAt | 패키지 중복 처리. 앱 삭제 시에도 대화 기록 정책 결정 |
| Message | id, friendId, sender, text, createdAt, status, requestId | 전송 중/성공/실패. 외래키·삭제 및 재시도 정책 정의 |
| UsageRule | friendId, thresholdMinutes, enabled, lastNotifiedDay | 권한 없으면 조회 중지. 시간대 변경·자정 처리 검증 |
| Relationship | friendId, totalPoints, updatedAt | 누적 점수. 증감 트리거와 상한·중복 방지 규칙 미확정 |
| Item/EquippedItem | itemId, friendId, equipped | 꾸미기 구현 시 추가. 구매·랭킹을 위한 신뢰 저장소는 아님 |

테이블과 마이그레이션은 구현 전에 기존 SQLite 구조를 확인하고 설계한다. 현재 대화는 로컬에 저장되며 기기 삭제·앱 삭제 시 손실될 수 있다. 백업·내보내기와 사용자 데이터 삭제는 별도 기능으로 설계한다. 교재 파일 처리 요건을 위해 JSON 내보내기/가져오기를 후보로 두되 개인정보와 잘못된 파일 처리를 검증한다.

## 7. 저장소 구조

현재 저장소는 [README의 실제 구조](../README.md#현재-저장소-구조)를 참고한다. 아래는 리팩터링 후의 **목표 예시**로, 빈 폴더를 미리 만들거나 현재 구현으로 발표하지 않는다.

```text
appsai-android/
├─ app/src/main/
│  ├─ java/kr/example/haliltalk/
│  │  ├─ ui/             # 화면·목록 어댑터
│  │  ├─ data/           # SQLite와 저장소 접근
│  │  ├─ model/          # 친구·메시지·규칙
│  │  ├─ usage/          # 권한·사용량 조회
│  │  ├─ notification/   # 주기 작업과 알림
│  │  ├─ ai/             # AI 인터페이스·Firebase 구현
│  │  └─ character/      # 관계·표정·꾸미기 규칙
│  ├─ res/layout/        # 화면·목록 항목 XML
│  ├─ res/drawable/      # 도트 캐릭터·소품
│  ├─ res/values/        # 문자열·색상·테마
│  └─ AndroidManifest.xml
├─ app/src/test/         # 필요한 규칙 단위 테스트 (추가 예정)
├─ app/src/androidTest/  # 기기 연동 테스트 (필요 시)
├─ docs/                 # 설계·진행 상태·출처
├─ .github/              # PR·이슈 템플릿, CODEOWNERS, CI
├─ scripts/              # 개발·검증 보조 도구
├─ README.md · CONTRIBUTING.md · GUIDE.md · AGENTS.md
└─ Gradle 설정 및 Wrapper
```

실제 변경은 기능별로 분리하고 기존 저장 데이터 및 앱 동작을 유지하며 진행한다. API 키, 로컬 SDK 경로, 기기 데이터, 빌드 결과물은 커밋하지 않는다.

## 8. 3명 협업과 작업 분할

진웅(`@jinwoong0306`)이 최종 관리자이며 `@Creepc`, `@jaezero`와 함께 개발한다. 다음은 **담당 미확정**인 작업 묶음이다. 세 명 모두 실제 Java/XML 개발을 수행하고, 팀 협의 후 이슈에 각 책임자를 배정한다.

- 화면·캐릭터: 친구 목록과 추가·채팅 XML, 어댑터, 도트 이미지.
- 기기 기능: 앱 목록·사용량 권한과 조회·알림.
- 채팅·데이터: SQLite 분리, 대화 상태, AI 인터페이스·연결.

현재는 하나의 MainActivity에 기능이 모여 충돌 가능성이 높다. 먼저 공통 구조와 인터페이스를 합의하고 변경을 작게 분리한다. 이슈 → `feature/이슈번호-기능명` 브랜치 → 실제 개발 커밋 → PR 및 빌드 → 다른 팀원 리뷰 → 진웅 최종 확인·병합 순서로 진행한다. 상세 규칙은 [CONTRIBUTING.md](../CONTRIBUTING.md). 브랜치 보호는 GitHub 설정으로 별도 적용해야 강제된다.

## 9. 단계별 실행과 검증

1. **발표 준비:** 실제 화면과 목업, 수동 사용량과 미구현 자동 알림, 예시 답장과 미구현 AI를 구분. 중간 발표 및 제출 공지는 [GUIDE.md](../GUIDE.md), e-class 최신 공지 확인.
2. **구조 분리:** 홈·선택·채팅 XML 및 데이터/사용량 로직을 분리하고 기존 친구 추가·재시작 유지·조회 회귀 확인.
3. **AI 최소 검증:** Firebase 프로젝트와 App Check 설정 후 테스트 캐릭터와 1회 대화 성공, 실패·재시도·전송 데이터·비용 한도를 확인.
4. **사용량·알림:** 선택 앱 권한 거부/철회, 기준 도달, 알림 허용/거부, 중복·재부팅·지연을 에뮬레이터와 실제 기기에서 시험.
5. **관계·꾸미기:** 점수 규칙을 팀 합의 후 구현하고 중복 적립과 재시작 복원 검증. 기본 캐릭터부터 적용.
6. **통합·발표:** 실제 시연 시나리오와 실패 시 대체 경로, 팀원 기여 및 출처 정리. 결제·랭킹은 미래 확장으로 표시.

우선순위와 실제 진척은 [ROADMAP.md](ROADMAP.md), [STATUS.md](STATUS.md)에 반영한다. 정확한 담당자, 예산, 실제 모델 접근 및 관계 증감과 알림 기준은 팀 논의가 필요하다.

## 공식 참고

- [Firebase AI Logic 시작하기: Android Java](https://firebase.google.com/docs/ai-logic/get-started)
- [Firebase AI Logic과 App Check](https://firebase.google.com/docs/ai-logic/app-check)
- [Firebase AI Logic 가격과 결제](https://firebase.google.com/docs/ai-logic/pricing?api=dev)
- [Gemini 모델별 가격과 무료 서비스 데이터 처리](https://ai.google.dev/gemini-api/docs/pricing)
- [Firebase 지원 모델](https://firebase.google.com/docs/ai-logic/models)
- [Android 앱 가시성](https://developer.android.com/training/package-visibility)
- [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)
- [WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent)
