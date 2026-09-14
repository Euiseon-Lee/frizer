# STEP 3 구현 범위 테스트 목록 및 결과

작성일: 2026-09-14. 범위: 현재 구현된 등록·추가 구매·조회·수정·음식 합치기·이력·입력 UI·이미지 선택기.
상태: 자동 검증 및 격리 DB 브라우저 검증 수행. 마지막 실행 수치와 개별 결과는 아래 자동 검증 및 [실행 목록](STEP3_TEST_CASES.md)을 따른다.
정책 기준: [STEP 3 보고서](STEP3_REPORT.md). 소비·폐기·개별 구매 항목 선택 이동·부분 분리·취소·삭제·엑셀은 미구현이므로 대상에서 제외한다.

## 구현 결과

합치기 히스토리 후속 구현: 개별 구매 기록과 기존 합치기 영수증을 통합 조회한다. 당시 이름은 보존하고 현재 연결 대상을 안내한다. 연속 합치기·기존 영수증 표시·재제출 중복 없음·통합 제한/동률 정렬·현재 대상 소멸·이름 이스케이프 검사 6개를 추가했다.

후속 사용자 요청으로 신규 등록 중복 제출 방지를 구현했다. V9 요청 영수증과 화면 제출 잠금을 추가하고, 같은 ID의 재전송·동시 요청·다른 내용·실패 재시도·수정/합치기 이후 재시도까지 검사했다. 요청 문구는 '해줘'로 통일했으며 최종 화면 명칭은 '이동'이다.

기존 192개 Java 검사를 유지하고, 별도 시나리오 통합 검사와 등록 UI JavaScript 검사를 추가했다.
검사에서 재현한 다음 세 결함은 수정하고 회귀 검사에 포함했다.

| 결함 | 재현 | 수정 및 재검증 |
| --- | --- | --- |
| 기존 음식 추가 오류 화면의 500 | 기존 음식 선택 + 수량 `NaN` 등 형식 변환 실패 시 foodForm이 null인 상태에서 category() 호출 | 템플릿에서 null을 안전하게 처리. 신규·추가·수정 3경로 × 9개 잘못된 필드 검증 |
| 선택 변경 후 취소 목적지 불일치 | 일반 등록에서 기존 음식 선택 또는 미리 선택된 A를 B로 변경 후 취소 | 현재 등록 방식과 선택 음식에 따라 링크 갱신. URL은 서버에서 생성. JS 검사 및 실제 브라우저 A 선택→해제→B 선택→취소 확인 |
| 서로 다른 병합의 동일 요청 ID 경합 | A→B와 C→D를 동일 UUID로 동시에 요청 | 영수증 PK 충돌을 업무 충돌로 변환하고 트랜잭션 롤백. 경합 3회 재현 후 회귀 검증 |

## 설계 및 확정 정책

### 경우의 수를 산출하는 방법

모든 문자열·날짜·요청 순서·스레드 실행 순서의 조합은 유한한 전수 검사 대상으로 만들 수 없다.
이번 목록은 요구사항에 따라 의미가 같은 입력을 묶고, 경계값·상태 전이·관련 입력의 교차 조합·실패 지점을 선택했다.
아래 유한 조합의 모든 항목과 명시된 시나리오는 실행했으며, 이 결과를 임의의 모든 입력이나 모든 기기의 보증으로 해석하지 않는다.

- 입력: 누락 / 정상 / 경계 바로 아래·동일·초과 / 잘못된 형식 / HTML 문자 / 오래된 버전.
- 음식: 없음 / 하나 / 같은 이름의 별도 음식 / 여러 구매 항목 / 여러 단위·위치 / 기존 비구조화 수량.
- 요청: 정상 / 재제출 / 내용이 다른 토큰 재사용 / 동시에 도착 / 이전 작업 뒤 도착.
- 보존: 기존 MASTER·ITEM·History·영수증을 전후 비교. 거부·실패 시 의도하지 않은 변경이 없는지 검사.
- 화면: 초기 / 선택 / 선택 변경·해제 / 오류 재표시 / 성공 이동 / 뒤로가기 복원 / 좁은 화면.

### 명시적으로 전개한 조합

