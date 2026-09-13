> 이전 구현 기록입니다. 현재 선택 정책은 [최종 정책 보고서](FINAL_POLICY_REPORT.md)를 참고하세요.

# V5 추가 이미지 적용 결과

- 기존 V5 이미지·일반 역할별 풀 유지. 빈 상태 풀만 승인 A 5장으로 교체.
- 기본 후보: extra-chin-on-paw, extra-curled-smile, extra-sunny-sit, extra-tongue-step, extra-puppy-gaze.
- B 6장은 이 폴더의 reserve에 보관. JPG 3장은 photos에 보관. public 배포·자동 선택·선로딩 제외.
- PNG 11장 SHA-256이 asset-map-addon.json과 일치. 원본 변환 없음.
- 선택기: src/main/resources/static/js/choco-empty.js.
- DOM 연결: src/main/resources/static/js/choco.js. 목록·위치별 빈 목록·빈 기록에서 공통 사용. 현재 앱에는 검색 화면이 없음. 오류 화면은 제외.
- sessionStorage 키: frizer.choco.empty.v5.1.a5. 식품 데이터는 저장하지 않음.
- 후보 전체 사용 전 재선택하지 않음. 새 회차 직전 이미지·포즈 회피. 충돌 후보는 보류, 전부 충돌하면 해당 빈 상태 보조 이미지만 생략.
- 실제 확인한 V5 look-aside/happy-sit/proud-sit은 adult-sit, happy-lounge는 lying-rest로 분류. 빈 풀에 기존 V5 후보를 임의로 추가하지 않음.
- 같은 컨텍스트의 DOM 교체·재렌더는 유지. 컨텍스트 변경·빈 상태 재진입·bfcache 복귀는 새 선택. 새로고침은 저장된 bag을 이어 사용.
- 이미지 실패는 후보별 제한 대체, 모두 실패하면 안내와 버튼만 유지.
- JavaScript 비활성화 시 이미지 없이 기존 안내·버튼 제공. 선택된 이미지만 로드.

## 검증
- node src/test/js/choco-empty.test.cjs: 1,000회 shuffle bag 순회와 회차 경계, 저장소 복원/실패, 잘못된 키 정리, 충돌 보류, 리렌더/이중 실행, 컨텍스트/재진입, 이미지 실패 통과.
- gradlew test bootJar --offline 전체 통과.
- 실제 실온/냉동 빈 목록에서 currentSrc가 승인 extra PNG임을 확인.
- 320/390/448px 가로형 chin-on-paw와 세로형 puppy-gaze 확인: contain, 160×150 영역, 추가 잘림·버튼 겹침·가로 넘침 없음. 리사이즈 시 동일 src 유지.
- 상대 asset base 초기화 오류를 실제 브라우저에서 발견해 수정하고 회귀 테스트 추가. 최종 tongue-step 정상 로드 확인.
- 기본 등록 아이콘 회색 rgb(105,107,122), 등록 선택 시 보라색 rgb(112,87,200): 다른 탭과 동일.
- 최근 기록 최신 5개, 전후 내용 복구. 위치·수량 메타데이터와 기록 시간은 홈에서 계속 생략.
- 보관 위치 카드 호버는 옅은 먹색 안쪽 그림자와 부드러운 바깥 그림자.