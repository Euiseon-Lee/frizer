# FR!ZER

**프리!저 · FR!ZER — 쪼코와 함께하는 1인 가구 식량 재고 관리**

1인 가구의 냉장고 재고 누적과 냉동 악성재고를 최소한의 입력으로 관리하는 개인용 모바일 웹 프로젝트입니다.
재고의 정확한 수량보다 유지하기 쉬운 입력과 먼저 확인할 음식의 발견을 우선합니다.

## 현재 상태와 기준 문서

STEP 3은 음식 그룹/개별 구매 관리, 신규/추가 등록, 수정, 전체/개별 이동, 부분 소비·폐기·제한적 취소, 종료 목록과 수량 변경 이력을 구현했다. 엑셀 일괄 등록은 양식 다운로드·미리보기·전체 반영·중복 방지까지 구현했다. 통계·부분 분리·물리 삭제 등은 후속 범위다.

- [앱의 용어·기본 구조·사용 흐름과 구현 상태](STEP3_REPORT.md)
- [수량·소비·폐기·취소](ITEM_QUANTITY_DESIGN.md)
- [개별 구매 이동](ITEM_MOVE_DESIGN.md)
- [공통 UI 규격](UI_DESIGN.md)
- [회귀 검증 범위](STEP3_TEST_MATRIX.md)
- [날짜 정책 초안](DATE_POLICY_DRAFT.md), [일괄 등록 초안](BULK_REGISTRATION_DRAFT.md)
- [쪼코 이미지 정책](UI_DESIGN.md)

2026-09-16 최종 검증: Java 473개(실패/오류/건너뜀 0), JavaScript 41개(실패/건너뜀 0), bootJar 통과. 사용자가 8080에서 최종 UI를 확인했다. 모든 임의 수량·음식명 조합이나 실제 모바일 기기를 검증한 것은 아니다.

Java/Mapper/V14는 앱 재시작 후 반영된다. 수량 UPDATE는 전후 수량·단위를 저장하며 과거 텍스트를 추정 변환하지 않는다. 취소 태그도 공통 Java 명칭으로 제공하므로 이전 앱은 재시작이 필요하다.

문서는 작업별로 새로 만들지 않고 위 기준 문서를 갱신한다. 임시 화면·스크린샷·테스트 실행 덤프는 Git 제외된 build/reports에 둔다. 이미지 해시 JSON·매핑 CSV는 src/test/resources/choco에 테스트 입력으로 보존한다.

수정 완료 후에는 원래 목록 필터를 유지해 소속 음식의 개별 구매 목록으로 돌아간다. 수정 수량은 현재 잔량 정정이며 최초 등록 수량을 바꾸지 않는다. 통계와 종료 항목 이동은 운영 이후로 미루며, 초기 대량 데이터 입력을 위한 일괄 등록은 음식 등록 화면에서 사용할 수 있다.

## 운영 준비 현황 — 2026-09-16

- 일괄 등록은 `/inventory/bulk`에서 제공한다. 실제 초기 데이터 목록은 사용자가 나중에 정리한다.
- 최종 전체 검증은 Java 473개·JavaScript 41개 및 bootJar 통과다. 자동 냉동 보정의 안내 분기와 원본 엑셀 서식 보존 검증을 포함한다.
- 사용자 확인은 기존 로컬 8080 앱만 사용한다. 8082 앱은 종료했으며 다시 실행하지 않는다. Java 변경은 기존 IDE 실행을 재시작해 반영한다. 실제 운영용 실행에서는 `compose.ui-dev.yml`을 제외해 JAR에 포함된 화면을 사용한다.
- 현재 Compose는 앱·DB 포트를 127.0.0.1에만 연결한다. 같은 PC에서 사용 가능하며 휴대폰·외부에서는 직접 접속할 수 없다.
- 앱 계정 로그인과 사용자별 데이터 분리는 아직 없다. 외부 공개 전 인증과 HTTPS, 접근 범위를 정해야 한다. 일괄 등록의 세션 확인은 로그인 기능이 아니다.

### 배포 방식 선택

| 방식 | 준비할 내용 | 사용 범위 |
| --- | --- | --- |
| 현재 PC에서 먼저 사용 | Docker 실행 유지, 정기 백업, 운영 시 ui-dev 제외 | 해당 PC |
| 상시 켜진 개인 서버/NAS | Docker 실행 가능 여부, 저장 공간, 사설 접속 경로, 백업 위치 | 허용한 개인 기기 |
| 외부 서버 | 서버·도메인, 인증·HTTPS, 백업·복구, 운영 비용 | 정한 사용자에게 외부 접속 |