| ID | 축 | 조합 수 | 검사 근거 |
| --- | --- | ---: | --- |
| C01 | 보관 위치 3 × 냉동 유형 3 × 냉동일 유무 2 × DB 상태 3 | 54 | DatabaseSmokeTest.storageCombinations |
| C02 | 소비기한 상태 4 × 유통기한 상태 4 × 개봉일 상태 4 | 64 | InventoryIntegrationTest.overviewCoversAllDateCombinationsWithoutContradictoryEmptyCard 내부 반복 |
| C03 | 신규·추가 2 × 문자열 필드 6 × 최대 길이 전후 3 | 36 | Step3ScenarioIntegrationTest.textBoundariesOnBothRegistrationPaths |
| C04 | 출처 6(미선택 포함) × 보관 위치 4(미선택 포함) × 오늘 냉동 여부 2 | 48 | sourceStorageTodayCombinationsPreserveAdditionalPurchase |
| C05 | 신규·추가·수정 3 × 형식 오류 필드 9 | 27 | malformedFieldsRenderErrorsOnEveryWritePath |
| C06 | 신규·추가·수정 3 × 미래 금지 날짜 3 | 9 | tomorrowIsRejectedOnEveryWritePath |
| C07 | 병합 요청 필드 4 × 누락·형식 오류 2 | 8 | missingOrMalformedMergeParametersDoNotWrite 내부 반복 |

C03에서 기존 음식의 이름·분류는 서버가 MASTER의 값으로 확정하므로 조작된 길이 초과 입력도 공유 정보를 변경하지 않아야 한다.
C04는 입력된 냉동일(오늘 이전)과 시판 냉동 유형을 기본으로 하며, 냉동일 미상·기본 유형은 기존 검사가 담당한다.
C01의 CONSUMED/DISCARDED는 기존 DB 상태 제약 검증이며 미구현 소비·폐기 기능을 검증했다는 의미가 아니다.
64개 내부 반복은 JUnit 결과에서 한 테스트로 집계된다. 조합 수와 테스트 실행 수를 합산하지 않는다.

### 기능별 검사 목록

아래 I=InventoryIntegrationTest, M=FoodMergeIntegrationTest, S=Step3ScenarioIntegrationTest, D=DatabaseSmokeTest이다.
정확한 메서드·매개변수 실행 목록은 [Java 실행 목록](STEP3_TEST_CASES.md)과 테스트 소스에서 확인한다.

