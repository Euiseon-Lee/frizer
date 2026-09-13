# FR!ZER STEP 2 구현 및 검증 결과

작성일: 2026-09-13. 범위: 음식 등록·상세·ACTIVE 재고 목록, 홈 요약·기록 조회와 쪼코 UI.

## 구현 결과

- Java 패키지 `com.euiseon.friger`로 변경. Gradle group, 테스트, MyBatis XML, 로그 설정 참조 동시 변경.
- `inventory.entity.FoodItem`, `history.entity.FoodHistory`로 모델 이동. 중간 `domain` 패키지 제거.
- 상세 문서를 `docs/`로 이동. 루트 README는 문서 링크와 진입 안내 제공.
- `GET /`는 홈 요약을 표시. 등록은 `/inventory/new`, 상세는 `/inventory/{id}`, 기록 조회는 `/history`.
- 음식명·보관 위치·필수 수량, 선택 용량, 출처, 선택 날짜, 냉동 유형과 메모를 입력하는 Thymeleaf 폼.
- JavaScript는 보관 위치에 따라 냉동 입력 영역을 표시. 비활성화해도 서버 등록 정책은 동일하게 동작.
- 서버에서 문자열 길이·필수값·enum·날짜 형식·미래 구매/개봉/냉동일을 검증하고 입력 내용을 유지.
- 출처 기본 ETC, 배달 잔반의 미선택 위치 FREEZER, 명시한 위치 우선.
- FREEZER 유형 기본 HOME_FROZEN, 배달 잔반은 HOME_FROZEN으로 정규화.
- 기존 냉동일 공란은 NULL 유지. ‘오늘 넣었어요’를 선택하면 서울 기준 오늘 사용.
- 비냉동 위치에서는 freeze_type=NONE, frozen_at=NULL.
- FoodItem INSERT와 CREATE History INSERT를 한 트랜잭션으로 처리. 이력 실패 시 음식도 롤백.
- 목록은 ACTIVE만 생성 시각·ID 내림차순. 모든 사용자 문자열은 HTML escape하여 표시.
- 냉동일 미상 및 두 기한이 없는 경우 기한 미입력 표시. 과거 소비기한은 허용하되 경과 주의 문구 표시.
- V1 migration은 유지하고 V2에서 용량 컬럼 및 출처 변경을 추가. 기존 DELIVERY는 DELIVERY_LEFTOVER로 이전.

## 자동 검증

명령: `gradlew.bat test bootJar --no-daemon --console=plain`

| 검증 | 결과 |
| --- | --- |
| STEP 1 DatabaseSmokeTest | 97개 성공 |
| STEP 2 InventoryIntegrationTest | 26개 성공 |
| 전체 | 123개 성공, 실패·오류·건너뜀 0 |
| bootJar | 생성 성공 |

STEP 2 테스트는 별도 Testcontainers PostgreSQL 17.11에서 실행합니다.
등록과 CREATE 이력, 기본값·명시 위치 우선·냉동일 정책, 서울 날짜 경계, ACTIVE 목록 정렬,
서버 입력 오류, 실제 템플릿 렌더링, 출력 escape를 검증합니다.
이력 INSERT를 실제 DB trigger로 실패시켜 음식 INSERT도 롤백되는지 확인했습니다.

검증 중 체크박스 누락 요청을 primitive boolean에 바인딩하지 못하는 문제를 발견하여
nullable Boolean으로 받고 true 여부를 명시적으로 판단하도록 수정했습니다.

결과 파일은 프로젝트 루트 기준 `build/reports/tests/test/index.html`,
`build/test-results/test/TEST-*.xml`, `build/libs/frizer-0.0.1.jar`입니다.

## 브라우저 확인

