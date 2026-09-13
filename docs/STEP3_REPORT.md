# FR!ZER STEP 3 설계 및 구현 보고서

작성일: 2026-09-13. 범위: MASTER·ITEM 재고 구조, 소비·폐기·분리·취소·삭제와 엑셀 등록·추가 구매.

상태: **설계 진행 중 / 구현·검증 미착수.** 사용자 확정 정책과 구현 제안을 구분해 기록한다.
STEP 3의 설계부터 구현·검증 결과까지 이 파일을 기준 문서로 갱신한다.
STEP 1·2 보고서는 완료 당시의 기록으로 유지한다.
엑셀 양식·처리 흐름의 세부 초안은 [일괄 등록 초안](BULK_REGISTRATION_DRAFT.md)에서 관리한다.
정책과 출시 범위는 이 STEP 3 보고서가 기준이며 보조 초안도 같은 결정에 맞춰 갱신한다.

## 구현 결과

- 현재 수행한 작업은 기존 V1~V6 스키마와 코드의 대조 및 설계 문서 정리다.
- STEP 3 애플리케이션 코드, migration, UI는 아직 작성하지 않았다.
- DB 변경·테스트 데이터 초기화는 수행하지 않았다. 문서는 사용자 요청으로 커밋·푸시하여 회사 PC에 인계한다.
- 아래 컬럼·전환 전략은 구현 설계안이며 실제 적용 결과가 아니다.

## 설계 및 확정 정책

사용자 확정 정책을 바탕으로 현재 V1~V6 스키마를 대조했다.
이 문서 작성으로 DB나 애플리케이션을 변경하지 않는다. 아래 V7은 예정 migration이며 실제 파일은 아직 없다.

사용자 후속 지침: 현재 데이터는 테스트용이므로 완벽한 과거 호환에 과도하게 투자하지 않는다.
새 구조의 명확성을 우선하고, 구버전 동시 운영·임시 호환 컬럼·복잡한 운영 복구 계획은 첫 구현에서 제외한다.
테스트 데이터 초기화는 가능한 전환 선택지이며 이 문서 작성 시 실제 데이터 삭제는 수행하지 않는다.

### 1. 사용자 확정 정책

1. quantity_amount는 현재 잔량이다.
2. 최초 수량 컬럼을 별도로 두지 않는다. 신규 유입 이력에 최초 숫자·단위를 보존한다.
3. 기존 비구조화 재고는 수량 작업 전에 사용자 확인으로 구조화한다. 자동 파싱하지 않는다.
4. 단위는 trim하고 합의한 표준 단위만 대표 표기로 정규화한다. 자동 환산하지 않는다.
5. ITEM 상태는 ACTIVE / DEPLETED다.
6. 부분 분리는 기존 ITEM 차감 + 새 ITEM 하나 생성이다. 전량 변경은 분리하지 않는다.
7. stock_batch_id, parent_item_id, operation_id를 도입한다.
8. 기존 changes_text는 유지하고 신규 구조화 수량·JSONB snapshot을 병행한다.
9. 소비·폐기는 한 번에 ITEM 하나를 대상으로 한다.
10. 마지막 유효 소비·폐기만 제한적으로 취소한다.
11. 물리 삭제는 웹의 명시적 오등록 정정 기능이다. ITEM과 관련 History를 함께 삭제한다.
12. 엑셀 첫 출시는 새 음식 등록·기존 음식 추가 구매만 지원한다.

엑셀은 행별 오류 표시·미리보기·반영 직전 재검증·중복 업로드 방지를 제공한다.
한 행이라도 오류가 있으면 전체 미반영하며, 반영 중 실패해도 전체 트랜잭션을 취소한다.
동일 MASTER에 여러 구매분을 추가하는 행은 허용한다. 반복 MASTER ID와 중복 업로드 행은 구분한다.

음식명 중복은 허용한다. MASTER 자동 통합은 하지 않는다. 사용자가 통합해도 ITEM·구매분·이력은 보존한다.