| ID | 검사할 내용 | 자동 검사 근거 | 결과 |
| --- | --- | --- | --- |
| R01 | 필수 음식명·수량·단위·보관 위치, 오류 중 입력 유지 | I 등록 검증, S C03/C05 | 통과 |
| R02 | 음식명100·분류50·메모500·용량50·출처메모200·단위10 경계 | S C03 | 통과 |
| R03 | 수량 0·음수·초과 자릿수·최소/최대 허용값·비숫자 | I 수량 매개변수, S invalidAdditionalQuantity… | 통과 |
| R04 | 단위 trim·소수 표시·자동 반올림 금지·기존 자유 문자열 | I structuredQuantity…/legacyQuantity… | 통과 |
| R05 | 출처 미선택·ETC 구분 및 출처 메모의 소속 | I unspecifiedSource…/sourceMemo…, S C04 | 통과 |
| R06 | 배달 잔반 기본 냉동·명시 위치 우선·시판/직접 냉동·오늘 날짜 | I 냉동 검사, S C04 | 통과 |
| R07 | 소비·유통기한 독립 입력, 구매·개봉·냉동 미래일 거부 | I 날짜 검사, S C05/C06 | 통과 |
| R08 | 기존 음식 추가는 같은 MASTER·새 ITEM, 기존 항목·이력 보존 | I addPurchaseUsesSharedIdentity… | 통과 |
| R09 | 조작된 이름·분류 무시, 대상 미존재·버전 누락·오래된 버전 거부 | I addPurchaseRejects…, S C03/sequentialConflicts… | 통과 |
| R10 | 알 수 없는 등록 방식·잘못된 enum/날짜/수량은 쓰기 없이 오류 | S unknownRegistrationMode…/C05 | 통과 |
| R11 | 같은 이름의 신규 음식은 자동 통합하지 않음 | M sameNamesRemainSeparate… | 통과 |
| R12 | 신규 등록 동일 ID·동일 POST 재전송 및 동시 전송은 한 번만 생성 | S registrationPostRetry…/concurrentRegistrationRetry… | 통과 |
| R13 | 동일 ID·다른 입력은 거부, 다른 ID·같은 음식명은 별도 등록 | S sameRegistrationToken…/concurrentDifferentContent…/newRegistrationWithDifferentTokens… | 통과 |
| R14 | 이력 실패·영수증 완성 실패 시 전체 롤백, 동일 ID 재시도 가능 | S registrationFailure…/registrationCompletionFailure… | 통과 |
| R15 | 입력 오류 후 같은 ID 유지·정상 재제출, ID 누락/형식 오류 거부, 화면별 ID 분리 | S registrationValidationError…/registrationWithoutToken…/newRegistrationPages… | 통과 |
| R16 | 등록 뒤 수정·합치기를 했어도 원래 등록 재요청이 과거 상태를 복원하지 않음 | S originalRegistrationRetryAfterEditAndMerge… | 통과 |
| U01 | 정상 수정·공유 이름/분류·개별 정보·변경 없음 | I 수정 검사, M sharedNameEdit… | 통과 |
| U02 | 오래된 수정 화면·병합 후 수정·다른 구매 항목의 공유 정보 충돌 | I invalidAndStaleEdits…, M staleItemEdit…/sharedNameEdit… | 통과 |
| U03 | 종결 상태·없는 항목 수정 거부 | I terminalAndMissingFood… | 통과 |
| Q01 | 빈 목록·ACTIVE만 표시·정렬·위치 필터·날짜 안내 우선순위 | I 목록·홈 검사, S mixedUnits… | 통과 |
| Q02 | 복수 구매·서로 다른 단위/위치·기존 비구조화 수량 | S mixedUnitsLocationsTerminalStates… | 통과 |
| Q03 | 잘못된 ID·형식·위치 파라미터, 400/404 및 무변경 | S missingAndMalformedRoutes… | 통과 |
| H01 | 생성 당시 이름·정보 보존, 수정 전후 기록, HTML 이스케이프 | I 이력 검사, M mergePreserves… | 통과 |
| H02 | 합치기 1회 표시·홈 반영·기존 ITEM 이력 불변·현재 항목 안내 | M mergeAppearsOnceInHistoryAndHome… | 통과 |
| H03 | 과거 합치기 영수증 표시·연속 합치기의 최종 대상 연결 | M previousMergeReceipts…/chainedMergesResolve… | 통과 |
| H04 | 단순 이름 변경은 현재 이름만 안내하고 합치기 기록을 만들지 않음 | M renamedItemsExplain… | 통과 |
| H05 | 통합 최신순 제한·동률 정렬·이름 이스케이프·현재 음식 없을 때 전체 목록 링크 | M mergeHistoryUsesCombinedLimit…/mergeHistoryEscapes… | 통과 |
| M01 | 상대 음식이 없으면 비활성·직접 URL 진입 차단 | M mergeIsDisabled… | 통과 |
| M02 | 미리보기는 읽기 전용, 원본 구매·이력 건수, 같은 이름 대상 구분 | M previewDoesNotWrite…/inlinePreview… | 통과 |
| M03 | 대상 이름·분류·기본값 유지, 원본 제거, ITEM ID·구매 정보·이력 보존 | M mergePreserves…, S mixedUnits… | 통과 |
| M04 | 자기 병합·대상 없음·토큰 누락·버전 충돌·요청 내용 변경 거부 | M staleConfirmation…, S mergeRejects…/C07 | 통과 |
| M05 | 같은 요청 재제출, 동일 요청 동시 제출은 한 번만 반영 | M previewDoesNotWrite…/simultaneousRetries… | 통과 |
| M06 | A→B→C 후 A→B 재제출은 되살리기·중복 이동 없이 전체 목록 복귀 | S chainedMergeRetry… | 통과 |
| T01 | 신규 등록 이력 실패 시 롤백 | I actualHistoryInsertFailure… | 통과 |
| T02 | 추가 구매 이력 실패 시 MASTER 버전·ITEM·History 전체 롤백 | S additionalHistoryFailure… | 통과 |
| T03 | 공유 이름 수정 이력 실패 시 MASTER·형제 항목 시각까지 롤백 | S sharedIdentityEditFailure… 및 I editHistoryFailure… | 통과 |
| T04 | 병합 영수증 실패 시 이동·버전 변경 롤백 | M receiptFailure… | 통과 |
| X01 | 같은 버전으로 동시에 추가 구매, 한쪽만 성공 | S simultaneousAdditional… 3회 | 통과 |
| X02 | 원본/대상 추가 구매 ↔ 합치기, 한쪽 성공·다른 쪽 충돌 | S additionalPurchaseVersusMerge… 2경우 | 통과 |
| X03 | 원본/대상 공유 정보 수정 ↔ 합치기 | S sharedEditVersusMerge… 2경우 | 통과 |
| X04 | A→B와 B→A, 교착·데이터 유실 없이 한쪽만 성공 | S reciprocalMerges… 3회 | 통과 |
| X05 | A→C와 B→C, 대상 버전이 바뀐 나중 요청 거부 | S competingMerges… | 통과 |
| X06 | 서로 다른 음식 쌍에 동일 토큰 동시 사용 | S sameTokenForDifferentConcurrentMerges… 3회 | 수정 후 통과 |
| X07 | 추가→오래된 병합 거부→새 미리보기 병합→오래된 추가 거부→재선택 추가 | S sequentialConflicts… | 통과 |
| D01 | V7 기존 ID·이력·이름 보존·동명 음식 미통합 | M v7Preserves… | 통과 |
| D02 | V8 명시적 ETC 보존·NULL 허용·출처 없는 메모 금지 | S v8Preserves… | 통과 |
| D03 | FK·enum·필수값·보관 상태·인덱스·시간대·이력 전이 제약 | D 및 I 기존 migration 검사 | 통과 |

