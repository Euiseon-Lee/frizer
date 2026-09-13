# 최종 쪼코 이미지 정책 및 검증

갱신일: 2026-09-14. [최종 정책](FINAL_USER_SPEC.md), [ZIP 추가 지침](image-add/ADD_INSTRUCTIONS.md).
기존 21장 원본을 보존하면서 추가 ZIP 특별 정책과 사용자 지정 영역별 제외 규칙을 반영했다.

## 구현 결과

**ADDED 15 + REUSED 5 = 20 / 누락 0 / 최종 이미지 36장 / 미사용 0.**
기존 21장의 SHA-256 동일성을 검증했다. 신규 15장은 ZIP 원본과 SHA-256이 같으며 재가공·덮어쓰기 없음.
이미지 선택기·프로필·헤더 및 보관 카드 배치·홈 링크 상호작용·수정 안내 문구를 변경했다. MASTER·ITEM·API·DB 변경은 없다.

## 20개 행 처리 결과

| ZIP 파일명 | 결과 | 최종 프로젝트 파일명 | 사용 그룹 | 중복 판정 근거 |
|---|---|---|---|---|
| calm-closeup.png | ADDED | calm-closeup.png | HEADER, OBSERVE, EXPLORE, PORTRAIT | 기존 전체 비교 후 다른 원본 확인; ZIP 해시 그대로 |
| happy-run-front.png | ADDED | happy-run-front.png | HEADER, SUCCESS, EXPLORE, WIDE, PORTRAIT | come-running과 발·꼬리 자세가 다른 별도 사진 |
| flower-sniff.png | ADDED | flower-sniff.png | HEADER, EMPTY, CALM, STORAGE_CALM, EXPLORE, PORTRAIT | 기존 전체 비교 후 다른 원본 확인; ZIP 해시 그대로 |
| side-profile-ear-up.png | REUSED | sniff.png | HEADER, EMPTY, CALM | 동일 원본 리사이즈, 정규화 MSE 5.306e-7 및 육안 일치 |
| front-paws-lounge.png | REUSED | happy-lounge.png | HEADER, SUCCESS, EXPLORE, WIDE, PORTRAIT | 동일 원본 리사이즈, 정규화 MSE 9.784e-7 및 육안 일치 |
| happy-sit-tilt.png | REUSED | happy-sit.png | HEADER, SUCCESS, EXPLORE, WIDE, PORTRAIT | 동일 원본 리사이즈, 정규화 MSE 7.675e-7 및 육안 일치 |
| red-collar-puppy.png | ADDED | red-collar-puppy.png | HEADER, OBSERVE, EXPLORE, PORTRAIT | 기존 전체 비교 후 다른 원본 확인; ZIP 해시 그대로 |
| puppy-paw-reach.png | ADDED | puppy-paw-reach.png | HEADER, OBSERVE, EXPLORE, PORTRAIT | 기존 전체 비교 후 다른 원본 확인; ZIP 해시 그대로 |
| belly-up-play.png | ADDED | belly-up-play.png | HEADER, SUCCESS, EXPLORE, WIDE, PORTRAIT | 기존 전체 비교 후 다른 원본 확인; ZIP 해시 그대로 |
| harness-puppy-sit.png | REUSED | puppy-ready.png | HEADER, OBSERVE, EXPLORE, PORTRAIT | 동일 원본 리사이즈, 정규화 MSE 6.352e-7 및 육안 일치 |
| puppy-look-down.png | ADDED | puppy-look-down.png | HEADER, OBSERVE, EXPLORE, PORTRAIT | 기존 전체 비교 후 다른 원본 확인; ZIP 해시 그대로 |
| side-rest.png | ADDED | side-rest.png | EMPTY, CALM, STORAGE_CALM, EXPLORE, WIDE | 기존 전체 비교 후 다른 원본 확인; ZIP 해시 그대로 |
| belly-up-lounge.png | ADDED | belly-up-lounge.png | EMPTY, CALM, STORAGE_CALM, EXPLORE, WIDE | 기존 전체 비교 후 다른 원본 확인; ZIP 해시 그대로 |
| flower-collar-sit.png | ADDED | flower-collar-sit.png | HEADER, EMPTY, CALM, STORAGE_CALM, EXPLORE, PORTRAIT | 기존 전체 비교 후 다른 원본 확인; ZIP 해시 그대로 |
| plastic-flower-hat.png | ADDED | plastic-flower-hat.png | HEADER, EMPTY, CALM, STORAGE_CALM, EXPLORE, PORTRAIT | 기존 전체 비교 후 다른 원본 확인; ZIP 해시 그대로 |
| striped-socks-puppy.png | ADDED | striped-socks-puppy.png | HEADER, EMPTY, CALM, STORAGE_CALM, EXPLORE, PORTRAIT | 기존 전체 비교 후 다른 원본 확인; ZIP 해시 그대로 |
| pink-coat-look-back.png | ADDED | pink-coat-look-back.png | ERROR, EMPTY | 기존 전체 비교 후 다른 원본 확인; ZIP 해시 그대로 |
| back-view-harness.png | ADDED | back-view-harness.png | PROFILE | 기존 전체 비교 후 다른 원본 확인; ZIP 해시 그대로 |
| happy-leash-step.png | REUSED | extra-tongue-step.png | HEADER, SUCCESS, EXPLORE, WIDE, PORTRAIT | 동일 원본 리사이즈, 정규화 MSE 1.094e-6 및 육안 일치 |
| snack-ring-tilt.png | ADDED | snack-ring-tilt.png | HEADER, EMPTY, CALM, STORAGE_CALM, EXPLORE, PORTRAIT | 기존 전체 비교 후 다른 원본 확인; ZIP 해시 그대로 |

