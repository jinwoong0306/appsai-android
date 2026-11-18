# U5 목표 알림·파일 전송 인계

2026-11-18 U4 `main` 위에 U5만 분리한 구현 기준이다. Manifest·설정·목표
화면·계정 전환과 연결했으며 이번 빌드·계측 결과는 `../STATUS.md`를 따른다.

## 추가 파일

- `app/src/main/java/kr/example/haliltalk/goals/GoalNotifications.java`
- `app/src/main/java/kr/example/haliltalk/goals/GoalReminderReceiver.java`
- `app/src/main/java/kr/example/haliltalk/goals/GoalBootReceiver.java`
- `app/src/main/java/kr/example/haliltalk/data/GoalBackup.java`
- `app/src/main/java/kr/example/haliltalk/TransferActivity.java`
- `app/src/main/res/layout/screen_transfer.xml`
- `app/src/main/res/values/transfer_strings.xml`
- `app/src/androidTest/java/kr/example/haliltalk/data/GoalBackupTest.java`
- `app/src/androidTest/java/kr/example/haliltalk/goals/GoalNotificationsTest.java`

## 통합 지점

1. 설정에서 `new Intent(this, TransferActivity.class)`로 연다.
2. `TransferActivity`, `GoalsActivity`는 `exported=false`로 등록한다.
   `goals.GoalReminderReceiver`는 `exported=false`로 등록한다.
   `goals.GoalBootReceiver`는 `BOOT_COMPLETED`, `TIME_SET`,
   `TIMEZONE_CHANGED`, 선택적으로 `MY_PACKAGE_REPLACED`를 등록하고
   `RECEIVE_BOOT_COMPLETED`를 선언한다. 직접 부팅 모드는 사용하지 않는다.
   위 필수 Manifest 등록은 통합본에 포함했다.
3. 목표 생성·수정·완료·삭제 후와 목표 화면 복귀 시
   `GoalNotifications.reschedule(context)`를 호출한다.
4. 계정 전환 **전** `GoalNotifications.cancelAll(context)`를 호출하고,
   새 계정/게스트 진입 후 `reschedule(context)`를 호출한다.
   `AccountSession.beforeTransition()`과 `enterGuest()`의 현재 통합 코드를 확인했다.
5. 목표 화면 알림 상태에는 `GoalNotifications.enabled(Context)`를 사용한다.
   이 함수는 목표 채널 `goal_reminders`의 차단도 확인한다.
6. 알림 탭은 `GoalsActivity`에 `friend_id` long과 `goal_account` String을 전달한다.
   `goal_account`가 있으면 `AccountSession.databaseName(this)`와 같은지 먼저 확인하고,
   다르면 친구 ID를 사용하지 않는다. 알림 게시 직후 계정을 바꾸는 경합에서도
   같은 로컬 친구 ID가 다른 계정의 친구를 열지 않게 하는 진입 경계다.
7. `AccountSession.databaseName(Context)`, `isEntered(Context)`,
   `generation()`을 사용한다. `FriendsDatabase` 생성자는 UI 스레드에서
   계정별 DB 이름을 고정하고, 백그라운드 콜백은 그 헬퍼만 사용한다.
   현재 v8의 목표 제목 CHECK 제약이 120자이므로 백업도 최대 120 UTF-16 코드 단위다.

## 알림 동작

`AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, ...)`의 단발 **비정확** 알람이다.
정확한 알람 권한·사용량 접근·저장소 권한을 새로 요구하지 않는다.
OS 절전과 알람 제한으로 기한보다 늦게 도착할 수 있다.

예약 identity에는 계정별 DB 파일명, 목표 ID, 기한, 예약 run UUID를 넣는다.
목표 내용과 UID 원문은 identity나 로그에 넣지 않는다.
수신기는 보관된 미소비 identity, 현재 진입 상태와 계정, DB의 미완료 여부,
동일 기한 및 친구 존재를 다시 확인한다. 알림 전 다시 계정을 확인한다.
목표/보상/대화 DB를 수정하지 않는다.