경합 검사는 두 스레드를 동시에 출발시키고 제한 시간 내 결과와 보존 조건을 검사한다.
모든 스케줄링 순서를 전수 열거하거나 대규모 부하를 재현하는 검사는 아니다. 앞뒤 순서의 대표 사례는 X07 및 기존 stale 검사로 보완한다.

### JavaScript 검사 목록

| ID | 경우 | 검사 파일 | 결과 |
| --- | --- | --- | --- |
| J01 | 초기 후보 숨김·명시적 검색·대소문자·동명 음식 식별 | registration-flow.test.cjs | 통과 |
| J02 | 빈 검색·결과 없음·입력 수정 시 옛 결과 제거 | 동일 | 통과 |
| J03 | 한글 조합 Enter/keyCode229는 검색·등록 차단, 일반 Enter는 검색 | 동일 | 통과 |
| J04 | 선택·해제·재선택·없어진 대상·음식이 없는 경우 제출 제어 | 동일 | 통과 |
| J05 | 등록 방식 전환 시 신규 이름·분류 초안 보존, 공유 필드 disabled | 동일 | 통과 |
| J06 | 분류 없음 숨김·단위 기본값 제안·사용자 단위 유지·오래된 버전 유지 | 동일 | 통과 |
| J07 | 선택 A→해제→B→새 등록 전환의 취소 경로 | 동일 | 수정 후 통과 |
| J08 | 출처·보관 위치 전환, 배달 기본값, 출처 메모 disabled | 동일 | 통과 |
| J09 | 오늘 냉동일은 서버 날짜, 직접 날짜 입력 시 체크 해제, 첫 오류 포커스 | 동일 | 통과 |
| J10 | 수량 입력·소수점·붙여넣기·비숫자·음수·지수·쉼표 등 11경우 | 동일 | 통과 |
| J11 | 병합 응답 역전·선택 해제 후 늦은 응답·네트워크 실패·재시도 | food-merge.test.cjs | 통과 |
| J12 | HTTP 실패·리다이렉트·응답 본문 실패·미리보기 없음·원본/대상 불일치 | 동일 | 통과 |
| J13 | 제출 중 중복 차단·대상 불일치 차단·BFCache 복귀 재조회 | 동일 | 통과 |
| J14 | 이미지 해시·그룹·12주기 bag·홈360회·중복·실패대체·저장소·복귀 | choco-selector.test.cjs | 통과 |
| J15 | 첫 제출 즉시 버튼 잠금·다음 제출 차단·입력값 전송 유지·브라우저가 복원한 입력 보존·버튼 재활성 | registration-flow.test.cjs | 통과 |
| J16 | 기존 음식 미선택 Enter 차단·선택 후 제출에서 MASTER ID/버전 활성 유지 | 동일 | 통과 |

