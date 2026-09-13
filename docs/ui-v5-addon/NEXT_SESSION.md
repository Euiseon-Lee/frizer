# 다음 작업 인계

기준: 2026-09-13. 사용자가 4개 단위의 커밋을 승인했으며 커밋 정리 후 새 채팅으로 이어간다. 다음 작업 시작 시 git status와 git log로 실제 커밋 여부부터 확인한다.

## 작업 위치와 실행

- 원본 프로젝트 C:\dev\frizer에서 작업한다. 별도 worktree는 사용하지 않는다.
- Java 21, Spring Boot, MyBatis, Thymeleaf 프로젝트다.
- 로컬 JDK: C:\dev\frizer\.gradle\jdks\jdk21.0.12_9
- 검증: node src/test/js/choco-selector.test.cjs 및 gradlew.bat test bootJar --offline
- 실제 앱: http://localhost:8080/ . 사용자 음식 데이터를 보존한다.
- .env, build, .gradle, 로컬 로그 등은 커밋하지 않는다.

## 이번 작업의 최종 상태

- 소비기한 경과 / 소비기한이 없는 경우 유통기한 경과 / 개봉 후 달력 기준 3개월 초과를 홈·목록·상세에서 동일하게 판정한다.
- 홈 한눈에 보기의 날짜·경고를 통합했다. 개봉 후 +N일은 개봉일부터의 전체 경과 일수다.
- 홈 최근 기록은 최신 5건, 한 줄 표시. 수정이 두 항목 이상이면 수정한 항목 N건, 한 항목이면 전후 값을 표시한다.
- 등록 시 이력에 snapshot-v1 JSON을 저장해 최초 입력값을 보존한다. 기존 이력은 당시 저장된 정보만 표시한다. 테이블 변경은 없다.
- 폼 검증 테두리·문구·오류 포커스는 #ff413b, field-error의 margin-left는 8px이다.
- 홈 보관 개수는 오른쪽 정렬, margin-right 18px, 상단 간격 12px이다.
- 홈 음식 등록하기 버튼은 + 제거, 내용 너비에 기본 패딩, 부모 div 기준 우측 정렬이다.
- 쪼코가 체크 완료! 카드: 실온 해 / 냉장실 바람 / 냉동실 눈송이 / 전체 하트. 모두 20.4px, 채움 없는 먹색 1px 선. 화살표 없음.
- 홈 WARNING은 #a62f27. 히스토리 상단은 홈과 같은 배경색.
- 히스토리 최근 기록 최대 100개는 우측 정렬, 우측 padding 16px, font-size 11px.
- 헤더 문구는 목록 집에 뭐 있더라?, 히스토리 어떻게 됐을까? 이다.
- 쪼코 정책은 FINAL_USER_SPEC.md가 최종 기준이다. 메타데이터와 최종 후보·검증·미리보기는 FINAL_POLICY_REPORT.md를 참고한다.
- 이미지 21장, emotionGroup/poseGroup/allowedRoles/renderMode 분리. 우선 표정 그룹을 모두 소진한 경우에만 차선 그룹으로 확장한다.
- 헤더 WAITING 금지, WARNING NEUTRAL 전용, 오류 본문 WAITING 전용. 프로필 happy-closeup 고정. 일반·빈 상태 전역 이력 공유, sessionStorage v2.
- 이미지 자체의 추가 재가공 없음. empty-curious는 승인된 투명 PNG 교체본이며 오류 하단 상반신 배치만 사용한다.

## 논의만 했고 아직 구현하지 않은 항목

- 목록 최근 등록 순은 고정 정렬 안내다. 최근 등록 순 / 오래된 순 / 유통기한 임박 순 / 소비기한 임박 순 / 개봉일 순 정렬 UI는 아직 구현하지 않았다. 음식명순은 추가 제안이었다.
- 분류의 드롭다운 전환과 통계 활용은 논의만 했다.
- 소비·폐기 완료 이미지 정책은 정의되어 있지만 해당 새 UI는 만들지 않았다.

## 승인된 커밋 분리

1. feat: 기한 및 개봉일 기반 확인 필요 음식 안내 통합
2. feat: 등록 시점 정보 보존과 최근 기록 요약 개선
3. feat: 쪼코 표정별 이미지 매핑과 전역 순환 정책 적용
4. style: 홈 카드와 입력 폼 및 히스토리 UI 정리

home.html, app.css, shell.html, InventoryIntegrationTest.java 등에 서로 다른 주제의 변경이 섞여 있어 파일 전체가 아닌 변경 구간 단위 분리가 필요하다. 원본 작업 파일을 유지하고 Git 인덱스에서 변경 구간을 분리해 커밋한다.