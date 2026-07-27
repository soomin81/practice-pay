package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * [AdminChangeMerchantUserStatusUseCase]의 입력이다.
 *
 * merchant-side [ChangeMerchantUserStatusCommand]와 달리 **대상 가맹점(`merchantId`)을 직접
 * 받는다** — 행위자가 특정 가맹점에 속한 `MerchantUser`가 아니라 어느 가맹점이든 관리하는
 * 내부 운영자(`InternalUser`)라, 테넌시를 요청자 소속이 아니라 경로가 지정한 가맹점으로
 * 잡는다. 요청자 식별자는 받지 않는다 — 인가는 `SecurityConfig` 정적 규칙이 하고(그 코드가
 * 실행됐다는 것 자체가 `SUPER_ADMIN`/`OPERATOR` 세션), 자기 자신 차단은 내부 운영자가
 * 가맹점 사용자가 될 수 없으므로 성립하지 않는다.
 */
data class AdminChangeMerchantUserStatusCommand(
	val merchantId: MerchantId,
	val targetMerchantUserId: MerchantUserId,
	val action: MerchantUserStatusAction,
)