JS 검사는 실제 소스 코드를 격리된 Node VM과 DOM 모형에서 실행한다. 실제 DOM·네트워크 연결은 아래 브라우저 흐름에서 보완했다.

## 자동 검증

최종 마감 재검증: Java 389개(이동 17 + 재고 95 + 시나리오 180 + DB 97), 실패·오류·건너뜀 0. `test bootJar` 성공. 등록 UI 25개 및 이동 미리보기·대상 선택 목록·쪼코 선택기 JavaScript 4종 모두 통과.

후속 검증에는 WARNING 0/1/3/4/100개, 날짜 미입력/과거/오늘/미래, 동일 수량 변경 이력 제외를 포함한다. 최종 날짜 카드·버튼·아이콘 수정까지 포함한 실행이며 브라우저 확인은 각 변경 당시 기록과 구분한다. [최종 상태](SESSION_SUMMARY.md)와 [전체 실행 목록](STEP3_TEST_CASES.md) 참고.
등록·폼·수량 JavaScript 25개, 기존 이미지 선택기와 확장된 합치기 JavaScript 검사 모두 통과.
`git diff --check` 통과. 최초 실패 기록은 위 결함 3건이며 최종 성공과 구분한다.

실행 환경은 이 원본 로컬 프로젝트의 JDK 21, Gradle 8.13, Testcontainers PostgreSQL 17.11이다.
테스트는 사용자 개발 DB를 초기화하지 않으며 각 Testcontainers DB 또는 별도 migration 스키마를 사용한다.

```powershell
.\gradlew.bat test bootJar --offline --no-daemon --console=plain
node src/test/js/registration-flow.test.cjs
node src/test/js/food-merge.test.cjs
node src/test/js/choco-selector.test.cjs
git diff --check
```

Java 결과 원본은 `build/test-results/test/TEST-*.xml`, HTML은 `build/reports/tests/test/index.html`이다.
이 경로는 이후 일부 테스트만 실행하면 내용이 바뀐다. 이번 최종 실행 목록은 [별도 목록](STEP3_TEST_CASES.md)에 보존했다.

## 브라우저 확인

일회용 DB `frizer_audit`(127.0.0.1:15439)와 별도 앱(127.0.0.1:18083)을 사용했다.
테스트 데이터는 브라우저에서 만든 검증 두부·검증 듀부와 추가 구매 항목이다. 기존 8080/8082 앱·DB는 검증 대상으로 사용하지 않았다.

| ID | 실제 UI 흐름 | 결과 |
| --- | --- | --- |
| B01 | 빈 등록 제출 → 필수 오류·첫 필드 포커스 → 입력 후 등록 | 통과 |
| B02 | 서로 다른 두 음식 등록 → 전체 음식 2개 | 통과 |
| B03 | 기존 추가 전환 → 초기 후보 없음·제출 비활성 → 결과 없음 검색 → Enter 검색 | 통과 |
| B04 | A 선택·읽기 전용 분류 → 해제·제출 비활성 → B 선택 → 취소 → B 구매 목록 | 통과 |
| B05 | 구매 목록에서 미리 선택된 추가 → 0.5팩·냉동·기타 메모·오늘 냉동일 → 저장 | 통과 |
| B06 | 기존 실온1팩과 새 냉동0.5팩이 별도 카드, 상세 날짜·출처 확인 | 통과 |
| B07 | 수정에서 기타→미선택·메모 변경 → 상세에 미선택과 수정 문구 | 통과 |
| B08 | 병합 선택 → 원본 구매2건·이력3건 미리보기 → 해제 시 제출 제거 → 재선택 | 통과 |
| B09 | 병합 실행 → 전체 목록으로 복귀·음식1개·구매3건(냉장2모/실온1팩/냉동0.5팩) | 통과 |
| B10 | 이력에서 예전 이름·출처·수정 전후 보존, 유일 음식의 합치기 비활성 | 통과 |
| B11 | 목록·구매 목록·상세·수정·등록·이력 × 320/390/448px | 18조합 DOM 가로 넘침 없음 |
| B12 | 320px 합치기 상단·하단, 구매 목록 실제 스크린샷 | 글자·날짜 배치 및 하단 버튼 접근 확인 |
| B13 | 브라우저 console 오류·경고 | 수집된 오류·경고 0 |
| B14 | V9 별도 DB에서 신규 등록 버튼 빠른 두 번 클릭, 새 등록 화면에서 같은 검사 반복 | 각 화면당 음식·구매 항목·이력·완료 영수증 1건씩, 총 2건 확인 |
| B15 | 성공 뒤 뒤로가기 시 제출 버튼 재활성 | 통과. 이름 필드는 autocomplete=off여서 브라우저가 복원하지 않았으며 입력 전체 복원을 보장하지 않음 |
| B16 | 별도 DB의 긴 과거 이름→두부 합치기 기록, 원래 수정 원문·현재 연결 안내, 각각 ITEM/MASTER 링크, 홈 합치기 요약 | 통과. 320px 뷰포트와 캡처 확인, console 오류·경고 0 |

