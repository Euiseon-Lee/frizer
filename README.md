# FR!ZER

**프리!저 · FR!ZER — 배민 켜기 전에 잠깐**

1인 가구를 위한 냉장고 재고 관리 모바일 웹입니다.

- [프로젝트 안내 및 실행 방법](docs/README.md)
- [STEP 1 검증 결과](docs/STEP1_REPORT.md)
- [STEP 2 구현 및 검증 결과](docs/STEP2_REPORT.md)
- [쪼코 UI V3 개편 및 검증 결과](docs/UI_V3_REPORT.md)
- [쪼코 UI V4 사진·테마 갱신 결과](docs/UI_V4_REPORT.md)
- [쪼코 UI V5 Final 적용 결과](docs/UI_V5_REPORT.md)

현재 홈 요약·음식 등록·재고 목록과 위치 필터·음식 상세·기록 조회를 제공합니다.
local 프로필로 실행한 뒤 `http://localhost:8080/`에서 확인할 수 있습니다.

Java 기본 패키지는 `com.euiseon.friger`입니다. 식품과 이력 모델은 각각
`inventory.entity.FoodItem`, `history.entity.FoodHistory`에 둡니다.
