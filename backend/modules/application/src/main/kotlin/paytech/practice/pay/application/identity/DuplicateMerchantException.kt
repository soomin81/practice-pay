package paytech.practice.pay.application.identity

/**
 * 새 `Merchant`를 등록하려는 `merchantCode`가 이미 다른 가맹점에서 쓰이고 있을 때
 * 던진다(`uk_merchant_merchant_code`).
 *
 * [DuplicateInternalUserException]과 같은 철학이다 — 등록을 시도하는 내부
 * 운영자에게 원인을 숨길 이유가 없다.
 */
class DuplicateMerchantException(
	message: String,
) : RuntimeException(message)