운영 대상은 아직 미정이다. 빠른 첫 사용은 현재 PC에서 시작할 수 있다. 휴대폰에서도 사용할 환경을 선택한 뒤 접속·인증 구성을 확정한다. 이 작업에서 외부 서버 생성이나 공개 배포는 하지 않았다.

### 백업과 복구

V14 적용 전 `pg_dump -Fc`로 현재 DB를 백업한다. 바이너리 덤프는 PowerShell 텍스트 리다이렉션을 사용하지 않고 `docker cp`로 복사한다.

```powershell
docker compose exec -T postgres pg_dump -U frizer -d frizer -Fc -f /tmp/frizer-backup.dump
docker compose cp postgres:/tmp/frizer-backup.dump ./frizer-backup.dump
```

백업 파일은 개인 데이터이므로 Git에 추가하지 않는다. 실제 운영 백업은 서버와 다른 저장 장치에도 보관한다. 복구 시험은 빈 임시 DB에서 `pg_restore --no-owner --no-acl`로 수행하고 음식·항목·이력 개수와 최신 migration을 확인한다. 사용 중인 DB에 덮어써서 시험하지 않는다.

분리·오등록 삭제를 첫 운영에 포함할지는 별도 범위 결정이 필요하다. 통계와 종료 항목 개별 이동은 운영 이후로 유지한다.

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
이 PC에서는 기존 로컬 8080 앱만 사용합니다. 별도 Docker 앱이나 8082 확인 서버를 시작하지 않습니다. 위 Docker 명령은 향후 실행 방식 전환 시 참고용이며, 현재 IDE 앱과 동시에 실행하지 않습니다.
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
현재 구조와 전환 계약은 [STEP 3 보고서](STEP3_REPORT.md)를 참고합니다.

Flyway는 src/main/resources/db/migration의 V1부터 V14까지 순서대로 적용합니다.
기존 운영 DB에 적용된 migration을 이후에 수정하지 않고, 추가 변경은 V2 이상으로 작성합니다.

- `food_item`: 현재 상태. 같은 이름의 식품 복수 등록 허용.
- `food_history`: 행동·발생 시각·이전/신규 위치·당시 수량·행동 메모.
- PK: BIGINT identity. 식품 날짜는 DATE, 생성/수정 시각은 TIMESTAMPTZ.
- enum: VARCHAR + CHECK. PostgreSQL enum 타입은 사용하지 않습니다.
- 필수값: NOT NULL. 이름은 빈 문자열/일반 공백만 있는 값도 거부합니다.
- FK: `ON DELETE RESTRICT`, 연쇄 삭제 없음.
- 인덱스와 요청 영수증 제약의 정확한 정의는 migration SQL을 기준으로 합니다.

### 확정된 냉동 상태 제약

| storage_type | freeze_type | frozen_at |
| --- | --- | --- |
| FREEZER | HOME_FROZEN 또는 COMMERCIAL_FROZEN | 날짜 또는 NULL |
| FRIDGE / ROOM | NONE | 반드시 NULL |

냉동실에서 NULL은 ‘냉동일 미상’입니다. 임의로 오늘로 대체하는 DB DEFAULT는 없습니다.
`freeze_type DEFAULT NONE`은 비냉동 등록용 기본값이므로 FREEZER INSERT는 유형을 명시해야 합니다.
이 제약은 ACTIVE, DEPLETED 모두 동일하게 적용됩니다.

### 시간 정책과 Domain

공통 `Clock`은 `Asia/Seoul`입니다. 등록 Service는 `LocalDate.now(clock)`으로 오늘을 구합니다.
DB 연결마다 `SET TIME ZONE 'Asia/Seoul'`을 적용합니다.
TIMESTAMPTZ는 시점을 저장하므로 표시할 때 한국 시간대로 변환하며, 입력 시각의 원래 오프셋 보존을 전제하지 않습니다.
`created_at`, `updated_at`은 NOT NULL 및 기본 현재 시각을 사용합니다.
시각·버전 갱신 및 재고 revision의 정확한 규칙은 최신 migration과 UPDATE SQL을 따릅니다.

FoodItem/FoodHistory는 불변 Java record입니다. MyBatis의 이름 기반 생성자 매핑과
underscore → camelCase 설정을 사용하며, XML에서 enum 이름 및 LocalDate/OffsetDateTime을 매핑합니다.

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

- src/main/java/com/euiseon/friger: 공통 설정, 재고·음식 그룹·이동·수량 서비스 및 이력
- src/main/resources/db/migration: 순차 스키마 변경
- src/main/resources/mapper: MyBatis SQL
- src/main/resources/templates, static: 서버 렌더링 화면·CSS·JS·쪼코 이미지
- src/test/java, src/test/js: 자동 회귀 테스트
- docs: 실행 안내·기준 설계·이미지 매핑 자료
- build/reports: 커밋하지 않는 검증 보고서·임시 화면
