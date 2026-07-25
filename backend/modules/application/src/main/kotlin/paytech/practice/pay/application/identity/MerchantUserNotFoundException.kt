package paytech.practice.pay.application.identity

/**
 * 관리 대상 `MerchantUser`를 찾을 수 없을 때 던진다 — inbound Adapter에서 `404`로
 * 매핑한다.
 *
 * **대상이 다른 가맹점 소속인 경우도 이 예외로 처리한다**(403이 아니다) — 남의 가맹점
 * 사용자의 존재 여부를 응답 코드로 알려주지 않기 위해서다. 존재하지 않는 ID와 구분되지
 * 않아야 한다.
 */
class MerchantUserNotFoundException(
	message: String,
) : RuntimeException(message)
