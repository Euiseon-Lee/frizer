# 다음 작업 안내

기준: 2026-09-14. 원본 C:\dev\frizer에서 작업하며 별도 worktree는 사용하지 않는다.

## 현재 상태

- 쪼코 추가 이미지15장, 동일 원본 재사용5장: ZIP20개 처리·누락0. 최종36장 모두 사용처가 있다.
- [최종 사용자 정책](FINAL_USER_SPEC.md)과 [구현·검증 보고서](FINAL_POLICY_REPORT.md)가 현재 이미지/UI 기준이다.
- HEADER26장, ERROR5장, PROFILE2장 세션 선택. 독립 bag과 같은 화면 중복 방지.
- sniff/rest는 보관 네 카드에서 제외. puppy-tilt는 모든 헤더에서 제외. 다른 사용처 유지.
- 전체 헤더 Grid 분리, 카드 첫 줄 아이콘·위치·숫자 및 하단 이미지, 숫자 단위 간격, 전체 보기 hover/focus, 수정 안내 문구 반영 완료.
- 코드·신규PNG·매핑 및 정책 문서가 커밋 대상이다. 사용자가 커밋 메시지를 승인했다. 승인된 제목은 feat: 쪼코 이미지 확장 및 헤더·보관 카드 UI 개선이다. 푸시는 이번 마감 범위에 포함하지 않는다. 실제 커밋 상태는 git status/log로 확인한다.

## 실행 및 검증

- Java21/Spring Boot/MyBatis/Thymeleaf. 로컬 JDK는 .gradle/jdks/jdk21.0.12_9.
- node src/test/js/choco-selector.test.cjs 및 gradlew.bat test bootJar --offline --no-daemon --console=plain.
- 현재8080은 build/resources/main을 사용한다. 템플릿·정적 리소스 변경은 processResources 후 새로고침한다. 다른 PC의 실행 방식은 별도 확인한다.
- .env·build·.gradle·로그·검수 임시 파일은 커밋하지 않는다.

## 다음 개발 범위

- [STEP3_REPORT](../STEP3_REPORT.md): MASTER/ITEM·잔량·분리·소비·폐기·취소·삭제 설계. 아직 구현하지 않았다.
- [일괄 등록 초안](../BULK_REGISTRATION_DRAFT.md): 첫 출시는 등록·추가 구매만 지원. 수정·소비·폐기는 후속 범위.
- 기존 Flyway V1~V6은 수정하지 않고 새 migration을 추가한다. 테스트 데이터 초기화는 선택 가능하나 아직 실행하지 않았다.
- STEP 보고서의 구현 결과/설계 및 확정 정책/자동 검증/브라우저 확인/남은 범위 규격을 유지한다. 이번 UI 검증을 STEP3 기능 검증으로 계산하지 않는다.