### 2. 현재 구현과의 차이

- food_item.food_id와 food_history.food_id가 현재 재고·이력 연결 키다. 이 ID를 보존한다.
- 현재 food_item에 음식명·분류와 실제 재고 정보가 함께 있다.
- quantity_amount는 NUMERIC(12,3), quantity_unit은 VARCHAR(10), quantity_text는 VARCHAR(50)이다.
- 신규 입력은 정수 9자리·소수 2자리이며 DB 수량 제약은 현재 0 초과다.
- 기존 비구조화 재고는 amount/unit 모두 NULL이며 quantity_text를 보존한다.
- 현재 상태는 ACTIVE / CONSUMED / DISCARDED다. 소비·폐기 기능 자체는 아직 미구현이다.
- 등록 이력은 changes_text TEXT에 snapshot-v1: 접두어와 JSON을 저장한다.
- 수정 이력은 changes_text에 변경 항목과 전후 값을 문자열로 저장한다.
- 현재 수정 충돌은 행 잠금과 expectedUpdatedAt 비교로 방지한다.
- MASTER, 구매분 식별, 분리, 취소, JSONB 컬럼은 아직 없다.

### 3. food_master 신규 테이블

| 컬럼 | 타입 / NULL | 의미 |
|---|---|---|
| master_id | BIGINT identity PK | 음식 식별자 |
| food_name | VARCHAR(100) NOT NULL | 대표 음식명, 공백만 입력 금지, UNIQUE 없음 |
| category | VARCHAR(50) NULL | 음식 분류 |
| default_quantity_unit | VARCHAR(10) NULL | 신규 ITEM 입력 기본값. 기존 ITEM 단위 변경 없음 |
| default_exclude_expiry_warning | BOOLEAN NOT NULL DEFAULT FALSE | 신규 ITEM 기한 경고 제외 기본값 |
| default_exclude_opened_warning | BOOLEAN NOT NULL DEFAULT FALSE | 신규 ITEM 개봉 경고 제외 기본값 |
| version_no | BIGINT NOT NULL DEFAULT 0 | 수정·통합·삭제 및 업로드 확인용 버전 |
| created_at | TIMESTAMPTZ NOT NULL | 생성 시각 |
| updated_at | TIMESTAMPTZ NOT NULL | 변경 시각 |

재고가 없어도 MASTER를 자동 삭제하지 않는다. 목록 노출과 추가 등록 검색은 별도로 처리한다.
MASTER 기본 설정은 신규 ITEM에 복사한다. 기존 ITEM 일괄 변경은 별도 명시적 작업이다.
대표 단위의 정확한 매핑표는 구현 전에 정하며, 자유 입력 영문 전체를 소문자로 바꾸지 않는다.

### 4. food_item 유지·확장

물리 PK 이름은 food_id를 유지한다. 설계에서 ITEM ID라 부르는 값이 이 컬럼이다.

| 컬럼 | 타입 / NULL | 의미 |
|---|---|---|
| food_id | 기존 BIGINT PK 유지 | ITEM 식별자, 기존 URL·이력 연결 보존 |
| master_id | BIGINT NOT NULL, FK RESTRICT | 소속 음식 |
| stock_batch_id | UUID NOT NULL | 원래 구매·등록분. 신규 유입마다 생성, 분리 시 공유 |
| parent_item_id | BIGINT NULL, 자기 FK RESTRICT | 직접 분리된 원본 ITEM |
| quantity_amount | 기존 NUMERIC(12,3) NULL | 현재 잔량. 타입 축소 없음 |
| quantity_unit | 기존 VARCHAR(10) NULL | 실제 재고 단위 |
| quantity_text | 기존 VARCHAR(50) NULL | 구조화 값의 표시 문자열 또는 기존 원문 |
| status | VARCHAR(20) NOT NULL | ACTIVE / DEPLETED |
| exclude_expiry_warning | BOOLEAN NOT NULL DEFAULT FALSE | 해당 ITEM 기한 경고 제외 |
| exclude_opened_warning | BOOLEAN NOT NULL DEFAULT FALSE | 해당 ITEM 개봉 경고 제외 |
| version_no | BIGINT NOT NULL DEFAULT 0 | 상태 변경 충돌 검증 |