일반 `reschedule()`은 표시 중인 유효 알림을 지우지 않는다.
아직 전달되지 않은 **기한 초과 pending**은 같은 identity로 보존해 현재 시각으로
다시 예약한다. 따라서 기한 직후 화면을 열거나 기한을 지나 재부팅해도
예약이 있었다면 알림이 사라지지 않는다.
처음 보는 과거 목표(예: 파일에서 가져온 목표)는 새로 예약하지 않는다.
완료·삭제·기한 변경·다른 계정의 pending/표시 알림만 정리한다.
동시에 등록하는 목표 알람은 가장 빠른 64개로 제한한다. 미래 기한을
이미 관찰한 나머지 목표는 별도 대기 키로 보관하고, 알림이 소비될 때
다음 목표를 예약한다. 대기 중 기한이 지나도 누락하지 않으며 계정 전환
시 pending과 대기 키를 취소한다. 소비 표식은 유지한다.

수신 시 pending 제거와 `(account, goal, due)` consumed 표식을 한 번에 저장하므로
중복 브로드캐스트와 시계가 뒤로 바뀐 경우에도 다시 알리지 않는다.
이는 중복 방지를 우선한 at-most-once 경계다. 표식 저장 직후 프로세스가
종료되면 표시가 누락될 수 있으며 정확히 한 번 전달을 보장하지 않는다.
권한이나 채널이 차단된 경우도 알림 없이 소비하며 수신기에서 권한을 요청하지 않는다.
`cancelAll()`은 pending과 표시 알림을 취소하지만 consumed 표식은 보존한다.
로그아웃 상태에서 `reschedule()`하면 취소만 하고 게스트 DB 알림을 새로 만들지 않는다.

## JSON 계약과 공개 API

```json
{
  "version": 1,
  "goals": [
    {
      "package": "com.example.app",
      "title": "오늘의 목표",
      "due_at": 1793876400000,
      "completed": false,
      "created": 1793872800000
    }
  ]
}
```

목표와 친구의 앱 패키지 식별자만 내보낸다. 친구 이름·성격·목적·로컬 친구/목표 ID,
계정 ID, 메시지, 코인, XP, 설정, 키, 토큰은 포함하지 않는다.

```java
byte[] GoalBackup.exportData(SQLiteDatabase database) throws IOException
GoalBackup.Document GoalBackup.read(InputStream input) throws IOException
GoalBackup.Preview GoalBackup.preview(SQLiteDatabase database, Document document) throws IOException
int GoalBackup.importConfirmed(SQLiteDatabase database, Preview confirmed) throws IOException
int GoalBackup.importConfirmed(SQLiteDatabase database, Preview confirmed,
        BooleanSupplier stillCurrent) throws IOException
```

`Preview` 공개 필드는 `total`, `additions`, `unknownFriends`, `alreadyPresent`다.
`read()`의 입력 스트림 소유권은 호출자에게 있다.
화면은 모든 입출력 스트림과 DB 헬퍼를 try-with-resources로 닫는다.

- 전체 UTF-8 파일 최대 1 MiB, 목표 최대 1,000개, 제목 최대 120 코드 단위.
- 제목은 비어 있거나 제어 문자·짝이 없는 surrogate를 포함할 수 없다.
- 패키지는 최대 255자의 Java 스타일 패키지 식별자다. 한 세그먼트인
  Android 시스템 패키지도 허용한다.
- 날짜는 정수 epoch ms, `0..253402300799999`다. 기한만 null을 허용한다.
  과거 기한과 완료 목표는 유효하지만 가져오기로 보상을 지급하지 않는다.
- 문자열로 위장한 숫자/boolean, 소수·음수·지수 숫자, 필수 값 누락,
  알려지지 않은 필드, 중복 필드, 잘못된 UTF-8, 뒤에 붙인 다른 문서를 거부한다.
- 파일 안의 동일한 `(package,title,due_at,completed,created)`는 전체 거부한다.
  DB에 이미 있는 동일 목표는 건너뛴다. 그 외는 추가만 하며 기존 행을 덮어쓰지 않는다.
- 패키지가 없는 친구는 미리보기 개수에 포함해 건너뛴다. 친구를 자동 생성하지 않는다.
  하나의 패키지에 여러 친구가 연결된 비정상 DB는 임의 매핑하지 않고 전체 거부한다.
- 파싱·값 검증은 트랜잭션 전에 끝낸다. 사용자가 네 개의 개수를 확인한 뒤에만
  쓰기를 시작한다. 트랜잭션 안에서 대상/중복/개수가 그대로인지 다시 검사하고,
  변했으면 새 확인을 요구한다. 삽입 실패나 수명주기/계정 guard 무효화는 전체 롤백한다.
