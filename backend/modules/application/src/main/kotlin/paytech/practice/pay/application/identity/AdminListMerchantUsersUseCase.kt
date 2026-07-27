package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.MerchantUserListProjection
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * 내부 운영자 콘솔이 **임의 가맹점의** 사용자 명부를 조회하는 Use Case다
 * (`GET /admin/merchants/{merchantId}/users`).
 *
 * merchant-side [ListMerchantUsersUseCase]가 요청자를 다시 읽어 권한·소속 가맹점을 얻는
 * 것과 달리 **요청자 검사가 없고 대상 가맹점을 인자로 직접 받는다** — 조회는 인증된 내부
 * 사용자 전원(`VIEWER` 포함)에게 열려 있어(`SecurityConfig`가 `GET`을 좁히지 않는다,
 * `GET /admin/merchants`와 같은 스코핑) 요청자 역할로 좁힐 것이 없다. 결과 타입은
 * merchant-side [ListMerchantUsersResult]를 그대로 재사용한다(같은 읽기 모델).
 *
 * **가맹점 존재 여부를 확인하지 않는다** — 없는 `merchantId`면 빈 목록이 나올 뿐이고,
 * 프론트는 항상 알려진 가맹점 목록에서 진입한다. 상태·역할 변경 경로는 대상 사용자
 * 조회에서 이미 테넌시 404를 내므로 없는 가맹점에 대한 변경도 자연히 막힌다.
 */
class AdminListMerchantUsersUseCase(
	private val merchantUserListProjection: MerchantUserListProjection,
) {
	fun execute(merchantId: MerchantId): ListMerchantUsersResult =
		ListMerchantUsersResult(merchantUsers = merchantUserListProjection.findByMerchantId(merchantId))
}