나머지 기존 컬럼을 유지한다: storage_type, capacity_text, expired_at, sell_by_at,
purchased_at, opened_at, frozen_at, source_type, source_memo, freeze_type, memo,
created_at, updated_at. 기존 날짜·출처·위치 제약은 새 모델과 함께 검증한다.

food_name/category는 MASTER로 읽기·쓰기 책임을 옮긴다. 새 앱과 함께 전환하며
필요한 값의 복사 후 ITEM의 중복 컬럼은 제거한다. 구버전 코드 호환용 이중 쓰기는 하지 않는다.

수량·상태 제약:
- 비구조화 예외: amount/unit 모두 NULL이고 ACTIVE. 기존 quantity_text를 그대로 표시한다.
- 구조화 ACTIVE: amount > 0, 유효한 unit 존재.
- 구조화 DEPLETED: amount = 0, 유효한 unit 존재.
- 단위는 공백만 허용하지 않고 최대 10자. 기존 숫자 상한 유지.
- 새 등록·추가 구매는 0 초과. 소비·폐기 처리량도 0 초과.
- amount/unit 중 하나만 NULL인 상태는 금지한다.
- 구조화 값 변경 시 Service가 quantity_text를 재생성한다.

분리 제약:
- 0 < 분리량 < 현재 잔량. 전량 개봉·이동은 동일 ITEM 수정.
- 부모와 자식은 같은 master_id, stock_batch_id, 단위를 유지한다.
- 분리량만큼 기존 ITEM을 줄이고 새 ITEM을 하나 만든다. 같은 트랜잭션으로 처리한다.
- 자식은 기존 구매 정보와 날짜를 복사한 뒤 요청한 관리 조건만 변경한다.
- 신규 자식만 부모를 지정하고, 재부모 지정은 하지 않는다. 자신을 부모로 지정하지 못한다.
- stock_batch_id 자체는 유입 수량을 나타내지 않는다. 최초 수량은 유입 이력이 근거다.

인덱스: master_id, stock_batch_id, parent_item_id 및 현재 ACTIVE 날짜 검색 인덱스를 유지·검토한다.

### 5. food_history 유지·확장

기존 PK와 food_id FK, action_type, previous_storage_type, new_storage_type,
quantity_text, memo, created_at, changes_text를 보존한다. 기존 행의 원문은 재작성하지 않는다.

| 추가 컬럼 | 타입 / NULL | 의미 |
|---|---|---|
| operation_id | UUID NULL | 신규 작업에서 필수. 같은 작업의 여러 History를 연결 |
| processed_quantity_amount | NUMERIC(12,3) NULL | 해당 ITEM에 적용한 양 |
| quantity_unit | VARCHAR(10) NULL | 당시 단위 |
| before_quantity_amount | NUMERIC(12,3) NULL | 작업 전 해당 ITEM 잔량 |
| after_quantity_amount | NUMERIC(12,3) NULL | 작업 후 해당 ITEM 잔량 |
| snapshot_version | INTEGER NULL | 신규 스냅샷 형식 버전 |
| before_snapshot | JSONB NULL | 작업 전 구조화 원본 값 |
| after_snapshot | JSONB NULL | 작업 후 구조화 원본 값 |
| reversal_of_history_id | BIGINT NULL, 자기 FK RESTRICT, UNIQUE | 취소 대상 |
| before_item_version | BIGINT NULL | 작업 전 ITEM 버전 |
| after_item_version | BIGINT NULL | 작업 후 ITEM 버전 |

기존 행은 신규 컬럼 NULL을 허용한다. operation_id가 있는 신규 행에는 버전 및
작업별 필요한 수량·스냅샷 필드의 정합성을 강제한다. 허용할 action_type과 기존
위치 전이 CHECK를 함께 갱신한다. CREATE/UPDATE 분기만 추가해서 끝내지 않는다.

