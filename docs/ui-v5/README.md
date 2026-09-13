> V5 최우선: PROCESSING_REPORT.md 참조. A 16장 경계 투명도만 수정; RGB/밝기/색온도 변경 없음. B 21장 미가공. 이전 문서의 미가공 설명은 이 고지로 대체. 웹용 크기 변경·WebP 생성은 이번 최소 수정 범위에서 제외.

# 쪼코 V5 전달 패키지
41장 검수: A 16장 / B 21장 / X 4장. 사진 37장 포함.

- assets/choco: 우선 사용 사진
- reserve/choco: 잘림·구도 조건이 있는 후순위 사진 (자동 사용 금지)
- ASSET_MAPPING.md: 41장 전부의 원본명/용도/검수/제외 이유
- asset-map.json: 우선순위와 파일 매핑
- image-pools.json: 검수된 기본 화면별 교체 풀
- previews: A/B 검토용 미리보기. 제품 자산으로 사용하지 않음.
- CODEX_PROMPT.md: 잔디 제외와 V5 기준 구현 지시

기존 ZIP 위에 덮어 풀지 말고 이 V5 폴더를 기준으로 사용. 이전 9장/25장 제한을 적용하지 않는다. A 사진은 알파 경계만 최소 수정했다. 원본은 originals/choco, 보조 B는 미가공이다. 소품인 잎 모자는 잔디 배경과 구분하여 유지했다.

## V5 Final 화면 업데이트
- UI_FINAL_SPEC.md: 최신 합의와 영역별 색상·기록 배지 명세. 이전 색상 지시보다 우선.
- design-tokens.css: 의미별 독립 토큰과 추후 배지 글자색 통일용 참고 CSS.
- 연보라 primary와 보관 위치 4색 유지. 다른 넓은 유색 면 축소, 핑크 등록 배너 제거.
- 기존 사진 가공 결과와 원본은 그대로 유지. 이번 업데이트는 UI 지시와 배너 후보 풀 변경.

Codex 전달 문장:
이 V5 Final 패키지의 CODEX_PROMPT.md, UI_FINAL_SPEC.md, ASSET_MAPPING.md를 읽고 기존 앱의 UI를 개편해줘. design-tokens.css와 image-pools.json을 참고하고, 기존 도메인 로직을 보존해줘. 잔디는 제외하고 쪼코를 다양하게 배치해줘. B 사진은 명시된 조건을 따르고 큰 사용 전에 확인해줘. 이전 패키지의 색상 지시와 사진 매핑은 이 패키지로 대체해줘.