중복 판정은 원본 알파 외곽을 기준으로 contain 정규화한 비교와 시각 검수를 함께 수행했다.
5건은 해시 자체가 같은 파일이 아니라 동일 사진의 리사이즈본이다. 비슷한 표정·포즈는 제외 근거로 사용하지 않았다.
원본 파일명·크기·원본/최종 SHA-256·판정 근거: [image-mapping.csv](image-add/image-mapping.csv), [results.json](image-add/results.json).

## 설계 및 확정 정책

최종 정책은 [사용자 정책](FINAL_USER_SPEC.md)을 따른다. 아래 후보는 현재 선택기와 대조해 정리했다.

| 그룹 | 연결 역할 | 후보 파일 |
|---|---|---|
| PROFILE | profile | happy-closeup.png, back-view-harness.png |
| HEADER | home-hero, list-header, detail-header, history-header, error-header, add-header, edit-header | cozy-curl.png, extra-curled-smile.png, leaf-hat-front.png, leaf-hat-side.png, rest.png, sniff.png, flower-sniff.png, flower-collar-sit.png, plastic-flower-hat.png, striped-socks-puppy.png, snack-ring-tilt.png, proud-sit.png, puppy-ready.png, look-aside.png, calm-closeup.png, red-collar-puppy.png, puppy-paw-reach.png, puppy-look-down.png, come-running.png, extra-sunny-sit.png, extra-tongue-step.png, happy-lounge.png, happy-sit.png, puppy-front-paws.png, happy-run-front.png, belly-up-play.png |
| ERROR | error | puppy-sit.png, empty-curious.png, extra-chin-on-paw.png, extra-puppy-gaze.png, pink-coat-look-back.png |
| EMPTY | empty | puppy-sit.png, empty-curious.png, extra-chin-on-paw.png, extra-puppy-gaze.png, pink-coat-look-back.png, cozy-curl.png, extra-curled-smile.png, leaf-hat-front.png, leaf-hat-side.png, rest.png, sniff.png, flower-sniff.png, side-rest.png, belly-up-lounge.png, flower-collar-sit.png, plastic-flower-hat.png, striped-socks-puppy.png, snack-ring-tilt.png |
| SUCCESS | success-create, success-add-stock, success-consume | come-running.png, extra-sunny-sit.png, extra-tongue-step.png, happy-lounge.png, happy-sit.png, puppy-front-paws.png, happy-run-front.png, belly-up-play.png |
| CALM | rest, loading | cozy-curl.png, extra-curled-smile.png, leaf-hat-front.png, leaf-hat-side.png, rest.png, sniff.png, flower-sniff.png, side-rest.png, belly-up-lounge.png, flower-collar-sit.png, plastic-flower-hat.png, striped-socks-puppy.png, snack-ring-tilt.png |
| OBSERVE | warning, success-discard | proud-sit.png, puppy-tilt.png, puppy-ready.png, look-aside.png, calm-closeup.png, red-collar-puppy.png, puppy-paw-reach.png, puppy-look-down.png |
| STORAGE_CALM | fridge, freezer | cozy-curl.png, extra-curled-smile.png, leaf-hat-front.png, leaf-hat-side.png, flower-sniff.png, side-rest.png, belly-up-lounge.png, flower-collar-sit.png, plastic-flower-hat.png, striped-socks-puppy.png, snack-ring-tilt.png |
| EXPLORE | all | cozy-curl.png, extra-curled-smile.png, leaf-hat-front.png, leaf-hat-side.png, flower-sniff.png, side-rest.png, belly-up-lounge.png, flower-collar-sit.png, plastic-flower-hat.png, striped-socks-puppy.png, snack-ring-tilt.png, proud-sit.png, puppy-tilt.png, puppy-ready.png, look-aside.png, calm-closeup.png, red-collar-puppy.png, puppy-paw-reach.png, puppy-look-down.png, come-running.png, extra-sunny-sit.png, extra-tongue-step.png, happy-lounge.png, happy-sit.png, puppy-front-paws.png, happy-run-front.png, belly-up-play.png |
| WIDE | banner | come-running.png, extra-sunny-sit.png, extra-tongue-step.png, happy-lounge.png, happy-sit.png, puppy-front-paws.png, happy-run-front.png, belly-up-play.png, side-rest.png, belly-up-lounge.png |
| PORTRAIT | room | cozy-curl.png, extra-curled-smile.png, leaf-hat-front.png, leaf-hat-side.png, flower-sniff.png, flower-collar-sit.png, plastic-flower-hat.png, striped-socks-puppy.png, snack-ring-tilt.png, proud-sit.png, puppy-tilt.png, puppy-ready.png, look-aside.png, calm-closeup.png, red-collar-puppy.png, puppy-paw-reach.png, puppy-look-down.png, come-running.png, extra-sunny-sit.png, extra-tongue-step.png, happy-lounge.png, happy-sit.png, puppy-front-paws.png, happy-run-front.png, belly-up-play.png |