신규 작업 종류:
CREATE, ADD_STOCK, UPDATE, STRUCTURE_QUANTITY, CONSUME, DISCARD,
OPEN, FREEZE, MOVE, SPLIT, CANCEL.

- STRUCTURE_QUANTITY는 과거 자유 입력을 현재 잔량으로 확인하는 작업이다. 구매량 증가 없음.
- CREATE/ADD_STOCK은 실제 신규 유입에만 사용한다. 분리 자식에 CREATE를 남기지 않는다.
- 부분 개봉·이동은 같은 operation_id의 SPLIT 이력 2건으로 기록한다.
  부모 이력은 n → n-q, 자식 이력은 생성 전 NULL → q이다.
  스냅샷에 분리 목적(OPEN/MOVE 등), 원본·신규 ITEM ID, 구매분 ID를 보존한다.
- SPLIT의 두 이력에 같은 양이 있어도 구매·소비·폐기량에 더하지 않는다.
- 전량 개봉·이동은 OPEN/MOVE/FREEZE 이력 1건이다.
- CANCEL은 원본 이력을 삭제하지 않고 reversal_of_history_id로 연결한다.
- JSONB에는 표시 문장 대신 안정된 필드명, 숫자, 날짜, enum 코드와 명시적 null을 저장한다.
- 당시 음식명과 master_id, 구매분 연결은 스냅샷에도 보존한다.
- 현재 소속 MASTER는 ITEM에서 조회한다. History에 현재 master_id를 중복 FK로 두지 않는다.
- 과거 등록 snapshot-v1과 수정 changes_text 표시를 계속 지원한다.

집계:
- 신규 유입량은 CREATE/ADD_STOCK만 포함한다. SPLIT/OPEN/MOVE/UPDATE/CANCEL은 유입이 아니다.
- 출처에 직접 조리·배달 잔반도 있으므로 전체는 '총등록량/총유입량'이다.
  실제 '총구매량'은 이 중 당시 source_type=PURCHASE인 기록만 집계한다.
- 소비·폐기는 원본 처리량에서 유효한 취소를 제외해 계산한다.
- 숫자가 없는 과거 이력은 구매량 0으로 간주하거나 현재 잔량으로 역산하지 않는다.
- 물리 삭제된 기록은 집계에서도 사라진다. 삭제는 통계 영구 보존과 양립하지 않는 명시적 기능이다.

### 6. 취소와 충돌

- 같은 ITEM의 마지막 유효 CONSUME/DISCARD만 후보이며 이미 취소된 이력은 제외한다.
- 이후 소비·폐기·수량 정정·단위 변경·구조화·이동·개봉·분리가 있으면 차단한다.
- 소비 후 소비 후 취소처럼 연결된 후속 수량 작업이 있는 과거 처리는 첫 버전에서 취소하지 않는다.
- 음식명·메모·경고 설정 등 수량과 충돌하지 않는 변경은 유지한다.
- 날짜 변경도 실제 개봉·이동 등의 상태 변경에 해당하는지 구분한다.
- 취소 시 현재 잔량에 원래 처리량을 더하고 quantity_text/status만 재계산한다.
- snapshot 전체 복원 금지. 날짜·위치·개봉·음식명·메모·경고 설정을 덮어쓰지 않는다.
- 현재 화면 버전 검사와 취소 가능성 검사는 별개다. 버전 불일치는 새로고침 후 재검증한다.
- ITEM 갱신, 취소 이력 INSERT, 중복 취소 방지는 한 트랜잭션이다.

version_no는 모든 ITEM 변경 시 증가시킨다. 수량 이상 변경은 action_type만 보지 않고
전후 구조화 필드로 확인한다. 기존 updatedAt 비교를 버전 기반으로 전환하되 어느 경로도
충돌 검증을 빠뜨리지 않는다.

