package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.InternalLoginAuditProjection

/**
 * "내부 운영자 로그인 감사 로그 조회" Use Case다 — 최근 로그인/실패/잠금 시도를 최신순으로
 * 돌려준다(`GET /admin/login-audit`).
 *
 * **요청자를 받지 않는다** — 인가는 전적으로 `SecurityConfig`가 진다(`ListInternalUsersUseCase`와
 * 같은 관행). 이 경로는 `SUPER_ADMIN` 전용이다(실패 시도·IP·누가 로그인했는지가 담긴다).
 *
 * [DEFAULT_LIMIT]은 `docs/`에 값이 없어 고정한 MVP 상수다 — 페이지네이션 없이 최근 N건만
 * 보여준다. 필요해지면 필터·페이지네이션으로 넓힌다.
 */
class ListInternalLoginAuditUseCase(
	private val internalLoginAuditProjection: InternalLoginAuditProjection,
) {
	fun execute(limit: Int = DEFAULT_LIMIT): ListInternalLoginAuditResult =
		ListInternalLoginAuditResult(entries = internalLoginAuditProjection.findRecent(limit))

	companion object {
		const val DEFAULT_LIMIT = 200
	}
}
