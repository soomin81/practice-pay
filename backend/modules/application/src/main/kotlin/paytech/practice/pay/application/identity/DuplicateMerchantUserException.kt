package paytech.practice.pay.application.identity

/**
 * 새 `MerchantUser`(하위 계정)를 발급하려는 `loginId` 또는 `email`이 같은
 * 가맹점 안에서 이미 다른 계정에 쓰이고 있을 때 던진다
 * (`uk_merchant_user_login`/`uk_merchant_user_email`, 둘 다 `merchant_seq` 범위다).
 *
 * [DuplicateInternalUserException]/[DuplicateMerchantException]과 같은 철학이다 —
 * 발급을 시도하는 `OWNER`/`ADMIN`에게 원인을 숨길 이유가 없다.
 */
class DuplicateMerchantUserException(
	message: String,
) : RuntimeException(message)