operation_id는 이력 연결 키이지 그것만으로 중복 제출 방지를 보장하지 않는다.
신규 쓰기 요청에 재사용 가능한 요청 토큰과 요청 내용 식별값을 적용하고, DB UNIQUE로
완료 결과를 한 번만 확정하는 별도 operation/request 기록을 둔다. 같은 토큰·다른 내용은 거부한다.
엑셀은 같은 업로드의 확인 재시도뿐 아니라 파일 재업로드의 신규 행 중복도 별도 식별한다.
이 기록의 세부 컬럼과 업로드 행 식별 규칙은 업로드 구현 설계에서 구체화한다.

### 7. 물리 삭제

- 웹의 오등록 정정 도구로만 제공한다. 소비·폐기와 별도 기능이다.
- 대상 ITEM 수·History 수와 다른 재고에 미치는 연결 영향을 먼저 보여주고 재확인한다.
- 실제 실행 시 대상 집합·버전·건수를 다시 검증한다. 확인 뒤 변경되면 재확인한다.
- MASTER 잠금을 기준으로 추가 등록·분리·통합·삭제와의 경쟁을 직렬화한다.
- History 삭제 → ITEM 삭제 → 요청한 경우 MASTER 삭제 순서로 같은 트랜잭션에서 처리한다.
- MASTER/ITEM/History FK는 RESTRICT 유지. 광범위한 CASCADE를 사용하지 않는다.
- 취소 History를 원본 History보다 먼저 지우는 등 자기 FK 의존성을 처리한다.
- ITEM만 삭제할 때 비어 있는 MASTER를 자동으로 삭제하지 않는다.

분리 관계 처리 제안:
- 부모 ITEM만 삭제해도 살아 있는 자식 ITEM을 자동 삭제하지 않는다.
- 삭제 전 미리보기에 영향을 받는 자식 연결 건수를 표시한다.
- 생존 자식의 parent_item_id를 명시적으로 NULL 처리하고 버전을 갱신한 후 부모를 삭제한다.
- 생존 자식의 stock_batch_id와 당시 분리 snapshot은 유지한다.
- 같은 operation_id라는 이유로 생존 ITEM의 이력을 함께 삭제하지 않는다.
- 부모 등록 이력이 삭제되면 구매분 최초 수량이 확인 불가능할 수 있음을 인정한다.
  생존 자식 수량을 신규 구매량으로 대체하지 않는다.

위 생존 자식 처리 방식은 확정된 삭제 범위를 구현하기 위한 제안이며 실제 UI에 드러내야 한다.

### 8. Flyway 전환 전략 — 테스트 단계에 맞춰 단순화

원칙: 이미 적용된 V1~V6은 수정하지 않는다. 실제 최신 migration 번호를 구현 시 다시 확인한다.

새 migration 하나(현재 기준 예정 V7)와 새 앱을 함께 전환한다.
- MASTER 생성, ITEM 확장, History 구조화 컬럼, 상태·수량 제약과 인덱스를 함께 적용한다.
- 기존 앱은 전환 중 중지한다. 구버전 앱과의 동시 운영은 지원하지 않는다.
- 간단하게 보존할 수 있는 기존 재고는 ITEM 1건당 MASTER 1개로 연결한다. 이름 기준 통합 금지.
- 기존 ID와 changes_text는 유지할 수 있으면 유지한다. 자유 문자열이나 과거 이력의 정교한 역산은 하지 않는다.
- food_name/category를 MASTER로 복사한 후 ITEM 중복 컬럼을 제거한다.
- ACTIVE 비구조화 재고를 유지하는 경우 기존 원문·NULL 쌍과 수동 구조화 정책을 적용한다.
- 과거 소진 상태·모순된 테스트 데이터 때문에 복잡한 변환이 필요하면 별도 호환 계층을 만들지 않는다.
  실제 대상 데이터를 확인하고 테스트 데이터 초기화 경로를 선택할 수 있다.
