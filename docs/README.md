# FR!ZER

**프리!저 · FR!ZER — 쪼코와 함께하는 1인 가구 식량 재고 관리**

1인 가구의 냉장고 재고 누적과 냉동 악성재고를 최소한의 입력으로 관리하는 개인용 모바일 웹 프로젝트입니다.
재고의 정확한 수량보다 유지하기 쉬운 입력과 먼저 확인할 음식의 발견을 우선합니다.

## 현재 상태와 기준 문서

### 사용자 계정 전환 — 운영 반영 완료

다음 문단은 배포 전 당시 기록이다. 사용 목표는 **현재 Render + Neon 운영 DB에서 본인 계정으로 실제 재고를 적재하고, 테스트 계정 데이터는 분리하는 것**이다. 별도 로컬 DB로 사용처를 옮기지 않는다. `e6d5eaf` 운영 배포와 휴대폰 로그인 성공은 사용자 확인 사항이다. `d300d8a`(홈 상단 별 제거)를 보존한 원본 프로젝트 `C:\dev\frizer`에서 직접 후속 작업 중이며, 이번 사용자 테이블/V15 변경은 아직 커밋·푸시·운영 적용하지 않았다. 아래의 기존 ‘배포 준비 중/휴대폰 미확인’ 기록은 당시 기록이다.

- `app_user.user_id`는 불변 소유권 식별자다. `login_id`는 변경 가능한 로그인명이며 비밀번호는 BCrypt 해시로 저장한다.
- 등급은 `ADMIN`/`USER`를 저장한다. **이번 본인 계정과 테스트 계정은 USER**다. 관리자 전체 사용자 음식 접근은 후속 설계 범위이며 현재 관리자 화면·우회 접근·공개 회원가입은 없다.
- 기존 로그인 폼, 로그아웃, CSRF, 화면 양식을 유지한다. 인증된 principal의 `user_id`를 SQL에 바인딩한다. 폼/URL의 사용자 ID는 소유자로 받지 않는다. 비활성 계정은 다음 요청에서 세션 접근을 차단한다. 비밀번호·활성 상태·등급 변경은 `session_version`을 증가시켜 이전 세션을 무효화한다. 로그인명만 바꾸면 동일 사용자 소유권을 유지한다. 세션 저장소는 기존 서버 메모리이며 Render 재시작 뒤에는 다시 로그인한다.
- V15는 기존 9개 업무 테이블의 모든 데이터를 잠긴 전환용 계정 `user_id=1`에 귀속시킨다. 최초 기동에서 전달한 `FRIZER_LOGIN_USERNAME`/`FRIZER_LOGIN_PASSWORD`로 그 계정만 활성화한다. 기존 단일 계정을 유지할 때는 기존 값을 사용하고, 이번 빈 운영 DB에는 사용자가 지정한 본인 계정 값을 사용한다. 임의의 자격증명을 생성하지 않는다. 전환 후 환경변수를 바꾸어도 DB 비밀번호를 재설정하지 않는다.
- `food_master`, `food_item`, `food_history`, 등록·이동·수량·병합 영수증, 엑셀 미리보기·중복 영수증을 모두 소유자별로 분리한다. 엑셀의 기존 `owner_id` UUID는 브라우저 세션 검증용으로 유지하고 별도의 `user_id` FK가 계정 소유권을 맡는다.
- 목록·상세·이력·홈·추가 등록 선택지·엑셀 양식·수정·이동·병합·수량 처리·취소·영수증 재시도까지 서버에서 소유권을 적용한다. 요청 UUID와 엑셀 fingerprint의 유일성도 사용자별이다. 다른 사용자의 동일 파일은 중복 등록으로 막히지 않는다.
- `user_id`에 DB 기본값은 없다. 소유권 없는 새 INSERT는 실패한다. 음식→항목→이력, 취소 이력은 `(user_id, id)` 복합 FK로 다른 사용자 연결을 거부한다. 영수증의 과거 항목/음식 ID는 기존처럼 기록으로 보존하며 사용자 FK와 SQL 범위로 격리한다.

**운영 반영 완료 — 2026-09-17**

f08d701을 Render에 배포했고 Live 및 Neon V15 적용을 확인했다. 본인 계정 choconuna와 테스트 계정 testuser는 모두 USER로 활성화했다. 두 계정의 HTTPS 로그인, 홈·재고·히스토리·등록 화면 조회와 로그아웃을 확인했다. 실제 운영 음식 등록·교차 계정 수정, 휴대폰 LTE/5G 접속, 백업 복구는 아직 검증하지 않았다. 후속 변경에는 하단 이미지 카드 여백 보완과 운영 도구 수정이 포함된다. 최신 배포 여부는 Render Live 커밋 ID로 확인한다.

