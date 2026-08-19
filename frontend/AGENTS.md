# Codex 지침 (frontend)

**`frontend/`에서 작업하기 전에 같은 자리의 [`CLAUDE.md`](CLAUDE.md)를 읽는다.** 실행 명령어, 앱 분리 기준, 체크아웃 화면 컨벤션이 그 문서에 있다.

**backend와 규칙이 다르다는 점을 먼저 확인한다** — 여기는 Docker를 쓰지 않고 호스트 Node로 돌리며, 워크스페이스가 없어 npm 명령을 각 앱 디렉토리에서 실행한다.

고객 대면 체크아웃을 건드린다면 기준 문서는 코드가 아니라 [`../docs/architecture/checkout-api.md`](../docs/architecture/checkout-api.md)다 — 계약을 바꿔야 하면 그 문서를 먼저 고친다.

저장소 공통 지침은 루트 [`../CLAUDE.md`](../CLAUDE.md)이고, 그 진입점은 루트 [`../AGENTS.md`](../AGENTS.md)다.

## 이 파일이 따로 있는 이유

에이전트는 **작업 중인 디렉토리의 지침 파일을 읽는다.** 루트에서 두 단계를 따라 내려와야만 닿는 규칙은 지켜지지 않을 수 있어서, 그 사슬을 한 단계로 줄인다.

- **공통 규칙도 frontend 규칙도 이 파일에 중복 작성하지 않는다** — 여기는 포인터만 둔다.
- frontend 규칙이 바뀌면 `frontend/CLAUDE.md`를 고친다.
