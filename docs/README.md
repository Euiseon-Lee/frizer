# FR!ZER

**프리!저 · FR!ZER — 배민 켜기 전에 잠깐**

1인 가구의 냉장고 재고 누적과 냉동 악성재고를 최소한의 입력으로 관리하는 개인용 모바일 웹 프로젝트입니다.
재고의 정확한 수량보다 유지하기 쉬운 입력과 먼저 확인할 음식의 발견을 우선합니다.

## 현재 상태

STEP 3 설계·구현·검증의 기준 문서는 [STEP 3 보고서](STEP3_REPORT.md)입니다.
최종 UI·검증 범위와 보류 사항은 [오늘 작업 정리](SESSION_SUMMARY.md)를 확인합니다.
현재 STEP 3은 **음식별 목록·구매분 상세·음식 단위 전체 이동을 구현**했습니다.
현재 구현 범위의 [테스트 목록 및 결과](STEP3_TEST_MATRIX.md), [Java 실행 목록](STEP3_TEST_CASES.md)을 확인할 수 있습니다. 2026-09-14 검증 보강 후 Java 389개와 등록 UI JavaScript 25개·기존 JS 검사·bootJar가 통과했습니다. 신규 등록은 클릭 직후 중복 제출을 막고 V9 요청 영수증으로 동일 요청의 재전송·동시 전송을 한 번만 저장합니다.
`/foods/{id}`에서 개별 구매 목록을 확인하고, **다른 음식으로 이동**에서 대상을 선택하면 같은 화면에 미리보기가 표시됩니다. 확인 후 실행하면 구매 항목과 이력을 보존하고 전체 음식 목록으로 이동합니다.
새 음식 등록은 같은 이름이어도 별도 음식으로 생성합니다. 등록 화면에서 기존 음식을 선택하거나 개별 구매 목록의 '구매 항목 추가'로 기존 음식에 추가할 수 있습니다. 출처 미선택은 기타와 구분합니다. 소비·폐기·분리·취소·엑셀은 후속 범위입니다.
아래 STEP 2 수치는 당시 결과이며 이후 수정·경고·이미지 정책의 인계 상태는
[최근 인계 문서](ui-v5-addon/NEXT_SESSION.md)를 참고합니다.

**STEP 2 등록·조회 및 쪼코 V5 UI 구현 완료 (2026-09-13).**
Java 기본 패키지는 `com.euiseon.friger`이며 모델은 `inventory.entity.FoodItem`, `history.entity.FoodHistory`에 둡니다.
등록 폼, 서버 검증, 음식과 CREATE 이력의 트랜잭션 저장, ACTIVE 목록과 모바일 레이아웃을 구현했습니다.

- `/`: 보관 수량·소비기한 요약·위치별 카드·최근 기록
- `/inventory`: 위치별 보관 목록과 카드 전체 클릭으로 상세 이동
- /inventory/new: 음식 등록 폼
- /inventory/{id}: 음식 상세
- /history: 최근 음식 기록 조회
- `POST /inventory`: 저장 후 목록으로 이동

`test bootJar` 성공, STEP 1 97개 + STEP 2 26개 = **123개 테스트 통과**.
자세한 결과는 [STEP 1 보고서](STEP1_REPORT.md), [STEP 2 보고서](STEP2_REPORT.md)를 참고합니다.
현재 등록 정보 수정은 구현되어 있습니다. 소비·폐기·개별 항목 이동, 엑셀 일괄 처리·다중 삭제, 대시보드 점수, PWA, 인증 및 배포는 아직 구현하지 않았습니다.

## 명칭

| 구분 | 값 |
| --- | --- |
| 서비스 표시명 | FR!ZER |
| 한글 발음 | 프리!저 |
| 프로젝트 / Gradle rootProject.name / DB | frizer |
| Java 기본 패키지 | com.euiseon.friger |
| 애플리케이션 클래스 | FrizerApplication |
| 향후 PWA name / short_name | FR!ZER / FR!ZER |