같은 날 추가 배포(사용자 확인): 여백·운영 도구 보완(`06785a8`), 이미지 최적화·keep-alive(`6b95748`), 파비콘·탭 제목, 코드 정리 V16, 수량 분리 V17, 다건 병합 V18을 포함한 최신 커밋 `e17e44f`까지 운영 배포를 완료했다. 현재 운영 DB는 Flyway V18이다. 단계 구분과 남은 1차 운영 작업(오등록 물리 삭제·백업 복구 실습·실데이터 검증)은 [STEP 4 보고서](STEP4_REPORT.md)를 따른다.

반복해서 따라 할 절차는 [클라우드 실행·배포 및 운영 안내](OPERATIONS.md)를 따른다. 비밀 파일과 임시 산출물은 커밋하지 않는다. 데이터가 쌓인 이후 DB 변경에는 백업·복구 검증을 적용한다.

**테스트 사용자 추가 방법**

V15 전환과 본인 계정 초기화가 끝난 대상에서 실행한다. 명령은 계정만 추가하며 Flyway는 실행하지 않는다. 비밀번호는 마스킹된 대화형 입력으로 두 번 받고, 인자/로그/채팅에 출력하거나 환경 파일을 변경하지 않는다. 이미 존재하는 ID는 실패하고 비밀번호를 덮어쓰지 않는다.

```powershell
# 운영에서는 사용자 승인 후에만 실행. 실행 전 대상 환경 파일을 확인한다.
./scripts/add-user.ps1 -EnvironmentFile C:/dev/frizer/.env.render -LoginId frizer-test
```

운영 전 검증은 Testcontainers의 독립 PostgreSQL에서 실행한다. 기존 8080 앱·공유 로컬 DB·Neon production은 이 검증의 대상이 아니다.

STEP 3(재고 관리 기능)은 2026-09-17 완료했다: 음식 그룹/개별 구매 관리, 신규/추가 등록, 수정, 다건 선택 이동·병합, 수량 분리, 부분 소비·폐기·제한적 취소, 종료 목록과 수량 변경 이력, 엑셀 일괄 등록(양식 다운로드·미리보기·전체 반영·중복 방지). 오등록 물리 삭제는 [STEP 4](STEP4_REPORT.md)로 이관해 1차 운영 마무리에 포함하고, 통계 등 2차 개발은 [STEP 5 계획](STEP5_PLAN.md)을 따른다.

- [앱의 용어·기본 구조·사용 흐름과 구현 상태](APP_CONCEPTS.md)
- [수량·소비·폐기·취소](ITEM_QUANTITY_DESIGN.md)
- [개별 구매 이동](ITEM_MOVE_DESIGN.md)
- [공통 UI 규격](UI_DESIGN.md)
- [회귀 검증 범위](TEST_MATRIX.md)
- [날짜 정책 초안](DATE_POLICY_DRAFT.md), [일괄 등록 규격](BULK_REGISTRATION.md)
- [쪼코 이미지 정책](UI_DESIGN.md)
- [STEP 4 — 계정·운영 배포와 1차 운영 마무리](STEP4_REPORT.md)
- [STEP 5 — 2차 개발 범위](STEP5_PLAN.md)

2026-09-16 이전 커밋 검증: Java 473개(실패/오류/건너뜀 0), JavaScript 41개(실패/건너뜀 0), bootJar 통과. 사용자가 8080에서 당시 UI를 확인했다. 모든 임의 수량·음식명 조합이나 실제 모바일 기기를 검증한 것은 아니다.

같은 날 후속 검증 문구·개인 로그인 변경: 전체 Java 496개(일괄 등록 44개·보안 18개 포함, 실패·오류·건너뜀 0), JavaScript 7개 파일, bootJar 통과. 실제 휴대폰 확인은 아직 수행하지 않았다.

Java/Mapper/V14는 앱 재시작 후 반영된다. 수량 UPDATE는 전후 수량·단위를 저장하며 과거 텍스트를 추정 변환하지 않는다. 취소 태그도 공통 Java 명칭으로 제공하므로 이전 앱은 재시작이 필요하다.

문서는 작업별로 새로 만들지 않고 위 기준 문서를 갱신한다. STEP 문서는 진행 중 단계의 작업 문서로만 유지한다. 단계가 완료되면 유지할 내용을 기준 문서로 흡수하고 STEP 문서는 제거하며, 과거 기록은 git 이력으로 보존한다(STEP 1·2 보고서와 완료된 STEP 3 보고서를 이 원칙으로 제거했다). 임시 화면·스크린샷·테스트 실행 덤프는 Git 제외된 build/reports에 둔다. 이미지 해시 JSON·매핑 CSV는 src/test/resources/choco에 테스트 입력으로 보존한다.

