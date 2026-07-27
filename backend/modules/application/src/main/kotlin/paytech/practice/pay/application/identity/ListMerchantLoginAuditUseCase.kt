package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.MerchantLoginAuditProjection

/**
 * "가맹점 로그인 감사 로그 조회" Use Case다 — 내부 운영자 콘솔(admin)이 전 가맹점의 최근
 * 로그인/실패/잠김을 최신순으로 본다(`GET /admin/merchant-login-audit`).
 *
 * [ListInternalLoginAuditUseCase]와 같은 모양이다 — **요청자를 받지 않는다**(인가는 admin
 * `SecurityConfig`가 진다). [DEFAULT_LIMIT]도 `docs/`에 값이 없어 고정한 MVP 상수다.
 */
class ListMerchantLoginAuditUseCase(
	private val merchantLoginAuditProjection: MerchantLoginAuditProjection,
) {
	fun execute(limit: Int = DEFAULT_LIMIT): ListMerchantLoginAuditResult =
		ListMerchantLoginAuditResult(entries = merchantLoginAuditProjection.findRecent(limit))

	companion object {
		const val DEFAULT_LIMIT = 200
	}
}