표시명에는 느낌표를 유지하며 Java 패키지·DB·디렉터리에는 `frizer`를 사용합니다.

## 기술 스택과 버전

| 구성 | 버전 / 선택 |
| --- | --- |
| Java toolchain | 21 |
| Gradle Wrapper | 8.13, Groovy DSL |
| Spring Boot | 3.5.16 |
| MyBatis Spring Boot Starter | 3.0.5 |
| MyBatis Core | 3.5.19 (Starter 전이 의존성) |
| Flyway core / PostgreSQL 모듈 | 11.7.2 (Boot BOM) |
| PostgreSQL JDBC | 42.7.11 (Boot BOM) |
| Testcontainers | 1.21.4 (Boot BOM) |
| 로컬 / 테스트 PostgreSQL 이미지 | postgres:17.11 |
| 애플리케이션 | Spring MVC, Thymeleaf, Bean Validation |
| 향후 프런트엔드 | HTML, CSS, Vanilla JavaScript, iPhone Safari 우선 |

Boot 3.x를 유지하고 Boot 3.2~3.5를 지원하는 MyBatis Starter 3.0.x를 사용합니다.
Gradle의 native BOM 지원으로 Boot 관리 의존성 버전을 맞춥니다.
MyBatis 테스트는 전체 Spring Context와 실제 XML Mapper를 사용하므로 별도의 MyBatis slice-test starter는 필요하지 않습니다.
JPA, Lombok, H2, SPA 프레임워크는 사용하지 않습니다.

선정 근거:

