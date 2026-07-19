package paytech.practice.pay.application.apikey

import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import paytech.practice.pay.domain.identity.MerchantUserId

/**
 * [RevokeMerchantApiKeyUseCase]의 입력이다.
 *
 * @property revokedByMerchantUserId 폐기를 요청한 `OWNER`/`ADMIN`의 ID. 폐기 대상
 * ([merchantApiKeyId])이 이 사용자와 같은 가맹점 소속인지도 이 값으로 확인한다
 * ([IssueMerchantApiKeyCommand]와 같은 멀티테넌시 방어 이유).
 */
data class RevokeMerchantApiKeyCommand(
	val merchantApiKeyId: MerchantApiKeyId,
	val revokedByMerchantUserId: MerchantUserId,
)
