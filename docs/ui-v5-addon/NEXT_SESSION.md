# 다음 작업 안내

기준: 2026-09-14. 원본 C:\dev\frizer에서 작업하며 별도 worktree는 사용하지 않는다.

## 최신 작업 정리

- [작업 정리 및 다음 논의](../SESSION_SUMMARY.md)를 먼저 읽는다. 반복 수정한 날짜 카드·버튼·경고 색상·이동 명칭은 이 문서의 최신 상태를 따른다. 마감 시 최종 UI 변경을 포함한 전체 Java 389개·JavaScript 4종·bootJar를 다시 검증하여 통과했다. 긴 이름 헤더와 WARNING 필터는 아직 미구현이다. 헤더는 최대 3줄·최소 16px 및 전체 이름 열기 안을 추후 재논의하도록 보류했고, 12px 축소는 채택하지 않았다.

2026-09-14 사용자가 오늘 변경 전체와 문서의 커밋을 승인했다. 승인된 제목은 `feat: 등록 중복 방지와 음식 이동 이력 및 구매 화면 개선`이다. 실제 커밋 상태는 git status/log로 확인한다. 푸시는 요청하지 않았다.

## 이전 작업 기록

- 히스토리 합치기 기록 구현: 기존 영수증을 조회해 과거 합치기도 표시하고 예전 이름의 ITEM 기록에는 현재 연결 음식명을 안내한다. 합치기 기록은 연속 합치기를 따라 현재 구매 목록으로 연결한다. 홈 최근 기록에도 포함한다. 추가 migration은 없으며 Java·Mapper 변경으로 사용자 앱 재시작이 필요하다. 전체 378개 및 320px 브라우저 검증 완료.
- 2026-09-14 현재 구현 범위 테스트 보강 완료. [테스트 목록](../STEP3_TEST_MATRIX.md)과 [전체 Java 실행 목록](../STEP3_TEST_CASES.md)을 추가했다. Java 378개·등록 UI JS 25개·이미지/합치기 JS·bootJar 통과. 등록 오류 화면 500·취소 링크 갱신·동일 UUID의 서로 다른 병합 경합 오류 3건을 수정했다. 이번 변경은 아직 커밋하지 않았으며 실제 상태는 git status/log로 확인한다.
- 신규 등록 중복 제출 방지 구현 완료: 클릭/Enter 직후 잠금, V9 요청 영수증으로 동일 ID·동일 입력의 동시/재전송 한 번만 저장, 다른 입력 거부, 실패 롤백 후 재시도. 새 ID는 같은 이름도 별도 등록한다. 사용자 안내는 '해줘' 및 '합치기'로 통일했다. 테스트용 18083 앱과 일회용 DB는 정리하며 사용자 앱은 재시작하지 않는다.
- 쪼코 추가 이미지15장, 동일 원본 재사용5장: ZIP20개 처리·누락0. 최종36장 모두 사용처가 있다.
- [최종 사용자 정책](FINAL_USER_SPEC.md)과 [구현·검증 보고서](FINAL_POLICY_REPORT.md)가 현재 이미지/UI 기준이다.
- HEADER26장, ERROR5장, PROFILE2장 세션 선택. 독립 bag과 같은 화면 중복 방지.
- sniff/rest는 보관 네 카드에서 제외. puppy-tilt는 모든 헤더에서 제외. 다른 사용처 유지.
- 전체 헤더 Grid 분리, 카드 첫 줄 아이콘·위치·숫자 및 하단 이미지, 숫자 단위 간격, 전체 보기 hover/focus, 수정 안내 문구 반영 완료.
- 현재 변경 범위는 음식별 구매 항목 관리·기존 음식 추가 등록·합치기와 후속 UI다. 승인된 커밋 제목은 `feat: 음식별 구매 항목 관리와 합치기 기능 구현`이다. 푸시는 범위에 포함하지 않는다. 커밋 상태는 git status/log로 확인한다.

## 실행 및 검증

- Java21/Spring Boot/MyBatis/Thymeleaf. 이 PC의 로컬 JDK는 C:/dev/frizer/.gradle/jdks/jdk21.0.12_9이며 개인 경로는 Git 설정에 고정하지 않는다.
- node src/test/js/choco-selector.test.cjs, node src/test/js/food-merge.test.cjs 및 gradlew.bat test bootJar --offline --no-daemon --console=plain.
- 추가 UI 검사: node src/test/js/registration-flow.test.cjs, node src/test/js/food-target-picker.test.cjs.
- 이 PC에서 Docker 확인 앱은 8082, 사용자 IntelliJ/bootRun은 8080을 사용한다. Git 제외 `.env`의 `FRIZER_APP_PORT=8082`로 분리했다. 사용자 확인용 8080을 점유하지 않는다.
- Docker는 `docker compose -f compose.yml -f compose.ui-dev.yml --profile app up -d --build --wait`로 시작한다. 이후 HTML·CSS·JS·이미지는 원본 마운트로 반영되므로 매번 재빌드하지 말고 새로고침으로 확인한다. Java·mapper·설정·migration 변경 시에만 빌드·재시작한다. 사용자 local 프로필도 UI 원본을 직접 읽으며 최초 설정 반영에는 재시작이 필요하다.
- .env·build·.gradle·로그·검수 임시 파일은 커밋하지 않는다.

## 다음 개발 범위

- [STEP3_REPORT](../STEP3_REPORT.md): 2026-09-14 음식 MASTER·구매분 상세·병합을 1차 구현했다. 잔량 작업·분리·소비·폐기·취소·오등록 삭제·엑셀은 후속 범위다.
- [일괄 등록 초안](../BULK_REGISTRATION_DRAFT.md): 첫 출시는 등록·추가 구매만 지원. 수정·소비·폐기는 후속 범위.
- V7 음식 그룹 전환, V8 출처 미선택 구분, V9 신규 등록 요청 중복 방지를 구현했다. 이후 변경은 V10 이상으로 추가한다. V9는 테스트 DB에서 적용·검증했으며 사용자 앱 재시작 때 적용된다. 기존 항목을 이름 기준으로 자동 통합하지 않는다.
- STEP 보고서의 구현 결과/설계 및 확정 정책/자동 검증/브라우저 확인/남은 범위 규격을 유지한다. 합치기 서비스 검증과 브라우저/UI 검증을 구분해 기록한다.

- 합치기 선택·미리보기는 같은 화면에서 동작하며 완료 후 전체 음식 목록으로 이동한다. 현재 원래 음식에 속한 구매 항목 전체 이동은 구현되어 있다. 일부 구매 항목 선택 이동·새 음식으로 분리는 후속 범위다.
