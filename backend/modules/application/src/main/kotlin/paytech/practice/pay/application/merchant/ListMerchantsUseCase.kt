package paytech.practice.pay.application.merchant

import paytech.practice.pay.application.port.outbound.MerchantListProjection

/**
 * "가맹점 목록 조회" Use Case다 — `docs/`에 이 흐름 자체가 정해져 있진 않아
 * `POST /admin/merchants`(가맹점 등록)와 같은 리소스 계층에 `GET /admin/merchants`로
 * REST 관례로 새로 정했다(`InternalUserIssuanceController`의 KDoc이 `POST
 * /admin/internal-users`에 대해 남긴 것과 같은 이유).
 *
 * 이 프로젝트에서 처음으로 아그리게이트별 서브패키지를 만든 순수 조회 Use Case다
 * (`application.merchant`) — `RegisterMerchantUseCase`가 `application.identity`에
 * 있는 건 그 Use Case의 복잡도가 대부분 초대/식별 쪽이기 때문이지 "Merchant
 * 관련은 다 `merchant` 패키지"라는 규칙이 아니다(`ConnectCheckoutWalletUseCase`의
 * KDoc과 같은 논리) — 이 Use Case는 순수하게 `Merchant`만 다뤄서 처음으로
 * `application.merchant`를 새로 만들었다.
 *
 * **입력이 없어 `Command`를 두지 않는다.** 필터·페이지네이션이 없는 MVP 단순화
 * 때문에(알려진 gap, [MerchantListProjection]의 KDoc 참고) 이 Use Case가 실제로
 * 받을 수 있는 값이 없다 — 나중에 필터가 생기면 그때 `Command`를 추가한다.
 *
 * **호출 권한 확인은 `IssueInternalUserUseCase`와 같은 원칙(정적 역할 검사를
 * inbound Adapter에 맡긴다)을 따르지만, 결과가 다르다 — `InternalUser`에는
 * `MerchantUser.canInviteSubAccounts()`/`canManageApiKeys()`에 대응하는 동적
 * 권한 확인 메서드가 없다(도메인에 그런 메서드 자체가 없다). 그래서 여기서는
 * 정적 검사 하나로 충분하다고 판단했다 — 실제로 `SecurityConfig`는 이 경로에
 * 별도 역할 제약을 두지 않는다: `VIEWER`가 "조회 전용"(`InternalUserRole`의
 * KDoc)이라 세 역할 모두 볼 수 있어야 하고, 그건 곧 인증된 모든 내부 사용자를
 * 뜻해서 기본 `authenticated()` 규칙과 같다.
 */
class ListMerchantsUseCase(
	private val merchantListProjection: MerchantListProjection,
) {
	fun execute(): ListMerchantsResult = ListMerchantsResult(merchants = merchantListProjection.findAll())
}
