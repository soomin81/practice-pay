package paytech.practice.pay.application.apikey

/**
 * 폐기하려는 `merchantApiKeyId`가 존재하지 않을 때, 또는 존재하지만 요청자와
 * 다른 가맹점 소속일 때 던진다.
 *
 * 두 경우를 구분하지 않고 같은 예외로 가린다 — 어느 쪽인지 드러내면 호출자가
 * 다른 가맹점의 `merchantApiKeyId`를 무차별 대입으로 탐색해 존재 여부를 알아낼
 * 수 있다(`AcceptAccountInvitationUseCase`의 `InvalidInvitationException`과 같은
 * 이유).
 */
class MerchantApiKeyNotFoundException(
	message: String,
) : RuntimeException(message)