- [Spring Boot 3.5 시스템 요구사항](https://docs.spring.io/spring-boot/3.5/system-requirements.html)
- [Spring Boot 관리 의존성](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html)
- [MyBatis Starter 호환표](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)
- [PostgreSQL 지원 버전](https://www.postgresql.org/support/versioning/)

최신 안정 3.x와 운영 시점의 지원 상태는 별개입니다. 공개 배포 전에 3.x 지원 상태를 다시 확인합니다.

## 사전 준비

### Docker로 앱과 DB 함께 실행

Docker Desktop의 Linux containers 엔진만으로 실행할 수 있습니다. 이 경로는 호스트 JDK가 필요 없습니다.
최초 실행 시 `.env.example`을 `.env`로 복사하고 `FRIZER_DB_PASSWORD`를 입력합니다.
기존 `.env`와 DB 볼륨은 유지합니다.

```powershell
docker compose --profile app up -d --build --wait
docker compose --profile app ps
docker compose --profile app logs --tail 100 app
```

브라우저에서 `http://localhost:8080/`를 엽니다. `FRIZER_APP_PORT`로 앱 포트를 바꿀 수 있습니다.
앱과 DB는 로컬 PC의 `127.0.0.1`에만 노출합니다. 앱은 `prod` 프로필로 Compose 내부 DB에
접속하며 Flyway migration을 자동 적용합니다. DB 준비 후 앱을 실행하고 HTTP healthcheck로 준비 상태를 확인합니다.
이미지에는 `.env`, 로컬 JDK 설정, Git 기록을 포함하지 않습니다.

```powershell
# 코드 수정 후 다시 빌드하고 실행
docker compose --profile app up -d --build --wait
# 중지 (DB 데이터 보존)
docker compose --profile app stop
```

기존 `docker compose up -d --wait` 명령은 DB만 실행합니다. IntelliJ 또는 `bootRun`으로
개발할 때는 Docker 앱을 먼저 `docker compose stop app`으로 중지해 8080 포트 충돌을 피합니다.
Docker 이미지 빌드는 `bootJar`를 실행하며, Docker가 필요한 통합 테스트는 별도로 실행해야 합니다.

### 화면 수정 시 재빌드 없이 확인

IntelliJ/bootRun의 `local` 프로필은 `ui-dev`를 함께 활성화합니다. 프로젝트 루트에서 실행하면
`src/main/resources/templates`와 `src/main/resources/static` 원본을 직접 읽습니다.
설정을 처음 적용할 때만 앱을 다시 실행하고, 이후 HTML·CSS·JavaScript·이미지 변경은 저장 후
브라우저 새로고침으로 확인합니다. 브라우저를 자동 새로고침하는 기능은 포함하지 않습니다.
Java 코드, MyBatis XML, 설정 파일, DB migration 변경은 빌드·재시작이 필요합니다.

Docker에서도 같은 방식으로 확인하려면 최초 실행 또는 Java 변경 시 다음 명령을 사용합니다.

```powershell
docker compose -f compose.yml -f compose.ui-dev.yml --profile app up -d --build --wait
```

이후 UI 수정에는 위 명령을 반복하지 않고 브라우저를 새로고침합니다. UI 원본 디렉터리만
읽기 전용으로 마운트하며 일반 `compose.yml` 실행은 빌드된 리소스를 사용합니다.
이 PC의 Docker 확인 포트는 Git 제외 `.env`의 `FRIZER_APP_PORT=8082`이고 사용자 로컬 실행은 8080입니다.
개발 모드의 템플릿 캐시와 정적 리소스 캐시를 비활성화합니다.
설정 옵션은 [Spring Boot 3.5 공식 문서](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/)를 참고합니다.

2026-09-14 실행 검증: 앱과 PostgreSQL 컨테이너 모두 healthy, Flyway V6 적용,
`/`, `/inventory`, `/inventory/new`, `/history` HTTP 200 확인.
호스트 JDK 21에서 `test bootJar` 성공: 통합 테스트 177개, 실패·오류·건너뜀 0개.
`node src/test/js/choco-selector.test.cjs`도 통과했습니다.

### 호스트에서 Java 앱을 실행하는 경우

- JDK 21 설치, `JAVA_HOME` 및 PATH 설정.
- Docker Desktop 설치 후 Linux containers 엔진 실행.
- 최초 실행 시 Gradle, Maven Central 의존성, Docker 이미지 다운로드를 위한 네트워크 접근.
- 프로젝트 파일을 `C:\dev\frizer`에 배치. 기존 파일이 있으면 먼저 비교하며 덮어쓰지 않습니다.

PowerShell에서 확인합니다.

```powershell
Set-Location C:\dev\frizer
java -version
docker version
.\gradlew.bat --version
```

Gradle Wrapper가 있어 시스템 Gradle 설치는 필요하지 않습니다.
Toolchain 설정은 JDK 설치 자체를 대신하지 않습니다. JDK 21 자동 다운로드 저장소는 설정하지 않았습니다.

### 개발자별 Java 설정

설치한 JDK 21을 `JAVA_HOME` 또는 IntelliJ의 Project SDK와 Gradle JVM으로 지정합니다.
Gradle에서 JDK를 찾지 못하면 Git에서 제외된 `gradle.properties`에
`org.gradle.java.installations.paths`를 자신의 JDK 경로로 설정한 뒤 Gradle 프로젝트를 다시 동기화합니다.
개인 JDK 절대 경로와 DB 비밀번호는 공유 실행 구성에 넣지 않습니다.

## 로컬 실행

### IntelliJ 실행

먼저 `docker compose up -d --wait postgres`로 DB를 실행합니다.
실행 구성에서 **Frizer local**을 선택합니다. 저장소의 `.run/Frizer-local.run.xml`은
`local` 프로필과 프로젝트 루트 작업 디렉터리를 지정해 `.env`의 DB 설정을 읽습니다.
기본 접속 주소는 `http://localhost:8080/`입니다. Docker 앱이 실행 중이면
`docker compose stop app`으로 앱 컨테이너를 중지한 뒤 IntelliJ에서 실행합니다.

기존 실행 구성을 직접 사용할 때는 Program arguments에
`--spring.profiles.active=local --server.address=127.0.0.1`을 입력하고 Working directory를 프로젝트 루트로 지정합니다.
프로필이 빠지면 `Failed to configure a DataSource` / `Failed to determine suitable jdbc url`로 기동이 실패합니다.

개인 포트나 OS별 JVM 옵션이 필요하면 실행 구성을 복제해
`.run/*.local.run.xml`로 저장합니다. 이 파일은 Git에서 제외됩니다.
Docker 앱과 함께 실행하려면 개인 구성에 `--server.port=8081`처럼 다른 포트를 지정합니다.
두 앱은 같은 개발 DB를 사용합니다.

새 환경에서는 `.env.example`을 `.env`로 복사하고 `FRIZER_DB_PASSWORD`에 개인 개발용 비밀번호를 입력합니다.
`.env`는 Git에서 제외되며 기존 DB가 있으면 그 계정의 비밀번호를 사용합니다.
기존 DB 볼륨의 비밀번호는 `.env` 변경만으로 바뀌지 않습니다. 비밀번호를 바꾸려면 DB 계정도 함께 변경해야 합니다.

```powershell
Set-Location C:\dev\frizer
docker compose config --quiet
docker compose up -d --wait
docker compose ps
.\gradlew.bat clean test
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

테스트 실패 시 원인을 해결한 뒤 진행합니다. 애플리케이션 기본 포트는 8080입니다.
`http://localhost:8080/`를 열면 홈 화면이 표시됩니다. 음식 등록 버튼으로 등록과 저장 결과를 확인할 수 있습니다.
로컬 profile 없이 실행하면 DB 접속 설정이 없으므로 정상 기동을 기대하지 않습니다.

### 로컬 PostgreSQL

- 데이터베이스: `frizer`
- 사용자: `frizer`
- 비밀번호: Git에서 제외한 `.env`의 `FRIZER_DB_PASSWORD` (기본값 없음)
- 호스트: `localhost`, 포트: `5432`
- 바인딩: `127.0.0.1`에만 노출
- 데이터: named volume `frizer_postgres_data` (Compose 프로젝트 접두사 적용)
- healthcheck: `pg_isready -U frizer -d frizer`

```powershell
docker compose logs postgres
docker compose stop
docker compose start --wait
```

`docker compose down`은 기본적으로 named volume을 보존합니다.
데이터 보존이 필요하면 volume 삭제 옵션을 사용하지 않습니다.

### 5432 포트가 사용 중인 경우

설치형 PostgreSQL이나 다른 컨테이너가 이미 5432를 사용하고 있을 수 있습니다.
기존 서비스를 종료하지 않고 이 프로젝트의 외부 포트를 바꿀 수 있습니다.

```powershell
Get-NetTCPConnection -LocalPort 5432 -State Listen -ErrorAction SilentlyContinue
$env:FRIZER_DB_PORT = '55432'
docker compose up -d --wait
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

동일 PowerShell 세션에서 설정하면 Compose와 애플리케이션에 같은 포트가 전달됩니다.
`.env.example`을 `.env`로 복사해 Compose 포트를 지정할 수도 있지만,
local 프로필은 명시적인 config import로 프로젝트 루트의 `.env`를 읽습니다. IntelliJ 실행 작업 디렉터리도 프로젝트 루트로 지정합니다. 환경변수를 별도로 설정하면 그 값이 우선합니다.

## profile과 환경변수

| 이름 | 적용 | 기본값 / 요구사항 |
| --- | --- | --- |
| SPRING_PROFILES_ACTIVE | 애플리케이션 | local 또는 prod를 명시. 자동 local 활성화 없음 |
| FRIZER_DB_PORT | Compose / local 앱 | 5432 |
| FRIZER_DB_URL | prod | 필수 PostgreSQL JDBC URL |
| FRIZER_DB_USERNAME | prod | 필수 |
| FRIZER_DB_PASSWORD | Compose / local / prod | 필수, 기본값 없음. local은 `.env` 사용 가능 |
| JAVA_HOME | Gradle 실행 | 설치한 JDK 경로 |

`application.yml`에는 공통 MyBatis, Flyway, 시간대 설정이 있습니다.
local은 로컬 DB 전용 접속값과 기능 Mapper 개발 로그를 사용합니다.
prod는 DB 환경변수를 요구하며 SQL 바인딩 값에 대한 DEBUG 로그를 기본 활성화하지 않습니다.
운영 비밀값은 저장소에 넣지 않습니다. `.env` 및 변형 파일은 `.gitignore`에서 제외합니다.
prod 설정은 향후 운영 실행을 위한 기반이며 배포 스크립트나 실제 배포는 포함하지 않습니다.

## DB 설계

STEP 3 1차의 V7부터 음식명·분류는 `food_master`, 구매·등록분은 `food_item`으로 분리합니다.
기존 ITEM ID와 이력을 유지하며 병합 요청 결과는 `food_merge_receipt`에 기록합니다.
아래 STEP 1·2 설명과 달라진 현재 구조와 전환 검증은 [STEP 3 보고서](STEP3_REPORT.md)를 참고합니다.

`src/main/resources/db/migration/V1__init_schema.sql`을 Flyway가 적용합니다.
기존 운영 DB에 적용된 migration을 이후에 수정하지 않고, 추가 변경은 V2 이상으로 작성합니다.

- `food_item`: 현재 상태. 같은 이름의 식품 복수 등록 허용.
- `food_history`: 행동·발생 시각·이전/신규 위치·당시 수량·행동 메모.
- PK: BIGINT identity. 식품 날짜는 DATE, 생성/수정 시각은 TIMESTAMPTZ.
- enum: VARCHAR + CHECK. PostgreSQL enum 타입은 사용하지 않습니다.
- 필수값: NOT NULL. 이름은 빈 문자열/일반 공백만 있는 값도 거부합니다.
- FK: `ON DELETE RESTRICT`, 연쇄 삭제 없음.
- 인덱스: ACTIVE 소비기한, ACTIVE 냉동 재고 냉동일, 이력 발생 시각 내림차순의 세 개.

### 확정된 냉동 상태 제약

| storage_type | freeze_type | frozen_at |
| --- | --- | --- |
| FREEZER | HOME_FROZEN 또는 COMMERCIAL_FROZEN | 날짜 또는 NULL |
| FRIDGE / ROOM | NONE | 반드시 NULL |

냉동실에서 NULL은 ‘냉동일 미상’입니다. 임의로 오늘로 대체하는 DB DEFAULT는 없습니다.
`freeze_type DEFAULT NONE`은 비냉동 등록용 기본값이므로 FREEZER INSERT는 유형을 명시해야 합니다.
이 제약은 ACTIVE, CONSUMED, DISCARDED 모두 동일하게 적용됩니다.

### 시간 정책과 Domain

공통 `Clock`은 `Asia/Seoul`입니다. 등록 Service는 `LocalDate.now(clock)`으로 오늘을 구합니다.
DB 연결마다 `SET TIME ZONE 'Asia/Seoul'`을 적용합니다.
TIMESTAMPTZ는 시점을 저장하므로 표시할 때 한국 시간대로 변환하며, 입력 시각의 원래 오프셋 보존을 전제하지 않습니다.
`created_at`, `updated_at`은 NOT NULL 및 기본 현재 시각을 사용합니다.
`updated_at`은 자동 갱신 트리거가 없으며 향후 UPDATE SQL에서 명시적으로 갱신합니다.

FoodItem/FoodHistory는 불변 Java record입니다. MyBatis의 이름 기반 생성자 매핑과
underscore → camelCase 설정을 사용하며, XML에서 enum 이름 및 LocalDate/OffsetDateTime을 매핑합니다.

## 확정 정책과 구현 범위

등록 정책은 STEP 2에서 구현했습니다. 위치 이동·소비·폐기·대시보드 정책은 이후 단계에서 구현합니다.

### 등록과 보관 위치

- 음식명·보관 위치·수량은 신규 등록 시 필수. 수량은 자유 문자열이며 용량은 선택 자유 문자열(예: 2인분, 300g). 수량·용량으로 자동 차감하거나 계산하지 않음.
- 출처 미입력은 ETC. 배달 잔반 출처의 기본 위치는 FREEZER지만 사용자가 명시한 위치가 우선.
- 배달 잔반 + FREEZER는 HOME_FROZEN으로 정규화. 그 외 FREEZER의 유형 미입력도 HOME_FROZEN.
- 시판 냉동식품은 COMMERCIAL_FROZEN을 명시적으로 선택.
- 기존 냉동 재고를 등록하면서 날짜를 모르면 NULL 유지, 화면에는 ‘냉동일 미상’.
- 새 냉동 처리 시 한국 기준 오늘. 새로 구매한 시판 냉동식품은 우리 집 냉동 보관 시작일을 기록.
- 기존 재고 등록에서 비어 있는 냉동일을 새 냉동 처리와 혼동하여 자동 보정하지 않음.
- FRIDGE/ROOM → FREEZER: HOME_FROZEN, 오늘, FREEZE 이력.
- FREEZER → FRIDGE/ROOM: NONE, NULL, MOVE 이력.
- FRIDGE ↔ ROOM: MOVE. 같은 위치로의 요청은 변경과 이력 없음.
- 최초 등록은 위치와 무관하게 CREATE 이력 1건만 작성.
- 날짜 미입력은 NULL. 구매·개봉·냉동일의 미래 날짜는 서버 검증에서 거부하고, 과거 소비기한 등록은 허용.

### 소비·폐기 및 History

- 물리 삭제 없음. 소비·폐기는 각각 CONSUMED/DISCARDED로 변경.
- 실행 전 확인 절차 제공. V1에 취소·복원 없음.
- 소비·폐기 이후 마지막 storage_type, freeze_type, frozen_at 유지.
- ACTIVE 조건부 UPDATE가 1건 성공했을 때만 History INSERT.
- 상태 변경과 History INSERT는 동일 Service 트랜잭션. 이력 실패 시 상태 변경도 롤백.
- 동일 완료 요청은 추가 변경과 이력 없음. 완료된 항목의 다른 상태 전환·이동·일반 수정은 허용하지 않음.
- History의 수량은 행동 당시 문자열이며, 메모는 행동 메모. 이름은 현재 FoodItem에서 조회.
- 일반 정보 수정 이력, 과거 냉동일 복원은 지원하지 않음.
- CREATE의 이전 위치는 NULL, 새 위치는 등록 위치.
- CONSUME/DISCARD의 이전·신규 위치는 같은 마지막 위치.
- History는 append-only 정책이며 이후에도 수정·삭제 Mapper를 제공하지 않음.

DB FK와 CHECK만으로 관리자 SQL의 모든 삭제나 수정이 금지되는 것은 아닙니다.
현재 migration은 append-only 트리거나 전용 권한 분리를 추가하지 않습니다.
상태 전이·중복 방지·트랜잭션 동작의 구현과 테스트는 Service 단계에서 진행합니다.

### 대시보드

- ACTIVE만 조회.
- 소비기한이 지난 식품은 상단 ‘소비기한 경과 — 섭취 여부 확인 필요’에 별도 표시.
- 경과 식품은 TOP 3에서 제외하고 섭취 대상으로 추천하는 문구를 사용하지 않음.
- 비경과 TOP 3 제목은 ‘오늘 먼저 먹을 음식’.
- 소비기한 없음은 경과로 간주하지 않음.
- 남은 일수 0일 +100, 1일 +90, 2~3일 +70, 4~7일 +40, 그 외/미상 0.
- HOME_FROZEN 배달 잔반은 냉동 30일 이상 +50, 14일 이상 +30 중 하나만 적용.
- 그 외 HOME_FROZEN은 냉동 90일 이상 +30. 배달 잔반과 중복 가산 없음.
- FRIDGE +10. 서로 다른 영역은 합산.
- 냉동일 NULL에는 냉동 경과 점수 없음. 소비기한 점수 등 다른 적용 가능한 점수는 유지.
- COMMERCIAL_FROZEN에는 냉동 경과 점수 없음.
- 동점: 소비기한, 냉동일, 생성 시각, ID 오름차순. 날짜 NULL은 뒤로.
- 양수 점수의 후보 중 최대 3개만 표시. 후보 부족 시 채우기 위한 무작위 추천 없음.
- 임박 영역은 오늘부터 7일 이내. 냉동일 미상 별도 알림/점수는 실제 사용 후 결정.
- 점수 숫자보다 우선 처리 이유 표시.

### 화면과 배포

주요 화면은 `/`, `/inventory`, `/inventory/new`, `/history` 네 개이며,
수정 화면은 향후 등록 폼을 재사용합니다. 목록 카드에서 주요 행동을 실행하도록 설계합니다.
PWA 명칭은 FR!ZER로 예약하되 STEP 1에서는 PWA 파일을 생성하지 않습니다.
인증 방식은 미결정이며 실제 Lightsail 공개 배포 전에 본인만 접근할 수 있는 방식을 확정해야 합니다.

## PostgreSQL 통합 테스트

```powershell
.\gradlew.bat clean test
```

Compose DB와 독립된 Testcontainers PostgreSQL을 동적 포트에 생성하므로 로컬 재고 DB를 변경하지 않습니다.
Docker가 없으면 실패하며 `disabledWithoutDocker`로 성공처럼 건너뛰지 않습니다.
각 테스트는 트랜잭션 롤백으로 격리하며 identity 번호의 연속성을 가정하지 않습니다.

테스트 대상:

- Spring Context, DB 연결, Flyway 적용/검증, 두 테이블 및 인덱스.
- XML Mapper 로딩과 SQL 실행.
- 보관 위치 3종 × 냉동 유형 3종 × 날짜 유무 2종 × 상태 3종 = 54개 제약 조합.
- 알려진/미상 냉동일, 소비·폐기 상태의 마지막 냉동 정보 보존 가능 여부.
- enum, LocalDate, OffsetDateTime의 시점, nullable 필드, record 이름 기반 매핑.
- 동일 이름 허용, 기본값, 필수값/enum 오류 거부, 이력 전이 CHECK, FK.

2026-09-13 Java 21 및 Docker PostgreSQL 17.11에서 파라미터화된 경우까지
97개 테스트를 실제 실행하여 모두 통과했습니다. 실패·오류·건너뜀은 각각 0개입니다.
테스트 전용 Mapper/XML은 `src/test`에만 있어 배포 JAR에는 포함되지 않습니다.

## 프로젝트 구조

```text
frizer/
├── README.md                          # 문서 진입점
├── docs/                              # 프로젝트 안내와 단계별 보고서
├── build.gradle, settings.gradle, gradlew, gradlew.bat
├── gradle/wrapper/
├── compose.yml
├── src/main/java/com/euiseon/friger/
│   ├── FrizerApplication.java
│   ├── common/config/, common/type/
│   ├── inventory/
│   │   ├── entity/FoodItem.java
│   │   ├── dao/InventoryDao.java
│   │   ├── service/InventoryService.java
│   │   ├── exception/InvalidFoodException.java, FoodNotFoundException.java
│   │   ├── controller/InventoryController.java
│   │   └── dto/FoodCreateForm.java
│   └── history/
│       ├── entity/FoodHistory.java
│       └── dao/HistoryDao.java
├── src/main/resources/
│   ├── application*.yml, messages.properties
│   ├── db/migration/ (V1 초기 스키마, V2 출처·용량, V3 출처 메모, V4 유통기한)
│   ├── mapper/inventory/, mapper/history/
│   ├── templates/inventory/list.html, new.html, detail.html
│   └── static/css/app.css, static/js/food-form.js
└── src/test/
    ├── java/com/euiseon/friger/smoke/    # STEP 1 검증
    ├── java/com/euiseon/friger/inventory/ # STEP 2 통합 검증
    └── resources/mapper/smoke/
```

STEP 2 결과는 브라우저에서 확인한 뒤 사용자 검토를 거쳐 커밋합니다.
다음 기능 단계와 원격 push는 별도 요청 시 진행합니다.