- 실제 local PostgreSQL과 앱으로 빈 목록 → 음식 등록 → 저장 완료 → 목록 표시 확인.
- 냉동 날짜를 비운 검증용 음식을 등록하여 ‘냉동일 미상’과 수량 표시 확인.
- 390px 모바일 폭에서 폼과 목록 레이아웃 확인. 실제 iPhone Safari 기기 검증은 아직 수행하지 않음.
- 브라우저 검증용으로 만든 식품과 이력만 정리. 기존 사용자 데이터는 변경하지 않음.

직접 확인:

1. Docker Desktop Linux 엔진을 실행합니다.
2. 프로젝트 루트에서 `docker compose up -d --wait`를 실행합니다.
3. `gradlew.bat bootRun --args='--spring.profiles.active=local'`를 실행합니다.
4. `http://localhost:8080/inventory`에서 음식 등록 버튼을 누릅니다.
5. 음식명·수량·보관 위치를 입력해 저장하고 목록의 카드 전체를 눌러 전체 정보를 확인합니다.

앱이 이미 8080에서 실행 중이면 그대로 브라우저를 열면 됩니다.
IntelliJ 실행 설정에 예전 메인 클래스가 저장되어 있다면
`com.euiseon.friger.FrizerApplication`으로 변경하고 working directory는 프로젝트 루트,
active profile은 `local`로 설정합니다.

## 남은 범위

### 사용자 검토 후 반영

- 기능별 controller / service / dao / dto / entity / exception 구조. history는 controller / service / dao / dto / entity로 구성.
- 음식 전용 예외는 inventory.exception에 배치. 공통 예외는 여러 기능이 공유할 때 common.exception에 추가.
- 출처: 선택 안 함 / 장보기 / 배달 잔반 / 직접 조리 / 부모님의 은혜 / 기타. 미선택 저장값은 ETC.
- 신규 등록 수량 필수, 용량 선택(자유 문자열). 기존 미입력 수량은 임의로 채우지 않음.
- 냉동 유형은 직접 냉동 / 시판 냉동식품의 두 가지. 오늘 냉동을 선택하면 날짜 입력란에 서울 기준 오늘을 채움. 날짜를 직접 변경하면 체크 해제. 날짜 입력란은 계속 표시.
- `/inventory/{id}` 상세 화면에서 전체 저장 정보 확인. 없는 ID는 404.
- 기존 데이터 이전, 용량·수량·상세 페이지·출처 메모 검증을 포함하여 총 123개 테스트 통과.
- 실제 브라우저에서 입력 → 저장 → 상세 보기 확인. 오늘 날짜 자동 입력과 직접 수정 시 체크 해제도 브라우저에서 확인.
- 쪼코 V5 Final과 사용자 후속 요청에 따라 사진·색상·말투·상단 문구와 카드 배치를 반영.
- 일괄 처리는 [엑셀 대조 방식 논의 초안](BULK_REGISTRATION_DRAFT.md)으로 정리. 구현하지 않음.
- ‘기타’ 출처 선택 시 선택 출처 메모 표시·저장·상세 조회. V3에 별도 source_memo 컬럼 추가.
- 용량 안내 문구를 사용자 지정 문구로 변경하고, 보관 위치 안내는 선택 버튼 위로 이동.
- 오늘 냉동 체크박스를 냉동 보관 시작일 그룹의 날짜 입력 아래 오른쪽에 배치.
- 유통기한·소비기한을 각각 선택 입력·저장·표시. V4는 기존 소비기한을 유지하고 유통기한 컬럼만 추가.
- 단독 입력·동시 입력·둘 다 미입력 및 날짜 오류를 검증. 유통기한 경과를 소비기한 경과로 처리하지 않음.
- 냉장 입고일·개봉 상태는 [날짜 정책 초안](DATE_POLICY_DRAFT.md)으로 설명. 아직 구현하지 않음.

소비·폐기·위치 이동·수정, 엑셀 일괄 처리·다중 삭제, 추천 점수, PWA, 인증·배포는 포함하지 않습니다.
원격 push는 별도 사용자 요청 시 진행합니다.