- 데이터 초기화는 별도 명시적 실행으로 구분한다. 앱 시작 때 자동으로 데이터를 지우지 않는다.
- 새 빈 테스트 DB에서 V1~새 migration 전체 실행과 새 시나리오 fixture를 검증한다.
- 보존 전환을 선택한 경우 해당 기존 데이터 fixture에서도 migration을 검증한다.
- 운영용 단계적 배포·복잡한 rollback 자동화는 실사용 배포 단계에서 필요에 맞춰 마련한다.

### 9. 구현 완료 검증 기준

- 보존 전환 선택 시 기존 ITEM/History ID·건수·원문 보존, MASTER 매핑 누락 없음, 자동 이름 통합 없음.
- 신규 입력/기존 비구조화 조회와 구조화, 0 소진, 수량 초과·중복 처리 방지.
- 두부 2모 → 1모 개봉 → 0.5모 소비 → 나머지 폐기 → 취소.
- 기존 구매분 1모 + 신규 구매분 2모에서 오래된 1모만 폐기.
- 분리 후 구매량 증가 없음, 분리 전후 총수량 보존, 다단계 부모 연결.
- 메모 변경 보존 취소, 개봉/이동/분리 후 취소 차단, 이중 취소 방지.
- 삭제의 자기 FK·생존 자식·취소 이력 처리, 확인 이후 변경 시 재확인, 실패 시 전체 복원.
- 과거 snapshot-v1/changes_text와 신규 JSONB의 이력 화면 호환.
- 엑셀 오류 한 건 시 전체 미반영, 미리보기 뒤 MASTER 변경·삭제 재검증, 중복 업로드 방지.
- 기존 날짜 정책·쪼코 이미지 선택 정책 유지. 테스트 데이터 보존 여부는 선택한 전환 경로에 따른다.

목록·상세의 새 UI, 대표 단위 매핑, 업로드 식별 방식은 이 정책 위에서 구현안을 구체화한다.

## 자동 검증

**STEP 3 자동 검증은 미실행.** 설계 문서 정리만 했으며 이전 단계의 통과 결과를 STEP 3 결과로 계산하지 않는다.

| 검증 | 결과 |
| --- | --- |
| 신규 migration 적용·제약 검증 | 미실행 |
| 수량·분리·소비·폐기·취소·삭제 통합 테스트 | 미실행 |
| 엑셀 등록·추가 구매 검증 | 미실행 |
| 전체 Java 테스트 및 이미지 선택기 회귀 검증 | 미실행 |
| bootJar | 미실행 |

구현 후 실제 실행 명령·환경·테스트 건수·실패 및 해결 사항·결과 파일을 기록한다.

## 브라우저 확인

**STEP 3 브라우저 확인은 미실행.** 구현 후 음식 등록·추가 구매, 구매분 선택,
부분 개봉·소비·폐기·취소, 삭제 확인, 엑셀 미리보기와 모바일 화면 결과를 기록한다.

## 남은 범위

- 컬럼·제약·생존 자식 삭제 연결 처리 등 구현 제안 구체화.
- 목록·상세·재고 액션 UI 및 대표 단위 매핑 결정.
- 신규 Flyway migration과 MASTER·ITEM·HISTORY 코드 구현.
- 테스트 데이터 실제 상태 확인 후 보존 전환 또는 초기화 경로 선택.
- 엑셀 등록·추가 구매와 요청·행 중복 방지 구현.
- 자동 검증과 브라우저 확인 후 위 결과 갱신.

엑셀 수정·소비·폐기는 후속 범위다. 통계·추천 점수·PWA·인증·배포는 이번 범위에 자동 포함하지 않는다.

문서 규격: 앞으로 STEP 보고서는 작성일·범위·상태와 함께 `구현 결과`,
필요시 `설계 및 확정 정책`, `자동 검증`, `브라우저 확인`, `남은 범위` 순서를 유지한다.
설계 중·미구현·미검증 항목을 완료 결과와 섞지 않고, 구현 완료 후 같은 파일에 실제 결과를 기록한다.
