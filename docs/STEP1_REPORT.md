# FR!ZER STEP 1 검증 결과

검증일: 2026-09-13 (Asia/Seoul).

이 문서는 STEP 1 완료 당시의 검증 기록입니다. 이후 STEP 2에서 패키지와 문서 위치를 정리했으며,
현재 구현 및 실행 결과는 [STEP 2 보고서](STEP2_REPORT.md)를 참고합니다.

## 1. 결과

**STEP 1 완료.** 지정 경로 배치, Java 21 정식 Gradle 빌드, 실제 PostgreSQL 통합 테스트,
local 프로필 애플리케이션 기동을 검증했습니다. STEP 2는 진행하지 않았습니다.

- 원본: 사용자 제공 STEP 1 구현본의 `outputs/frizer` 폴더 (개인 절대 경로 생략).
- 현재 프로젝트: `C:\dev\frizer`
- 원본 README/보고서와 전체 소스·설정·테스트 확인 후 28개 파일 복사 및 SHA-256 일치 확인.
- 원본의 `.gradle` 캐시는 복사하지 않았으며 대상의 기존 `.idea`는 유지.
- 구현 오류가 없어 Java, SQL, MyBatis XML, build.gradle은 원본 그대로 유지. 문서를 현행화하고,
  IntelliJ의 JDK 탐색 문제를 해결하기 위한 로컬 gradle.properties와 해당 Git 제외 규칙을 추가.
- 원본 폴더는 변경하지 않음. 사용자가 Git을 연결했으며 STEP 1 결과를 최초 로컬 커밋으로 정리.
  push와 배포는 수행하지 않음.

## 2. 검증 환경

| 구성 | 실제 사용 값 |
| --- | --- |
| OS | Windows 11 amd64 |
| Java | Amazon Corretto 21.0.12.1, build 21.0.12.1+9-LTS |
| JAVA_HOME | `C:\dev\frizer\.gradle\jdks\jdk21.0.12_9` |
| Gradle Wrapper | 8.13 |
| Spring Boot | 3.5.16 |
| MyBatis Starter / Core | 3.0.5 / 3.5.19 |
| Flyway | 11.7.2 |
| PostgreSQL JDBC | 42.7.11 |
| Testcontainers | 1.21.4 |
| Docker Desktop / Engine | 4.69.0 / 29.4.0, Linux engine |
| PostgreSQL | `postgres:17.11` |

JDK는 공식 Amazon Corretto 다운로드에서 받아 `.gradle/jdks`에 압축 해제했습니다.
시스템 환경변수는 변경하지 않았습니다. JDK는 Git 제외 로컬 도구이며 `gradlew clean`으로 삭제되지 않습니다.
초기 제한 환경에서 Docker named pipe 접근이 거부되어 승인된 권한 확대로 검증했습니다.
필요한 의존성과 컨테이너 이미지를 정상 확보했으며 이전 인계 문서의 환경 장애는 해소됐습니다.

IntelliJ 추가 진단: IDE 로그에서 Gradle 실행 JDK가 azul-17이며 Java 21 toolchain을
발견하지 못해 compileClasspath 동기화가 실패한 것을 확인했습니다.
PC 전용 `gradle.properties`의 `org.gradle.java.installations.paths`에 위 JDK 경로를 지정했습니다.
이 파일은 Git에서 제외합니다. IntelliJ에서 Gradle 프로젝트를 다시 동기화해야 변경이 적용됩니다.

## 3. 실제 검증 결과

| 검증 | 결과 |
| --- | --- |
| 소스 이관 / 원본 대조 | 28개 파일 SHA-256 일치 (문서 갱신 전) |
| `java -version`, `gradlew.bat --version` | Java 21 / Gradle 8.13 확인 |
| `docker compose config --quiet` | 성공 |
| `gradlew.bat clean test bootJar --no-daemon --console=plain` | BUILD SUCCESSFUL, 44초, 8개 task 실행 |
| PostgreSQL 통합 테스트 | **97개 실행, 성공 97, 실패 0, 오류 0, 건너뜀 0** |
| `docker compose up -d --wait` | PostgreSQL healthy |
| `gradlew.bat bootRun --args='--spring.profiles.active=local' --no-daemon --console=plain` | Java 21, Tomcat 8080 정상 기동 |
| local DB Flyway | V1 init schema 적용 성공, success=true |
| local DB 테이블 | food_item, food_history, flyway_schema_history 확인 |
| `GET http://localhost:8080/` | 예상 HTTP 404 (Controller 미구현 단계) |
| bootJar 내용 | 도메인·프로필·V1 포함, 테스트 SmokeMapper/XML 제외 |

테스트는 Compose DB와 독립된 Testcontainers PostgreSQL에서 실행됐습니다.
Spring Context, Flyway 적용/검증, XML SQL 실행, 세 인덱스, 냉동 정책 54조합,
enum/날짜/시점/nullable record 매핑, 기본값·동일 이름 허용, 필수값·이력 전이·FK 제약을 검증했습니다.
로컬 DB의 food_item은 0건이며 테스트용 식품 데이터가 들어가지 않았습니다.

결과 파일:

- `build/reports/tests/test/index.html`
- `build/test-results/test/TEST-com.euiseon.friger.smoke.DatabaseSmokeTest.xml`
- `build/libs/frizer-0.0.1.jar`

기동 로그의 MyBatis Mapper 미발견 및 Thymeleaf templates 미발견 경고는
기능 Mapper와 화면을 만들지 않은 STEP 1 범위에 따른 것입니다. 기동 실패가 아닙니다.

## 4. 재실행

기존 설치형 PostgreSQL이 5432 포트를 사용하므로 FR!ZER Compose DB는 55432에 바인딩했습니다.
기존 서비스와 DB는 변경하지 않았습니다. PowerShell에서 다음을 실행합니다.

```powershell
Set-Location C:\dev\frizer
$env:JAVA_HOME = 'C:\dev\frizer\.gradle\jdks\jdk21.0.12_9'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:FRIZER_DB_PORT = '55432'
docker compose up -d --wait
.\gradlew.bat clean test bootJar
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

통합 테스트만 실행할 때는 Compose DB 기동이 필요 없지만 Docker Linux 엔진은 필요합니다.
검증 후 앱과 이번에 만든 Compose 컨테이너는 종료하며 named volume의 V1 스키마는 보존합니다.

## 5. 범위

추가 설정 정리: Compose와 local 프로필의 하드코딩 비밀번호를 제거하고
`FRIZER_DB_PASSWORD`를 필수로 사용합니다. local 프로필은 프로젝트 루트 `.env`를 명시적으로 읽습니다.
현재 PC의 기존 DB 접속값은 Git 제외 `.env`에만 보관하며 `.env.example`의 비밀번호는 빈 값입니다.
기존 볼륨의 DB 계정 비밀번호 자체를 변경한 것은 아닙니다.

Controller, Service, 기능용 Mapper, DTO, 화면, PWA, AWS 배포는 추가하지 않았습니다.
상태 전이 Service 트랜잭션과 등록·목록 UI 등은 이후 단계의 작업입니다.
STEP 1에 남은 검증 장애는 없습니다. STEP 2는 별도 사용자 요청 전까지 진행하지 않습니다.
