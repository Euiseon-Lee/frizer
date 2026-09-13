# V5.2 누적 추가 패키지

이 ZIP 하나가 이전 V5.1 추가 ZIP을 대체한다. 기존 V5 Final 전체 패키지는 유지한다.

- 기존: 알파 최소 보정 11장(A 5 / B 6), 매핑, 순환 지시 유지
- 신규: 배경 있는 JPG 3장(사진 프레임 전용, 무가공 원본)
- 중복 PNG 1장: V5 relaxed-stretch와 바이트까지 동일하여 재등록 제외
- PHOTO_REVIEW.jpg / PHOTO_REVIEW.md: 신규 검토 및 사용 조건
- photo-map-addon.json: 신규 사진 전용 매핑

신규 JPG 3장은 투명 누끼가 아니다. 현재 빈 목록 누끼 풀의 기본 후보 수는 여전히 5장이다. 사진 프레임 방식은 별도 선택 후 적용한다.

Codex 전달: 이 누적 추가 ZIP의 CODEX_PROMPT.md를 읽고 V5.1 추가 패키지 대신 적용해줘. 기존 누끼 11장은 유지하고 신규 JPG 3장은 프레임 전용 후보로 관리해줘. JPG를 누끼형 빈 목록 풀에 섞지 말고, relaxed-stretch는 중복 등록하지 마.

---
아래는 기존 V5.1 가공 내역이다.

# 쪼코 V5.1 추가 패키지

기존 V5 Final에 더하는 11장 가공본과 빈 목록 이미지 교체 지시다. 기존 전체 사진은 중복 포함하지 않았다.

- assets/choco: 새 기본 후보 5장
- reserve/choco: 프레임/경계 검토가 필요한 후순위 6장
- originals: 업로드 원본 11장
- ASSET_MAPPING.md / asset-map-addon.json: 파일 매핑, 사용 조건, SHA-256, 알파 변경 수
- REVIEW.jpg: 실제 소형 크기에서 밝은/어두운 배경 비교
- CODEX_PROMPT.md: 빈 화면 다양화 및 중복 방지 지시
- tools/process.py: Python 재현 스크립트 (Pillow, NumPy, SciPy 필요)

가공: 투명도 128 이상 영역에서 거리 2px 이내 및 바깥 반투명 경계에 3×3 최소 필터 15%, sigma 0.45 Gaussian 20% 후보를 적용하여 알파를 소폭 감소시켰다. RGB·밝기·해상도는 동일하다. 알파가 0인 배경을 새로 채우지 않았다. 원본의 잘림과 소품은 그대로다. 누끼 재생성이나 완벽한 실루엣 복구를 의미하지 않는다.

11장 전부 알파만 가공했고 사진 파일의 RGB 동일성, 내부 알파 유지, 알파 증가 없음 검증을 수행했다. 앱 구현은 이 ZIP에 포함하지 않는다.

Codex에 전달: 기존 V5 Final을 유지하면서 이 추가 패키지의 CODEX_PROMPT.md를 적용해줘. 새 A 5장을 빈 목록 공유 풀에 추가하고 empty-curious를 기본 풀에서 제외해줘. shuffle bag으로 다양하게 순환하되 리렌더 중 사진은 유지해줘. B 6장은 자동 사용하지 마.
