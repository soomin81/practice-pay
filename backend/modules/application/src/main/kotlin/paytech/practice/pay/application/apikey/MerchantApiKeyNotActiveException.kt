package paytech.practice.pay.application.apikey

/**
 * 이미 `REVOKED`/`EXPIRED`인 `MerchantApiKey`를 다시 폐기하려 할 때 던진다.
 *
 * `RevokeMerchantApiKeyUseCase`가 `MerchantApiKey.revoke()`를 호출하기 전에
 * `isUsable()`로 미리 확인한다 — 도메인의 `checkTransition`이 던지는
 * `IllegalStateException`을 그대로 HTTP까지 새게 두지 않기 위해서다(이 프로젝트의
 * 다른 Use Case들도 같은 이유로 상태를 먼저 확인하고 도메인 메서드를 부른다,
 * 예: `AcceptAccountInvitationUseCase`가 `AccountInvitation.accept()`를 부르기 전에
 * `status == PENDING`을 먼저 확인하는 것과 같다).
 */
class MerchantApiKeyNotActiveException(
	message: String,
) : RuntimeException(message)