수정 완료 후에는 원래 목록 필터를 유지해 소속 음식의 개별 구매 목록으로 돌아간다. 수정 수량은 현재 잔량 정정이며 최초 등록 수량을 바꾸지 않는다. 통계는 2차 개발([STEP 5](STEP5_PLAN.md))이며, 초기 대량 데이터 입력을 위한 일괄 등록은 음식 등록 화면에서 사용할 수 있다.

## 백업과 복구

데이터가 쌓인 DB의 스키마 변경 전에는 `pg_dump -Fc`로 먼저 백업한다. 로컬 Docker DB의 바이너리 덤프는 PowerShell 텍스트 리다이렉션을 사용하지 않고 `docker cp`로 복사한다.

```powershell
docker compose exec -T postgres pg_dump -U frizer -d frizer -Fc -f /tmp/frizer-backup.dump
docker compose cp postgres:/tmp/frizer-backup.dump ./frizer-backup.dump
```

백업 파일은 개인 데이터이므로 Git에 추가하지 않는다. 실제 운영 백업은 서버와 다른 저장 장치에도 보관한다. 복구 시험은 빈 임시 DB에서 `pg_restore --no-owner --no-acl`로 수행하고 음식·항목·이력 개수와 최신 migration을 확인한다. 사용 중인 DB에 덮어써서 시험하지 않는다. 운영(Neon) DB의 백업·복구 실습은 [STEP 4 보고서](STEP4_REPORT.md)의 남은 작업이다.

2026-09-16의 배포 방식 비교, Render + Neon 최초 설정 순서와 당시 검증 기록은 git 이력으로 보존한다. 반복 배포·환경 재구축 절차는 [운영 안내](OPERATIONS.md)를 따른다.

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
최초 실행 시 `.env.example`을 `.env`로 복사하고 `FRIZER_DB_PASSWORD`와 `FRIZER_LOGIN_PASSWORD`(10자 이상)를 입력합니다.
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

로컬에서도 개인 로그인을 기본으로 활성화한다. Git에서 제외된 `.env`의 `FRIZER_LOGIN_USERNAME`과 `FRIZER_LOGIN_PASSWORD`(10자 이상)를 설정한 뒤 기존 8080 앱을 재시작한다. 로그인 전에는 `/login`으로 이동하며, 로그인 후 기존 로컬 재고를 확인할 수 있다. Render용 `.env.render`와 로컬 로그인 비밀번호는 별도로 관리한다.

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
현재 prod,render 프로필로 Render에서 운영 중입니다. 배포 절차는 [운영 안내](OPERATIONS.md)를 참고합니다.

## DB 설계

STEP 3 1차의 V7부터 음식명·분류는 `food_master`, 구매·등록분은 `food_item`으로 분리합니다.
기존 ITEM ID와 이력을 유지하며 병합 요청 결과는 `food_merge_receipt`에 기록합니다.
현재 구조와 전환 계약은 [앱 개념 문서](APP_CONCEPTS.md)를 참고합니다.

Flyway는 src/main/resources/db/migration의 V1부터 V18까지 순서대로 적용합니다.
기존 운영 DB에 적용된 migration을 이후에 수정하지 않고, 추가 변경은 V19 이후 새 버전으로 작성합니다.

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

이전 작업 기록(현재 구현 상태 아님) — 2026-09-16 로그인·통합 오류 화면 후속 점검: 전체 Java 496개(보안 18개), 실패·오류·건너뜀 0, bootJar 통과. 이후 히스토리 첫 기록의 상단 구분선만 CSS로 추가했다. 320px 로그인 오류 알림의 색상·가로 넘침·닫기 동작을 확인했다. 390px에서 긴 403 문구는 3줄로 자동 줄바꿈된다. 실제 휴대폰 LTE/5G, Render 실행·HTTPS 세션, 운영 백업 복구는 미검증이다. 세션은 메모리에 저장되어 앱 재시작 시 로그인이 풀리며, 회원가입·다중 계정·사용자별 재고 분리·로그인 시도 제한은 구현하지 않았다.

2026-09-17 원본 프로젝트 C:\dev\frizer 최종 검증: Java 515개, 실패·오류·건너뜀 0, JavaScript 7개 파일, bootJar 및 diff 공백 검사 통과. V15 전환·두 계정 격리·세션 검증 포함. 히스토리 빈 화면의 하단 쪼코 이미지에만 버튼 위 20px 간격을 추가했다. 이는 배포 전 검증 기록이다. 이후 운영 배포 결과는 문서 상단과 OPERATIONS.md를 따른다. 기존 로컬 8080 서버와 공유 로컬 DB는 변경하지 않았다.
