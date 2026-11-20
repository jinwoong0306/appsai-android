# U5 이후 추가 작업 3인 분담

기준: 공개 `main`의 U5 `fcdda8c`, 2026-11-19 사용자 지정 담당 순서. 원본 통합 작업 폴더에는 아직 공개되지 않은 작업이 섞여 있으므로 해당 폴더의 파일 전체를 최신 `main`에 덮어쓰지 않는다. 이미 공개된 U1~U5 기능을 새 기여로 중복 계산하지 않는다.

| 업로드 순서 | 담당 | 계정 | 후속 기능 |
|---|---|---|---|
| 1 | 박재영 | `@jaezero` | 일상 행동 개선·친구별 3/7/14 시도일 모험·엽서 |
| 2 | 이종서 | `@Creepc` | 소품 탐색·찜·직접 코인 보상·코인 원장 |
| 3 | 장진웅 | `@jinwoong0306` | 한국어 화면·공간·미읽음 목록·질문형 이미지 안내 |

이 표는 사용자가 지정한 **앞으로의 작업 책임**이지 과거 코드의 작성자나 팀원의 실제 기여 증명이 아니다. 업로드 당시 계정 인증과 저장소 권한을 확인하고, 인계받은 코드의 통합과 직접 수정·검증 범위를 구분한다.

## 1. 박재영: 행동·모험

U3의 기존 작은 행동/회고를 보존하며, 홈의 오늘 이어갈 친구와 7일·누적 시도일, 모험 경로의 친구별 진척, 빈 오늘 계획에만 적용되는 추천 초안, 기록으로 열리는 1/3/7/14일 추억 엽서를 연결한다. 기록 수정·삭제 후 재계산하며 휴식과 미기록은 실패로 취급하지 않는다. 모험 경로 변경은 확인을 거쳐 저장한다.

주요 경계: `AdventureActivity.java`, `data/AdventureStore.java`, `JourneyActivity.java`, `MainActivity.java`의 행동/모험 진입, `GoalsActivity.java`의 친구 진입, `data/FriendsDatabase.java`의 `adventure_choices` 추가, `res/layout/screen_adventure.xml`, `res/layout/row_adventure*.xml`, `res/values/adventure.xml` 및 기능별 저장·화면 회귀. 공개 U5의 계정 DB·목표 알림·파일 전송을 유지한다. **코인 보상과 찜/원장, 후속 이미지 안내·새 전역 글꼴/공간은 넣지 않는다.** 모험 기록과 엽서는 로컬 전용이며 AI나 목표 JSON·기존 클라우드 백업에 자동 포함하지 않는다.

## 2. 이종서: 상점·재화

기존 소품의 테마/부위/보유/구매 가능 필터, 도감과 찜, 직접 구매 확인, 보유품의 꾸미기 초안 전달, 오늘 실제 시도 회고 뒤 **별도 확인한** 계정·기기 날짜별 1회 20코인, 시작 선물·이전 잔액·적립·구매 원장을 맡는다.

주요 경계: `ShopActivity.java`, `shop/ShopCatalog.java`, `shop/ShopCollectionStore.java`, `shop/CoinLedger.java`, `shop/DemoShopStore.java`, `data/QuestRewardStore.java`, `JourneyActivity.java`의 보상 진입, `CustomizationActivity.java`의 초안 연결, `res/layout/screen_shop.xml`과 상점 행·내역, `res/values/shop.xml`, `collections.xml` 및 구매·마이그레이션 회귀. 이미 공개된 1번의 DB v9 `adventure_choices`를 보존하고, 각각 독립적으로 빌드·검증되는 실제 증분으로 v10 `shop_wishlist` → v11 `coin_ledger`/구매 기록 → v12 `quest_reward_receipts`/확인형 보상을 추가한다. 기존 500코인·8개 기본 소품·XP·소유권을 보존한다. 지급·구매/원장은 각각 같은 트랜잭션으로 처리하고 실패 시 롤백한다. 찜은 자동 구매가 아니다.

## 3. 장진웅: 화면·안내

주아/고운돋움 글꼴·한국어 날짜/앱 이름, 홈·친구·꾸미기·가로/넓은 창의 실제 데이터 화면, 미읽음 표시, 정원·공간·채팅 배경, 12개 주제·34개 단계의 밝음/다크 실제 화면 이미지 안내와 확대·방향키/접근성 이동을 맡는다.

주요 경계: `KoreanUi.java`, `AdaptiveLayout.java`, `HelpGuide.java`, `GuideCatalog.java`, `GuideImageView.java`, `ReferenceActions.java`, `character/SceneBackgrounds.java`, 관련 홈/꾸미기/안내 XML·색상·스타일·글꼴·원화·WebP와 화면 회귀, `docs/SOURCES.md`의 글꼴·이미지 출처/라이선스. 공개 U2/U3의 읽음 영수증과 XP 증가를 유지하고, 안내를 읽는 것만으로 저장·동의·구매·재화를 변경하지 않는다. 1·2번의 최종 화면을 설명하는 이미지는 두 기능의 통합 뒤에 촬영한다.

## 적용·검증

1. **1번 → 2번 → 3번** 순으로 최신 공개 `main`에서 별도 브랜치를 만든다. 각 브랜치는 자신의 기능에 필요한 Java/XML·Manifest·직접 테스트를 함께 포함해 독립적으로 빌드 가능해야 한다.
2. `MainActivity.java`, `JourneyActivity.java`, `CustomizationActivity.java`, `FriendsDatabase.java`, `res/values/shop.xml`, `styles.xml` 등 공통 파일은 이전 단계의 최신 변경 구간을 보존하며 병합한다. 원본의 더 오래된 전체 파일을 덮어쓰지 않는다.
3. 기능별 계측/화면·기존 저장 데이터 보존을 확인한 다음 변경 경로만 stage한다. 공개 구성 빌드: `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:compileReleaseJavaWithJavac -PfirebaseDisabled`. `docs/STATUS.md`의 기존 로컬 검증 수치는 새 공개 브랜치의 결과로 바꾸어 주장하지 않는다.
4. 실제 개발·검토 범위와 남은 검증을 PR에 적고 다른 팀원이 리뷰한다. 이 문서의 담당 표만으로 작성자 표시, 기여 횟수, push 또는 `main` 반영을 주장하지 않는다.
