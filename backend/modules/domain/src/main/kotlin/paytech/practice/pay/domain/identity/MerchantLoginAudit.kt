package paytech.practice.pay.domain.identity

import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Instant

/**
 * 가맹점 관리자 로그인 시도 하나를 남기는 **불변 감사 기록**이다(`merchant_login_audit` 테이블).
 * [InternalLoginAudit]의 가맹점판이며, 상태 전이 없는 append-only 스냅샷이라 같은 이유로
 * 공개 생성자를 가진 평범한 `data class`다.
 *
 * @property merchantId 시도가 가리킨 가맹점. **없는 `merchantCode`로의 시도면 `null`이다** —
 * 가맹점 코드 존재 여부도 응답에 노출하지 않는 로그인 규칙과 짝이다.
 * @property merchantUserId 시도가 가리킨 계정. 가맹점은 찾았지만 `loginId`가 없으면 `null`이다.
 * @property attemptedMerchantCode 시도에 쓰인 가맹점 코드(원문 문자열). 가맹점을 못 찾아도
 * 이 값은 남는다 — 다른 애그리게이트(`Merchant`)의 VO `MerchantCode`로 참조하지 않고 원문을
 * 그대로 담는다(도메인 순수성: 애그리게이트는 다른 애그리게이트를 `*Id`로만 참조한다).
 * @property attemptedLoginId 시도에 쓰인 로그인 아이디. 계정이 없어도 이 값은 남는다.
 * @property clientIp 요청의 원격 주소. `X-Forwarded-For`는 다루지 않는다 — `null`일 수 있다.
 */
data class MerchantLoginAudit(
	val id: MerchantLoginAuditId,
	val merchantId: MerchantId?,
	val merchantUserId: MerchantUserId?,
	val attemptedMerchantCode: String,
	val attemptedLoginId: LoginId,
	val outcome: LoginOutcome,
	val clientIp: String?,
	val occurredAt: Instant,
)
