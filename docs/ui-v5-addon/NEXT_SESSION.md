# 다음 작업 안내

기준: 2026-09-14. 원본 C:\dev\frizer에서 작업하며 별도 worktree는 사용하지 않는다.

## 현재 상태

- 쪼코 추가 이미지15장, 동일 원본 재사용5장: ZIP20개 처리·누락0. 최종36장 모두 사용처가 있다.
- [최종 사용자 정책](FINAL_USER_SPEC.md)과 [구현·검증 보고서](FINAL_POLICY_REPORT.md)가 현재 이미지/UI 기준이다.
- HEADER26장, ERROR5장, PROFILE2장 세션 선택. 독립 bag과 같은 화면 중복 방지.
- sniff/rest는 보관 네 카드에서 제외. puppy-tilt는 모든 헤더에서 제외. 다른 사용처 유지.
- 전체 헤더 Grid 분리, 카드 첫 줄 아이콘·위치·숫자 및 하단 이미지, 숫자 단위 간격, 전체 보기 hover/focus, 수정 안내 문구 반영 완료.
- 현재 변경 범위는 음식별 구매 항목 관리·기존 음식 추가 등록·합치기와 후속 UI다. 승인된 커밋 제목은 `feat: 음식별 구매 항목 관리와 합치기 기능 구현`이다. 푸시는 범위에 포함하지 않는다. 커밋 상태는 git status/log로 확인한다.

## 실행 및 검증

- Java21/Spring Boot/MyBatis/Thymeleaf. 이 PC의 로컬 JDK는 C:/Users/Administrator/.jdks/azul-21.0.12.1이며 개인 경로는 Git 설정에 고정하지 않는다.
- node src/test/js/choco-selector.test.cjs, node src/test/js/food-merge.test.cjs 및 gradlew.bat test bootJar --offline --no-daemon --console=plain.
- 이 PC에서 Docker 확인 앱은 8082, 사용자 IntelliJ/bootRun은 8080을 사용한다. Git 제외 `.env`의 `FRIZER_APP_PORT=8082`로 분리했다. 사용자 확인용 8080을 점유하지 않는다.
- Docker는 `docker compose -f compose.yml -f compose.ui-dev.yml --profile app up -d --build --wait`로 시작한다. 이후 HTML·CSS·JS·이미지는 원본 마운트로 반영되므로 매번 재빌드하지 말고 새로고침으로 확인한다. Java·mapper·설정·migration 변경 시에만 빌드·재시작한다. 사용자 local 프로필도 UI 원본을 직접 읽으며 최초 설정 반영에는 재시작이 필요하다.
- .env·build·.gradle·로그·검수 임시 파일은 커밋하지 않는다.

## 다음 개발 범위

- [STEP3_REPORT](../STEP3_REPORT.md): 2026-09-14 음식 MASTER·구매분 상세·병합을 1차 구현했다. 잔량 작업·분리·소비·폐기·취소·오등록 삭제·엑셀은 후속 범위다.
- [일괄 등록 초안](../BULK_REGISTRATION_DRAFT.md): 첫 출시는 등록·추가 구매만 지원. 수정·소비·폐기는 후속 범위.
- V7 음식 그룹 전환 및 V8 출처 미선택 구분을 적용했다. 기존 음식 추가 등록을 구현했으며 이후 변경은 V9 이상으로 추가한다. 기존 항목을 이름 기준으로 자동 통합하지 않는다.
- STEP 보고서의 구현 결과/설계 및 확정 정책/자동 검증/브라우저 확인/남은 범위 규격을 유지한다. 합치기 서비스 검증과 브라우저/UI 검증을 구분해 기록한다.

- 합치기 선택·미리보기는 같은 화면에서 동작하며 완료 후 전체 음식 목록으로 이동한다. 기존 구매 항목을 옮기는 이동·분리 기능은 아직 후속 범위다.
