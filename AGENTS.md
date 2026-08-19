# Codex 프로젝트 지침

이 저장소의 공통 개발 지침은 루트의 `CLAUDE.md`에 정의되어 있다.

Codex는 작업을 시작하기 전에 반드시 다음 순서로 문서를 확인한다.

1. `CLAUDE.md`
2. **작업할 디렉토리의 지침** — `backend/`면 `backend/CLAUDE.md`, `frontend/`면 `frontend/CLAUDE.md`. 명령어와 구현 컨벤션은 루트가 아니라 이 문서들에 있다(각 디렉토리의 `AGENTS.md`가 같은 곳을 가리킨다).
3. 현재 작업과 관련된 `docs/` 하위 문서
4. DB 변경 작업이면 `backend/db-core/src/main/resources/db/migration/`의 Flyway migration 파일

## 필수 규칙

- `CLAUDE.md`를 이 프로젝트의 단일 기준 지침으로 사용한다.
- 공통 규칙을 이 파일에 중복 작성하지 않는다.
- 공통 개발 규칙 변경 시 `CLAUDE.md`를 수정한다.
- 도메인, 상태, DB, API 또는 아키텍처 변경 시 관련 문서도 함께 수정한다.
