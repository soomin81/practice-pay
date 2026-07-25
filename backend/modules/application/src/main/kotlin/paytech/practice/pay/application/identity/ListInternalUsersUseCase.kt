package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.InternalUserListProjection

/**
 * "내부 운영자 목록 조회" Use Case다 — 콘솔의 내부 직원 화면이 누가 소속돼 있고 누가
 * 아직 `INVITED`로 남아 있는지 보여주기 위해 쓴다.
 *
 * **요청자를 받지 않는다 — 가맹점 쪽의 [ListMerchantUsersUseCase]와 의도적으로 다르다.**
 * 이유 둘:
 * 1. **같은 앱의 지역 관행이 그렇다.** `ListMerchantsUseCase`도 무인자 `execute()`이고,
 *    [IssueInternalUserCommand]의 KDoc은 "발급 권한 확인은 inbound Adapter(세션의 역할)가
 *    끝냈다고 전제한다"고 명시한다 — `apps:api-admin`은 인가를 `SecurityConfig`의 정적
 *    규칙에 맡기는 쪽으로 일관돼 있다.
 * 2. **멀티테넌시 문제가 없다.** 가맹점 쪽에서 요청자를 다시 읽은 핵심 이유는 "조회 대상
 *    가맹점을 신뢰할 수 있는 곳에서 얻기 위해서"였는데(요청 본문의 `merchantId`를 믿으면
 *    남의 가맹점을 읽는다), 내부 운영자는 특정 가맹점에 속하지 않아 좁힐 범위 자체가 없다.
 *
 * 그래서 **누가 이 목록을 볼 수 있는지는 전적으로 `SecurityConfig`가 정한다** —
 * `/admin/internal-users`가 `hasRole("SUPER_ADMIN")`이고 메서드로 좁혀져 있지 않아 `GET`도
 * 함께 덮는다(`InternalUserController`의 KDoc 참고).
 */
class ListInternalUsersUseCase(
	private val internalUserListProjection: InternalUserListProjection,
) {
	fun execute(): ListInternalUsersResult = ListInternalUsersResult(internalUsers = internalUserListProjection.findAll())
}