- 로컬 DB 자체에 완전히 동일한 목표가 중복돼 있으면 내보내기도 중복 오류로 거부한다.
  데이터를 조용히 생략하거나 임의로 합치지 않는다.

`TransferActivity`는 SAF `ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT`와
`CATEGORY_OPENABLE`, `application/json`을 사용한다.
IO는 단일 작업 스레드에서 실행하고 회전 시 작업·확인 미리보기를 보존한다.
화면 종료/계정 전환 시 기존 콜백은 새 화면 상태를 덮어쓰지 않으며,
가져오기는 커밋 전까지 guard를 확인한다. 프로세스 종료 후에는 미확인 파일을
자동 가져오지 않고 재선택을 안내한다. 내보내기 실패 파일은 일부/빈 파일일 수 있음을
안내한다. 가져오기 커밋 후 알림 재예약만 실패한 경우는 가져오기 롤백으로 표시하지 않는다.

## 검증과 남은 항목

기존 통합본의 파일 미리보기·확인 추가·손상 파일과 목표 화면 검증 이력은
이번 분리 APK의 실행 결과로 간주하지 않는다. U5 백업 9건·알림 13건을
포함한 이번 빌드·계측 결과는 `../STATUS.md`에 별도로 기록한다.
실제 SAF 제공자의 선택 UI, 파일 전송 중 회전, 알림 표시·재부팅의 실기기 검증은 남아 있다.

- 두 XML의 Bun XML 파싱 성공.
- `TransferActivity`의 transfer 전용 문자열 참조 누락 0건.
- Java 소스와 메모리 DB 테스트를 읽어 검토했다.
- Java LSP는 기존 `jdtls` 미설치로 실행할 수 없었다. 설치하지 않았다.

`GoalBackupTest` 9개: 다른 로컬 친구 ID로 왕복, 기존 데이터/보상 유지,
미등록 친구 건너뛰기, 추가 전용 병합, 손상/타입/날짜/중복/문자/크기 경계,
삽입 실패 전체 롤백, 계정/수명주기 무효화 롤백, 확인 후 매핑 변경, 패키지 충돌.

`GoalNotificationsTest` 13개: 기한 초과 pending 재예약 보존, consumed 재생성 방지,
새 과거 목표 제외/미래 목표 예약, 완료·삭제 정리, 기한 변경 재검사,
이전 계정·잘못된 인텐트 거부, 취소 후 무효화, 권한/서비스 없음에서 1회 소비,
고정 시각의 DB 대상 검증, 세대·진입·계정 변경 경계, 101개 기한에서
64개 예약·소비 후 대기열 충원. 별도 `qa_goal_reminder_regression.db`와 별도 설정을 사용하고
검사 뒤 정리한다. 현재 SQLiteOpenHelper의 경로 기반 열기를 포함해 실제 조회를 검증하며
사용자 계정 DB와 알림/세션 설정을 바꾸지 않는다.
고정 sleep이나 시각 경과에 의존하는 단언은 없다.

검증 명령:

```text
gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PfirebaseDisabled --offline
adb -s emulator-5570 shell am instrument -w -r -e class kr.example.haliltalk.data.GoalBackupTest,kr.example.haliltalk.goals.GoalNotificationsTest kr.example.haliltalk.test/android.test.InstrumentationTestRunner
```

이후 실제 SAF의 내보내기·미리보기 취소·확인 가져오기와 회전,
알림 거부/채널 차단/계정 전환/지연 기한/재부팅을 확인하고 `docs/STATUS.md`에 기록한다.
기존 `PACKAGE_USAGE_STATS` ProtectedPermissions lint 문제는 이 작업에서 건드리지 않았다.

## 조회한 Android 공식 문서

Context7 도구는 이 세션 카탈로그에서 제공되지 않아 Android 공식 원문을 직접 조회했다.

- 비정확 알람과 재부팅 후 복구:
  https://developer.android.com/develop/background-work/services/alarms/schedule
- SAF 문서 생성·열기와 URI 스트림:
  https://developer.android.com/training/data-storage/shared/documents-files
- Android 13 이상 알림 런타임 권한:
  https://developer.android.com/develop/ui/views/notifications/notification-permission
