package paytech.practice.pay.application.identity

/**
 * 새 `InternalUser`를 발급하려는 `loginId` 또는 `email`이 이미 다른 계정에서
 * 쓰이고 있을 때 던진다(`uk_internal_user_login_id`/`uk_internal_user_email`).
 *
 * 어느 필드가 겹쳤는지는 [message]에 담는다 — 로그인 실패([InvalidCredentialsException])와
 * 달리, 여기서는 발급을 시도하는 SUPER_ADMIN에게 원인을 숨길 이유가 없다.
 */
class DuplicateInternalUserException(
	message: String,
) : RuntimeException(message)
