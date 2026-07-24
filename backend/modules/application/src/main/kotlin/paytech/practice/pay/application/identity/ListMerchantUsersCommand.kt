package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.MerchantUserId

/**
 * [ListMerchantUsersUseCase]의 입력이다.
 *
 * **조회 대상 가맹점(`merchantId`)을 받지 않는다** — 항상 요청자 자신의 소속 가맹점을
 * 조회한다([ListMerchantApiKeysCommand][paytech.practice.pay.application.apikey.ListMerchantApiKeysCommand]와
 * 같은 이유). 호출자가 `merchantId`를 실어 보낼 수 있으면 남의 가맹점 명부를 읽을 수
 * 있는 멀티테넌시 취약점이 된다.
 */
data class ListMerchantUsersCommand(
	val queriedByMerchantUserId: MerchantUserId,
)
