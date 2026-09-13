# 쪼코 이미지 최종 사용자 정책

갱신일: 2026-09-14. 승인된 기존 21장 전체 후보에 추가 ZIP 20개 행을 반영한다.
최신 추가 지침: [ADD_INSTRUCTIONS.md](image-add/ADD_INSTRUCTIONS.md), [원본 매핑](image-add/image-mapping.csv).
추가 요청이 이전 프로필 고정·오류 네 장·로드 실패 재시도 횟수보다 우선한다. 기존 파일은 보존하며 사용처는 아래 영역별 제외 규칙을 적용한다.

## 이미지와 분류

- PROFILE: happy-closeup.png, back-view-harness.png
- WAITING: puppy-sit.png, empty-curious.png, extra-chin-on-paw.png, extra-puppy-gaze.png, pink-coat-look-back.png
- REST: cozy-curl.png, extra-curled-smile.png, leaf-hat-front.png, leaf-hat-side.png, rest.png, sniff.png, flower-sniff.png, side-rest.png, belly-up-lounge.png, flower-collar-sit.png, plastic-flower-hat.png, striped-socks-puppy.png, snack-ring-tilt.png
- NEUTRAL: proud-sit.png, puppy-tilt.png, puppy-ready.png, look-aside.png, calm-closeup.png, red-collar-puppy.png, puppy-paw-reach.png, puppy-look-down.png
- HAPPY: come-running.png, extra-sunny-sit.png, extra-tongue-step.png, happy-lounge.png, happy-sit.png, puppy-front-paws.png, happy-run-front.png, belly-up-play.png

분류는 UI 분위기이며 실제 동물의 감정 판정이 아니다.

## 특별 정책

- pink-coat-look-back: WAITING, ERROR/EMPTY에서 사용. 일반 헤더 제외. 오류는 기존 네 장 + 이 이미지의 다섯 장만 순환.
- back-view-harness: happy-closeup과 함께 PROFILE 풀 구성. 세션당 한 장을 선택하고 페이지 이동·리렌더에도 유지.
- calm-closeup: NEUTRAL. 일반 헤더·확인·중립 안내에 사용하고 성공/오류 전용으로 분류하지 않음.
- happy-run-front는 come-running과 다른 사진이므로 별도 추가. 비슷한 포즈를 중복으로 처리하지 않음.
- 기존 leaf-hat-front/side와 신규 꽃·장식 다섯 장은 계절 제한 없이 헤더·편안한 상태에 사용.

## 영역별 순환

frizer.choco.scopes.v4의 독립 bag: PROFILE/HEADER/ERROR/EMPTY/SUCCESS/CALM/STORAGE_CALM/OBSERVE/EXPLORE/WIDE/PORTRAIT.
- 일반 헤더: 26장. WAITING·PROFILE·가로형 두 장과 puppy-tilt.png를 제외한다.
- 오류: WAITING 다섯 장만 사용. 프로필 두 장은 일반 후보에서 제외.
- 프로필: 두 장 중 세션 한 장 고정. 새 세션에서 다시 선택하며 같은 세션에서는 bag을 추가 소비하지 않음.
- 빈 상태: WAITING + REST. 요약 휴식: REST. 냉장/냉동 카드: REST에서 sniff.png와 rest.png를 제외한 STORAGE_CALM.
- 성공: HAPPY. WARNING·폐기 안내: NEUTRAL.
- WIDE는 기존 등록 배너에 연결: 기존 HAPPY 후보를 유지하고 side-rest/belly-up-lounge 추가.
- PORTRAIT은 실온 카드에 연결하며 sniff.png와 rest.png를 제외한다.
- 전체 카드는 EXPLORE이며 sniff.png와 rest.png를 제외한다. 새 페이지·재고 액션은 만들지 않음.

한 주기에서 모든 후보 사용 후 재셔플한다. 영역별 순환은 독립이며 화면 점유는 공유한다.
오류 우선 및 미사용 후보가 적은 영역 우선 배정. 기존 일반 영역은 입력·리렌더 중 유지한다.
후보가 다른 영역 점유·실패로 막히면 보류하고 사용 가능한 기존 후보를 일시 재사용한다.
막힌 미사용 후보는 다음 선택에 남겨 장기간 누락을 방지한다. 최근 사용만으로 숨기지 않는다.
이전 global v1/v2와 scopes v3는 제거하며, 손상된 bag은 초기화한다. 저장소 실패 시 메모리 fallback.

## 로드 실패와 배치

- 첫 로드 실패 시 같은 사용 그룹의 다른 파일로 한 번만 교체. 대체도 실패하면 무한 재시도하지 않음.
- 두 번 실패한 이미지 영역은 visibility:hidden으로 자리를 유지하고 문구·버튼은 보존.
- 프로필 대체 선택도 세션에 유지.
- 원본 PNG·투명도·방향 유지. JPG 변환·재압축·좌우 반전·cover/crop 없음.
- side-rest/belly-up-lounge는 넓은 빈 상태·배너·카드에 연결하고 가로형 CSS 크기를 별도 적용.
- 나머지는 contain 안에서 원본 비율 유지. 얼굴·귀·발·장식 추가 잘림 없음.

## 중복 판정 및 검증

바이너리/픽셀 동일성, 알파 외곽 기준 정규화 비교, 원본 사진 육안 대조를 함께 사용한다.
유사도 수치만으로 제외하지 않는다. 동일 원본 리사이즈 5건 재사용, 다른 원본 15건 추가.
ADDED 15 + REUSED 5 = 20, 누락 0. 전체 registry 36장, 미사용 0.
결과와 전후 해시는 [처리 기록](image-add/results.json), 구현·검증 보고는 [정책 보고서](FINAL_POLICY_REPORT.md).


## 영역별 제외 규칙

- sniff.png와 rest.png: 쪼코가 체크 완료! 네 카드에서만 제외. 헤더·빈 상태·요약 휴식 사용 유지.
- puppy-tilt.png: 모든 상단 헤더에서 제외. 카드·중립 안내 사용 유지.
- 후보 목록 변경 시 저장된 bag의 완전성을 재검증해 이전 후보 목록은 초기화한다.
