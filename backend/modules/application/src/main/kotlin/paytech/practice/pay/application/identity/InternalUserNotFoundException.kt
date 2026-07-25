package paytech.practice.pay.application.identity

/**
 * 관리 대상 `InternalUser`를 찾을 수 없을 때 던진다 — inbound Adapter에서 `404`로
 * 매핑한다([MerchantUserNotFoundException]과 같은 성격).
 */
class InternalUserNotFoundException(
	message: String,
) : RuntimeException(message)
