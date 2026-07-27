package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.merchant.MerchantCode

/**
 * [AuthenticateMerchantUserUseCase]의 입력이다.
 *
 * [merchantCode]가 필요한 이유는 [paytech.practice.pay.application.port.outbound.MerchantUserRepository]의
 * KDoc 참고 — `login_id`가 가맹점 안에서만 유일해서 로그인 폼이 어느 가맹점인지
 * 사람이 읽을 수 있는 코드로 먼저 밝혀야 한다.
 *
 * @property password 원문 비밀번호. [AuthenticateInternalUserCommand]와 같은 이유로 저장되거나 로그에 남지 않는다.
 * @property clientIp 요청의 원격 주소. 감사 로그에만 남기고 인증 판단에는 쓰지 않는다 —
 * inbound Adapter가 `HttpServletRequest.remoteAddr`로 채우며 없으면 `null`이다(기본값이 있어
 * IP를 넘기지 않는 호출부·테스트도 그대로 동작한다).
 */
data class AuthenticateMerchantUserCommand(
	val merchantCode: MerchantCode,
	val loginId: LoginId,
	val password: String,
	val clientIp: String? = null,
)
