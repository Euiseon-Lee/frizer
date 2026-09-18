# FR!ZER 클라우드 실행·배포 안내

기준일: 2026-09-17. 이 문서는 현재 운영 환경을 직접 사용하고 다음 변경을 배포하기 위한 안내다. 비밀번호와 DB 접속 비밀값은 별도로 보관한다.

## 1. 평소에는 주소만 열면 된다

- 앱: https://frizer-y4tg.onrender.com
- 상태 확인: https://frizer-y4tg.onrender.com/health — 정상 응답은 `ok`다. 이 응답만으로 DB와 모든 기능이 정상이라고 판단하지는 않는다.
- Render 관리: https://dashboard.render.com/web/srv-dal9fo65vjqs73dnas60
- Neon 관리: https://console.neon.tech — 프로젝트 `broad-poetry-32715265`, 브랜치 `production`, DB `neondb`를 선택한다.

본인 계정 `choconuna`로 로그인하여 음식을 등록하면 된다. `testuser`는 별도의 테스트용 일반 사용자이며 서로의 음식·재고·이력은 공유하지 않는다. 두 계정 모두 요청한 비밀번호로 생성했다. 공개 회원가입은 없다. ADMIN/USER 등급의 기반만 있으며, 관리자가 모든 사용자의 데이터를 보는 기능은 아직 구현하지 않았다.

PC를 켜 두거나 IntelliJ·Docker·터미널을 실행할 필요가 없다. Render가 앱을 실행하고 Neon이 DB를 보관한다. GitHub는 배포할 소스코드를 보관한다.

