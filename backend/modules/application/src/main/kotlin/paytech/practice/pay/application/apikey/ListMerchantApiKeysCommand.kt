package paytech.practice.pay.application.apikey

import paytech.practice.pay.domain.identity.MerchantUserId

/**
 * [ListMerchantApiKeysUseCase]의 입력이다.
 *
 * @property queriedByMerchantUserId 조회를 요청한 `OWNER`/`ADMIN`의 ID. 이 값
 * 하나로 조회 권한 확인과 조회 대상 가맹점을 모두 결정한다 —
 * [IssueMerchantApiKeyCommand]와 같은 이유로 `merchantId`를 별도로 받지 않는다.
 */
data class ListMerchantApiKeysCommand(
	val queriedByMerchantUserId: MerchantUserId,
)