미래 success-add-stock/success-consume/success-discard/loading/error-header는 역할 정의이며 새 UI는 구현하지 않았다. 모든 36장에는 현재 템플릿의 사용처가 있다.

- 영역별 독립 shuffle-bag, 같은 화면 중복 금지, 오류 우선 배정. 미사용 후보가 다른 영역에 점유되면 보류하고 가용 후보를 선택한다.
- 프로필은 두 장 중 세션 한 장 고정. ERROR는 WAITING 다섯 장만 사용한다.
- 첫 로드 실패 시 같은 그룹에서 한 번 대체하고 재실패 시 슬롯을 유지한다.
- sniff/rest는 네 보관 카드에서 제외, puppy-tilt는 HEADER에서 제외한다. 다른 사용처와 원본 파일은 보존한다.

## 화면 구현 결과

- 홈과 모든 tab-heading: 텍스트/이미지 Grid 3:2, 간격 12px. 이미지 absolute 배치를 해제하고 contain으로 비율 유지.
- 보관 카드: 첫 줄 아이콘·위치명·우측 숫자(개 제거). 하단 전체 폭 ×112px 이미지 행. 320px에서 위치명 15px·숫자 19px.
- 보관 중 3개 태그: 숫자와 단위를 붙여 표시, fit-content로 내용 길이에 맞춰 확장.
- 최근 기록 전체 보기: hover 색상·밑줄, 키보드 focus-visible 강조·테두리. reduced-motion 대응.
- 수정 화면 안내: 수정된 내용도 쪼코가 기억할게.
- 등록 배너 크기는 유지: 일반 88×100px/내부 폭 최대29%, 가로형 132×74px/최대38%.

## 자동 검증

- 이미지 선택기: ADDED15/REUSED5/누락0, 원본 해시, 36장 실제 사용처, HEADER26/ERROR5/PROFILE2 확인.
- 각 bag 12주기·홈 360회 배정, 중복 방지·저장소·로드 실패·프로필 유지·DOM 재생성·BFCache 회귀 검증 통과.
- Java 177개(InventoryIntegrationTest80 + DatabaseSmokeTest97), 실패·오류·건너뜀0: 2026-09-14 최종 마감 시 재실행 통과.
- 최종 마감 검증 명령: node src/test/js/choco-selector.test.cjs 및 gradlew.bat test bootJar --offline --no-daemon --console=plain. 2026-09-14 최종 실행 결과: 모두 통과, BUILD SUCCESSFUL. git diff --check도 통과했다.

## 브라우저 확인

- 실제8080 홈·목록·위치별 목록·등록·히스토리·상세·수정·오류 ×320/360/390/448/1280px: 40조합에서 겹침·이탈·가로 넘침 없음. 긴 음식명 줄바꿈 및 공통 로고/프로필 분리 확인.
- 이미지 추가 당시 8081에서 일반 헤더27장·오류5장 실제 순환을 확인했다. 이후 puppy-tilt 제외로 현재 헤더26장임은 자동 검증했다.
- 홈 이미지 후보27장 ×320/390/448px 검수 당시 81회 배정에서 슬롯 유지 및 sniff 겹침 없음.
- 보관 카드 숫자만 표시되는 것을8080에서 확인. 태그는 실제CSS 검수 화면의 1~10자리에서 약87~150px로 확장하고 내부 잘림 없음.
- 최신 hover/focus CSS가8080에 로드되는 것을 확인했다. 실제 마우스 hover 동작 자체는 별도 자동 조작하지 않았다.
- 현재8080은 build/resources/main을 사용한다. processResources 후 새로고침으로 변경 반영. 사용자 데이터 수정 없이 검수했다.

## 남은 범위

이번 UI 작업과 검증은 완료했으며 사용자가 커밋 메시지를 승인했다. 승인된 커밋 제목: feat: 쪼코 이미지 확장 및 헤더·보관 카드 UI 개선. 푸시는 이번 마감 범위에 포함하지 않는다.
STEP3 재고 기능과 엑셀 일괄 등록 구현은 별도 후속 작업이다.