Render Free는 요청이 없는 동안 절전 상태가 될 수 있어 첫 접속이 느릴 수 있다. 잠시 기다린 후 다시 확인한다. 재시작·새 배포 시 메모리에 저장된 로그인 세션은 사라질 수 있으므로 다시 로그인한다. 음식 데이터는 Neon에 남는다. 무료 서비스의 제한은 변경될 수 있으므로 [Render Free 안내](https://render.com/docs/free)를 참고한다. 무료 Render DB는 30일 만료이므로 만들지 않는다. [Neon 무료 한도](https://neon.com/pricing)와 Render 사용량은 콘솔에서 확인하며 유료 플랜으로 자동 전환하지 않는다.

## 2. 현재 운영 설정

| 항목 | 값 |
| --- | --- |
| 프로젝트 작업 위치 | `C:\dev\frizer` |
| 배포 브랜치 | `master` |
| 앱 실행 | Render Docker, Free, Singapore |
| 자동 배포 | CI 통과 시 master 자동 배포(2026-09-18 전환, `autoDeployTrigger: checksPass`) — GitHub Actions `CI` 워크플로(Java 전체 스위트+bootJar, JS 테스트)가 게이트다. 전환 커밋까지는 수동 배포로 내보내고, 첫 자동 배포 전에 Render 대시보드 Build & Deploy의 Auto-Deploy가 `After CI Checks Pass`인지 확인한다 |
| 프로필 | `prod,render` |
| DB | Neon PostgreSQL, 현재 Flyway V18 |
| 정상 확인 경로 | `/health` |
| 사용자 인증 도입 배포 기준 커밋 | `f08d701d9f904d257164d5fe5b079e0034108072` |
| 최신 배포 커밋 | `e17e44f` — 수량 분리·다건 병합 포함 (2026-09-17 사용자 확인) |

2026-09-17 운영 배포에서 Render Live와 V15 성공을 확인했다. 두 계정의 HTTPS 로그인, 홈·재고·히스토리·등록 화면 조회, 로그아웃을 확인했다. 운영 음식 등록·교차 계정 수정의 실데이터 검증, 실제 휴대폰 LTE/5G 접속, 백업 복구 실습은 별도로 남아 있다. 사용자 격리는 배포 전 자동 통합 테스트에 포함했다.

2026-09-17 사용자 확인: `06785a8` 운영 반영 완료. 휴대폰은 사용자가 실사용 중 확인하며 별도 당일 작업으로 잡지 않는다.

2026-09-17 사용자 확인: 이미지 로딩 최적화·홈 UI 정리·Render 주기 호출 커밋 `6b95748`을 Render 수동 배포했고 Live를 확인했다. 배포 후 실화면 확인(로그인, 주요 화면, WebP 이미지 로딩)은 3-5절 절차를 따르며 실사용 중 확인한다.

2026-09-17 사용자 확인: 파비콘·탭 제목, 코드 정리(V16), 수량 분리(V17), 다건 병합(V18)을 포함한 최신 커밋 `e17e44f`까지 운영 배포를 완료했다. 남은 1차 운영 작업(오등록 물리 삭제, 백업·복구 실습, 실데이터 검증)은 [STEP 4 보고서](STEP4_REPORT.md)를 따른다.

## 3. 변경한 코드를 운영에 배포하기

### 3-1. 프로젝트와 변경 파일 확인

PowerShell을 열고 다음을 실행한다.

```powershell
Set-Location C:\dev\frizer
git branch --show-current
git status --short
git diff --stat
git diff
```

브랜치가 `master`인지 확인한다. 다른 브랜치나 예상하지 않은 변경이 있으면 먼저 그 내용을 정리한다. `.env`, `.env.render`, 비밀번호, 로그, `build/` 결과물, 임시 파일은 커밋하지 않는다. `.gitignore`만 믿지 말고 커밋 대상 목록을 직접 확인한다.

자동 배포 전환(2026-09-18) 이후 master push = 운영 배포 요청이다. 작업은 기능 단위 브랜치에서 진행하고, master 머지를 배포 승인으로 취급한다. **DB 마이그레이션(V 파일)이 포함된 머지는 실데이터가 쌓인 뒤에는 머지 전에 `pg_dump` 백업을 먼저 만든다** — Flyway는 실패 시 트랜잭션 롤백되지만, 성공했는데 잘못된 마이그레이션은 백업으로만 되돌릴 수 있다. Neon의 시점 복구 보존 기간은 플랜에 따라 다르므로 콘솔에서 직접 확인하고, 복구 수단으로 단독 의존하지 않는다.

### 3-2. 테스트와 빌드

JDK 21, Node.js, Docker Desktop이 필요하다. Docker Desktop을 실행하고 아래 명령을 순서대로 실행한다. Java를 못 찾으면 `JAVA_HOME`을 설치한 JDK 21 폴더로 지정한다.

```powershell
java -version
.\gradlew.bat test bootJar
node --test src/test/js/*.test.cjs
```

명령이 실패하면 배포하지 말고 오류를 수정한다. Java 테스트는 Testcontainers의 독립 DB를 사용한다. 테스트를 위해 운영 DB 설정을 넣지 않는다. push 후에는 GitHub Actions `CI`가 같은 테스트(Java 전체 스위트+bootJar, JS 테스트)를 다시 돌려 통과한 커밋만 자동 배포된다. 로컬에 Node가 없으면 JS 테스트는 CI 결과로 확인한다.

### 3-3. 필요한 파일만 커밋·푸시

`git add`에는 실제 변경한 파일 경로를 하나씩 지정한다. 아래 `<...>`는 실제 경로와 메시지로 바꾼다. 그대로 실행하는 명령이 아니다.

```powershell
git add -- <확인한파일1> <확인한파일2>
git diff --cached --name-only
git diff --cached
git diff --cached --check
git commit -m "<확인한 커밋 메시지>"
git push origin master
git rev-parse HEAD
```

에이전트에 작업을 맡겼다면 커밋 전에 대상 파일과 메시지를 확인받도록 한다. 마지막 명령의 커밋 ID를 복사한다. **커밋과 push만으로는 현재 운영 앱이 바뀌지 않는다.**

### 3-4. Render에서 배포

1. 위 Render 관리 링크에서 `frizer` 서비스를 연다.
2. `Manual Deploy` → `Deploy a specific commit`을 선택한다.
3. 복사한 커밋 ID를 입력하고 해당 커밋을 선택한다.
4. `Deploy Commit`을 누른다.
5. 배포 로그를 확인한다. 빌드 성공 뒤 앱 시작까지 기다린다.
6. 상태가 `Live`이고 원하는 커밋 ID가 표시되는지 확인한다.

현재 배포의 예시는 [f08d701 배포 화면](https://dashboard.render.com/web/srv-dal9fo65vjqs73dnas60/deploys/dep-dalb7t65vjqs73eobvv0)에서 볼 수 있다. 버튼 이름과 배포 동작은 [Render 배포 문서](https://render.com/docs/deploys)를 참고한다.

### 3-5. 배포 후 직접 확인

1. `/health`에서 `ok` 응답을 확인한다.
2. 앱에 다시 로그인한다.
3. 홈, 재고, 히스토리, 음식 등록 화면을 연다.
4. 변경한 기능을 확인한다. 음식 등록을 확인할 때는 보관할 실제 음식으로 등록한다. 현재 물리 삭제 기능은 없다.
5. 프로필 화면의 로그아웃 버튼을 누르고 다시 로그인해 본다.

`BUILD SUCCESSFUL`만 보이면 아직 배포 완료가 아니다. Render `Live`와 실제 앱 동작까지 확인한다.

## 4. 코드 변경 없이 재시작하기

### UptimeRobot 주기 호출 — 현재 사용 중인 방법

UptimeRobot 무료 모니터가 5분 간격으로 공개 `/health`를 호출해 절전을 막는다. 설정: HTTP(s) 모니터, URL `https://frizer-y4tg.onrender.com/health`, 간격 5분. 서비스가 응답하지 않으면 이메일 알림이 온다. 관리는 https://uptimerobot.com 대시보드에서 한다. 중지하려면 모니터를 Pause한다.

### GitHub Actions 주기 호출 — 수동 실행 전용 예비

`.github/workflows/render-keepalive.yml`은 같은 5분 keep-alive를 GitHub Actions 예약 실행으로 시도한 것이다. 2026-09-17까지는 크론식 단순화(`*/5`) 재푸시, workflow Disable→Enable 토글, Actions 권한 확인에도 예약 실행이 발화하지 않아 UptimeRobot으로 전환했는데, 2026-09-18부터 예약 실행이 뒤늦게 발화하기 시작해 UptimeRobot과 5분 핑이 중복되고 실행 메일이 반복됐다. 방침대로 한쪽을 중지했다: schedule 트리거를 제거하고 수동 실행(Run workflow) 전용 예비로 유지한다. UptimeRobot이 유일한 상시 keep-alive다. UptimeRobot을 중단할 때만 schedule을 되살린다.

GitHub 예약 실행은 지연·누락될 수 있어 상시 가동을 보장하지 않는다. 공개 저장소는 60일간 저장소 활동이 없으면 예약 실행이 비활성화될 수 있다. 공개 저장소의 표준 러너는 무료지만 비공개 저장소는 계정의 Actions 사용량을 소비한다. 5분 간격은 하루 288회이며, 비공개 저장소라면 활성화 전에 사용량·예산을 확인한다. Render 무료 실행 시간은 workspace당 월 750시간을 공유한다. 이 호출은 Neon DB를 깨워 두는 기능은 아니다.

기준: [Render 무료 인스턴스](https://render.com/docs/free), [GitHub 예약 실행](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule), [Actions 사용량](https://docs.github.com/en/billing/concepts/product-billing/github-actions).

### 수동 재시작

Render 서비스의 `Manual Deploy` → `Restart service`를 선택하고 Live와 `/health`를 확인한다. 현재 배포된 커밋을 다시 실행하는 기능이므로 새 코드 반영에는 3절의 배포 절차를 사용한다. 재시작하면 사용자가 다시 로그인해야 할 수 있다.

환경변수를 변경한 경우에는 다음 절의 저장·배포 절차를 사용한다. 단순 재시작은 현재 배포의 환경값을 사용한다. [재시작 동작](https://render.com/docs/deploys#restarting-a-service)

## 5. 환경변수 확인·변경

Render 왼쪽 `Environment`에서 확인한다. DB 접속 계정과 앱 로그인 계정은 서로 다르다.

| 이름 | 용도 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod,render` |
| `JAVA_TOOL_OPTIONS` | 무료 인스턴스에 맞춘 JVM 메모리 설정. 현재 값 유지 |
| `FRIZER_DB_URL` | Neon 직접 연결 JDBC URL. TLS 인증서 검증 유지 |
| `FRIZER_DB_USERNAME`, `FRIZER_DB_PASSWORD` | Neon DB 접속 계정 |
| `FRIZER_LOGIN_USERNAME`, `FRIZER_LOGIN_PASSWORD` | 비어 있는 초기 소유자 계정의 최초 활성화에만 사용 |

이미 생성한 앱 계정의 비밀번호는 DB에 암호화된 해시로 저장된다. `FRIZER_LOGIN_PASSWORD`를 변경해도 기존 계정의 비밀번호는 바뀌지 않는다. 현재 운영 계정은 `choconuna`, `testuser`이며 초기 환경변수의 예전 로그인 이름과 다를 수 있다.

앱은 Hikari 3개 이하 연결을 직접 사용한다. Flyway migration과 연결별 시간대 설정 때문에 Neon의 `-pooler` URL을 사용하지 않는다. 기본 인증서를 신뢰하는 Java TLS 팩토리와 `sslmode=verify-full`을 사용하며 TLS 검증을 끄지 않는다. Neon Free의 기본 절전 정책은 유지한다.

환경값 변경 후 `Save and deploy`를 선택하면 기존 빌드에 새 환경값이 적용된다. `Save only`는 저장만 하므로 즉시 실행에 반영되지 않는다. 코드를 다시 빌드해야 하는 변경이면 `Save, rebuild, and deploy`를 사용한다. [환경변수 저장 옵션](https://render.com/docs/configure-environment-variables)

`.env.render`는 로컬 운영 도구가 사용하는 비밀 파일이며 Render 설정과 자동 동기화되지 않는다. DB 자격증명을 변경하면 필요한 양쪽 설정을 맞춘다. 값을 채팅·문서·스크린샷·Git에 남기지 않는다. 이 파일을 Java `spring.config.import`로 직접 읽히면 따옴표가 JDBC URL에 포함될 수 있으므로 계정 추가에는 아래 스크립트를 사용한다.

## 6. 일반 사용자 계정 추가

V15 배포와 초기 소유자 활성화가 완료된 운영 DB에서 사용한다. JDK 21과 운영과 호환되는 JAR, 최신 `scripts/add-user.ps1`, 올바른 `.env.render`가 필요하다.

```powershell
Set-Location C:\dev\frizer
.\gradlew.bat bootJar
.\scripts\add-user.ps1 -EnvironmentFile C:/dev/frizer/.env.render -LoginId newuser
```

`newuser`를 원하는 새 아이디로 바꾼다. 안내에 따라 비밀번호를 두 번 입력한다. 입력 내용은 화면에 표시되지 않는다. 아이디 앞뒤에 공백을 넣지 않고 100자 이내로 입력한다. 비밀번호는 10자 이상으로 입력한다. 너무 긴 비밀번호는 앱이 거부하므로 짧게 조정한다.

성공 메시지를 확인한 뒤 앱에서 새 계정으로 로그인한다. 이 명령은 USER 계정만 추가하고, 이미 존재하는 아이디는 덮어쓰지 않는다. Flyway 스키마 변경과 웹 서버 실행도 하지 않는다. 기존 계정의 비밀번호 재설정·비활성화는 이 명령의 기능이 아니다.

`choconuna`와 `testuser`는 이미 생성했으므로 다시 추가하지 않는다. 명령을 실행하는 PC의 `.env.render`가 연결 대상을 결정한다. 로컬 개발용 `.env`와 혼동하지 않는다.

## 7. Neon DB 상태 확인

Neon Console에서 위 프로젝트 → `production` 브랜치 → `neondb`를 선택한 뒤 SQL Editor에서 다음 조회를 실행한다. 비밀번호 해시는 조회하지 않는다.

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank DESC LIMIT 5;

SELECT user_id, login_id, role, enabled
FROM app_user ORDER BY user_id;

SELECT user_id, count(*) AS item_count
FROM food_item GROUP BY user_id ORDER BY user_id;
```

음식이 없으면 마지막 조회는 결과가 없을 수 있다. 정상이다. 앱은 시작할 때 새 Flyway migration을 적용한다. 이미 적용한 V1~V18 파일을 고치지 않고 다음 변경은 V19 이후 새 파일로 추가한다.

음식 데이터가 쌓인 뒤 DB를 변경할 때는 먼저 백업하고 별도 DB에서 복원 가능 여부를 확인한다. Neon의 복구 가능 시점과 보존 기간은 계정 플랜과 설정을 직접 확인한다. 새 브랜치를 만들었다는 사실만으로 독립적인 장기 백업이 확보되었다고 판단하지 않는다. 현재 운영 DB를 대상으로 복원 실습을 하지 않는다.

백업은 아래 명령으로 만든다(2026-09-18 추가). Docker Desktop 실행과 `.env.render`가 필요하고, 로컬에 pg_dump를 설치하는 대신 서버와 버전이 맞는 postgres:17 이미지를 사용한다. 결과는 저장소 밖 `C:\dev\frizer-backups\frizer-날짜시각.dump`(`-Fc` 형식)로 저장되며 읽기 전용 작업이라 운영에 영향이 없다. 비밀번호는 명령행이 아니라 환경변수로만 전달된다.

```powershell
.\scripts\backup-db.ps1 -EnvironmentFile C:/dev/frizer/.env.render
```

복원 검증은 운영과 분리된 빈 DB(예: 로컬 compose Postgres의 새 데이터베이스)에 `pg_restore --no-owner --no-acl`로 수행하고, 음식·항목·이력 건수와 최신 migration을 확인한다. 백업 파일에는 개인 데이터와 계정 해시가 들어 있으므로 공유·커밋하지 않는다.

## 8. 문제가 생겼을 때

| 증상 | 확인 순서 |
| --- | --- |
| 첫 접속이 오래 걸림 | 절전 복귀를 기다린 뒤 `/health` → Render Logs 확인 |
| 빌드 실패 | 실패한 배포의 로그에서 최초 오류 확인 → 로컬 수정·테스트 → 새 커밋 배포 |
| 앱 시작 실패 | Render Logs에서 DB 연결·환경변수·Flyway 오류 확인 |
| `jdbcUrl`을 거부함 | JDBC URL 형식과 불필요한 따옴표 확인. 계정 추가는 최신 스크립트 사용 |
| DB 연결 실패 | Neon 프로젝트/브랜치/DB, DB 계정, Render 환경값 확인. TLS 검증을 끄지 않음 |
| 로그인 실패 | 요청한 앱 계정 정보로 로그인. 환경변수 변경으로 기존 비밀번호를 재설정할 수 없음 |
| 배포 후에도 화면이 예전 상태 | Live 커밋 ID와 push 여부 확인 → 브라우저 새로고침 |
| 다시 로그인하라고 나옴 | 재시작·재배포 후에는 정상일 수 있음. 반복되면 앱 로그 확인 |

로그는 Render의 `Logs` 또는 해당 배포 상세 화면에서 본다. 계정 추가 오류는 실행한 PowerShell에서 확인한다. 로그를 공유할 때 DB 접속 정보와 자격증명은 제거한다.

V15는 사용자 소유권을 추가했기 때문에 V14 시절 앱으로 단순히 되돌리면 정상 동작하지 않는다. Render의 앱 롤백은 DB 스키마나 데이터를 되돌려 주지 않는다. DB 변경 후 장애가 나면 추가 입력을 멈추고 원인을 확인해 호환되는 수정 버전을 배포한다. `Flyway clean`, 임의 migration 삭제·수정, 무조건적인 repair로 우회하지 않는다.

## 9. 새 환경을 다시 만들 때

현재 사용자는 이미 구성된 서비스를 이용하면 된다. 아래는 재구축 시 설정을 빠뜨리지 않기 위한 순서다.

1. Neon에서 별도 프로젝트/브랜치와 PostgreSQL DB를 준비하고 직접 연결 정보를 확보한다. 기존 운영 DB를 쓸지 새 DB를 쓸지 먼저 구분한다.
2. Render에서 GitHub 저장소를 연결한 Web Service를 만들고 Docker, `master`, Singapore를 선택한다. 설정 기준은 저장소의 `render.yaml`이다.
3. 5절의 환경변수와 `/health`를 설정한다. 새로운 빈 DB일 때만 최초 앱 계정 값을 지정한다. 환경변수 값 칸에 불필요한 바깥 따옴표를 붙이지 않는다. Neon의 `DATABASE_URL_UNPOOLED`로 `node scripts/prepare-render-env.mjs`를 실행하면(Node 20.12 이상) 비밀 설정 파일 `.env.render`를 생성할 수 있다. 기존 파일은 자동 덮어쓰지 않는다.
4. 배포하고 Live, Flyway 적용, HTTPS 로그인, 주요 화면을 확인한다.
5. 필요한 계정은 6절의 도구로 추가한다. 새 환경용 비밀 파일은 운영 파일과 분리한다.

`neon deploy`는 Neon 쪽 설정을 적용하는 명령이며 Spring Boot 앱을 Render에 배포하는 명령이 아니다. 실제 앱 배포는 3절을 따른다.

후속 보완: 계정 추가 스크립트의 따옴표 처리 수정은 testuser 생성으로 확인했다. 빈 카드 하단 여백 보완은 24개 배치 조합에서 수정 전후를 비교했다. 실제 최신 운영 버전은 Render Live의 커밋 ID와 GitHub master를 비교해 확인한다.

## 10. 다른 PC에서 다음 작업 시작하기

운영 데이터를 사용하기만 한다면 앱 주소에 로그인하면 된다. 개발 작업을 이어가려면 아래 순서로 준비한다. 기존 PC의 실행 프로세스나 Codex 작업 폴더를 옮길 필요가 없다.

### 저장소와 도구 준비

Git, JDK 21, Node.js 24, Docker Desktop, PowerShell을 준비한다. GitHub 저장소에 접근할 수 있도록 해당 PC에서 로그인한다. Render·Neon 관리가 필요한 경우에도 각 서비스에 직접 로그인한다. 이 PC에 저장된 로그인 상태는 Git으로 전달되지 않는다.

새로 받는 경우 원하는 상위 폴더에서 실행한다.

```powershell
git clone https://github.com/Euiseon-Lee/frizer.git
Set-Location frizer
git switch master
git status --short
git log -3 --oneline
```

이미 저장소가 있다면 해당 폴더에서 먼저 변경 여부를 확인한다. 미커밋 변경이 있으면 보관 방법을 정리한 뒤 진행하며, 강제 초기화로 지우지 않는다.

```powershell
git status --short
git switch master
git pull --ff-only origin master
git log -3 --oneline
```

다른 PC에서는 실제 clone 경로를 사용한다. 이 문서의 C:\dev\frizer는 현재 PC의 경로다.

### 로컬 개발 환경 복원

1. 저장소 루트의 .env.example을 .env로 복사하고 로컬 DB 비밀번호와 로컬 로그인 초기값을 채운다. 기존 .env가 있다면 덮어쓰지 않는다.
2. JAVA_HOME을 그 PC에 설치한 JDK 21 폴더로 맞춘다. 기존 PC 전용 gradle.properties나 IDE 경로를 그대로 복사하지 않는다.
3. Docker Desktop을 실행하고 아래 명령으로 독립적인 로컬 DB와 앱을 시작한다. 운영 데이터를 로컬 DB로 자동 복사하지 않는다.

```powershell
docker compose up -d --wait
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

로컬 접속은 http://localhost:8080 이다. 포트를 이미 쓰고 있으면 [개발 안내](README.md)의 포트 설정 절차를 따른다. 로컬 설정으로 Neon production에 연결하지 않는다. 검증 명령은 3-2절을 사용한다. Node.js 도구 의존성이 필요한 작업은 저장소 루트에서 npm ci로 복원한다.

.env, .env.render, gradle.properties, build/, node_modules/, 개인 IDE 설정은 Git에 없다. 일반 개발에는 .env.render가 필요 없다. 운영 계정 추가 등 DB 관리 작업에만 해당 PC에 운영 비밀값을 안전하게 별도 준비한다. 메신저·커밋·인수인계 문서에 자격증명을 넣지 않는다. .env.render 작성 시 5절의 DB 환경변수를 넣고, 6절 스크립트를 사용한다.

### 다음 작업자가 알아야 할 상태

- 본인 데이터는 운영 choconuna 계정으로 계속 적재한다. testuser는 별도 USER 계정이다. 계정을 다시 만들거나 초기화하지 않는다.
- V15 사용자 소유권과 DB 인증은 운영 적용했다. 관리자의 전체 사용자 조회는 미래 설계이며 아직 기능이 없다. 공개 회원가입·비밀번호 재설정 UI도 없다.
- 히스토리 하단 세로형 이미지의 카드 아래 여백은 28px, 버튼과 이미지 사이는 20px이다. 재고·개별 구매 빈 카드 및 상단 이미지 배치는 비교 검증에서 유지됐다.
- 검증 기준(2026-09-18): Java 전체 스위트와 JAR 빌드 통과가 커밋 게이트다. JS 테스트(`node --test src/test/js/*.test.cjs`)는 최근 작업 PC에 Node가 없어 미실행 상태이며, 특히 item-move.test.cjs(대상 선택 개편으로 재작성)와 move-select.test.cjs(신규)는 Node 환경에서 실행 확인이 필요하다. 실기기 확인 대기: 모바일 밀도 축소·병합 대상 선택 개편.
- 수량 분리와 다건 병합은 구현·운영 배포했다. 오등록 물리 삭제는 [STEP 4](STEP4_REPORT.md)의 남은 작업이고, 통계·관리자 전체 사용자 조회 등은 [STEP 5 계획](STEP5_PLAN.md)의 2차 개발 범위다. 먼저 [앱 개념 문서](APP_CONCEPTS.md), [회귀 검증 범위](TEST_MATRIX.md), [UI 기준](UI_DESIGN.md)을 읽고 다음 작업 범위를 정한다.
- 작업은 clone한 원본 프로젝트에서 진행한다. 필요한 파일만 커밋하고 사용자에게 메시지를 확인받는다. 운영 반영은 master push 시 CI 통과 후 자동 배포된다(2026-09-18 전환) — 마이그레이션 포함 머지는 3-1절의 백업 원칙을 먼저 따른다.