B11은 페이지 가로 넘침 검사이며 모든 요소의 겹침·키보드·스크린리더를 전수 검증한 결과는 아니다.
좁은 화면의 fullPage 캡처가 DOM 측정과 다르게 표시되어 뷰포트 캡처·스크롤·실제 요소 폭으로 재확인했다. 이를 제품 CSS 결함으로 판정하지 않았다.
실제 iPhone Safari 및 Android 브라우저는 실행하지 않았다. 한글 IME 이벤트 분기는 자동 검증했고 실제 모바일 IME는 별도 검증 대상이다.
브라우저 확인은 등록 화면 수정 적용본에서 수행했다. 이후 서비스의 요청 ID 경합 수정은 전체 통합 테스트로 검증했다.
B14~B15는 최종 V9 빌드로 별도 실행했다. 이번 검증용 앱·DB는 정리했으며 사용자 실행 앱은 재시작하지 않았다.

### 신규 등록 중복 방지 계약

- GET 등록 화면은 UUID를 발급한다. 동일한 화면의 POST 재전송은 동일 ID를 유지한다.
- 누락된 ID로는 저장하지 않고 입력 오류 화면에 새 ID를 제공한다. 형식이 잘못된 ID는 400으로 거부한다.
- 동일 ID·동일 폼 입력은 성공 결과를 재사용한다. 입력 동일성은 서버에서 폼을 JSON으로 직렬화한 값으로 비교한다. 동일 ID에 다른 입력은 자동으로 새 등록하지 않는다.
- 다른 ID는 같은 이름·수량이어도 사용자가 새로 시작한 별도 등록이다. 이름 기반 자동 통합은 하지 않는다.
- 완료 영수증에는 FK를 두지 않아 향후 항목 정리 후에도 이미 처리한 요청이라는 사실을 유지한다. 미완료 상태는 음식·이력과 같은 트랜잭션에서만 존재하고 성공 시 food_id가 기록된다.
- 기존 음식 추가는 기존 MASTER 버전 검사를 유지하고, 화면의 중복 제출 차단을 함께 적용한다. 이번 신규 등록의 성공 결과 재사용 계약을 기존 추가까지 확장하지 않았다.

## 남은 범위

- R12에서 처음 관찰했던 신규 등록 중복 제출 문제는 V9와 화면 잠금으로 해결했다. 등록 결과 영수증을 임의로 지우거나 만료시키지 않으며, 향후 삭제 기능에서도 과거 요청 재전송으로 데이터가 재생성되지 않도록 유지해야 한다. 사용자 실행 앱은 재시작 시 V9를 적용한다.
- 실기기 Safari/Android·모바일 IME·스크린리더·전체 키보드 이동·오프라인 복구·수천 건 데이터 부하는 별도 검증 범위다.
- 프로세스 강제 종료·DB 연결 단절·디스크 장애와 임의의 모든 경합 순서는 이번 실패 주입 범위에 포함하지 않는다. 이번에는 이력/영수증 제약 실패와 요청 응답 실패를 주입했다.
- 미구현 STEP 3 기능은 구현 후 같은 방식으로 목록과 검증을 추가한다.

이번 범위의 목록에 든 자동 검사와 명시된 브라우저 검사를 실행했다. 미실행 환경은 완료 판정에 포함하지 않는다.
