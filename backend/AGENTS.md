# Codex 지침 (backend)

**`backend/`에서 작업하기 전에 같은 자리의 [`CLAUDE.md`](CLAUDE.md)를 읽는다.** 빌드/테스트 명령어, 헥사고날 계층 규칙, jOOQ/MySQL 컨벤션, 설정과 비밀값 규칙이 전부 그 문서에 있다.

기능별 구현 판단 기록은 [`IMPLEMENTATION-NOTES.md`](IMPLEMENTATION-NOTES.md)에 있다 — 통째로 읽을 필요는 없고, 비슷한 상황의 선례를 찾을 때 본다.

저장소 공통 지침은 루트 [`../CLAUDE.md`](../CLAUDE.md)이고, 그 진입점은 루트 [`../AGENTS.md`](../AGENTS.md)다.

## 이 파일이 따로 있는 이유

에이전트는 **작업 중인 디렉토리의 지침 파일을 읽는다.** 루트 `AGENTS.md` → 루트 `CLAUDE.md` → `backend/CLAUDE.md`로 사슬이 이어져 있지만, 끝까지 따라가지 않으면 backend의 실제 컨벤션에 닿지 못한 채 작업이 시작된다. 이 파일은 그 사슬을 한 단계로 줄인다.

- **공통 규칙도 backend 규칙도 이 파일에 중복 작성하지 않는다**(루트 `AGENTS.md`와 같은 규칙이다). 여기는 포인터만 둔다.
- backend 규칙이 바뀌면 `backend/CLAUDE.md`를, 저장소 공통 규칙이 바뀌면 루트 `CLAUDE.md`를 고친다.
