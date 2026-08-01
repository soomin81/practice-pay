package paytech.practice.pay.application.merchant

import paytech.practice.pay.application.port.outbound.MerchantListProjection

/**
 * "가맹점 목록 조회" Use Case다(`GET /admin/merchants`).
 *
 * **입력이 없어 `Command`를 두지 않는다** — 필터·페이지네이션이 없는 MVP 단순화 때문에
 * 받을 값이 없다(알려진 gap, [MerchantListProjection]의 KDoc 참고). 필터가 생기면 그때
 * `Command`를 추가한다.
 *
 * **요청자를 받지 않는다** — 인가는 `SecurityConfig`가 정적으로 판단한다. 이 경로에는 역할
 * 제약을 두지 않는데, `VIEWER`가 "조회 전용"(`InternalUserRole`의 KDoc)이라 세 역할 모두
 * 볼 수 있어야 하고 그건 곧 기본 `authenticated()` 규칙과 같아서다.
 */
class ListMerchantsUseCase(
	private val merchantListProjection: MerchantListProjection,
) {
	fun execute(): ListMerchantsResult = ListMerchantsResult(merchants = merchantListProjection.findAll())
}
