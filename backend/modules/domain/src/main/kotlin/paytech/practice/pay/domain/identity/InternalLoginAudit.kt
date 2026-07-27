package paytech.practice.pay.domain.identity

import java.time.Instant

/**
 * 내부 운영자 로그인 시도 하나를 남기는 **불변 감사 기록**이다(`internal_login_audit` 테이블).
 *
 * 상태 전이가 없는 append-only 스냅샷이라 다른 애그리게이트와 달리 `private` 생성자 +
 * `create`/`reconstitute` 팩토리를 두지 않고 공개 생성자를 가진 평범한 `data class`로 둔다 —
 * `PaymentQuote`가 같은 이유로 그렇게 돼 있다(`backend/CLAUDE.md`의 "도메인 코드 컨벤션").
 *
 * @property internalUserId 시도가 가리킨 계정. **없는 `loginId`로의 시도면 `null`이다** —
 * 존재하지 않는 계정에 대한 시도도 감사에 남긴다(공격 탐지에 쓸모가 있다).
 * @property attemptedLoginId 시도에 쓰인 로그인 아이디. 계정이 없어도 이 값은 남는다.
 * @property clientIp 요청의 원격 주소. 프록시 뒤 실제 IP(`X-Forwarded-For`)는 다루지 않는다 —
 * `null`일 수 있다.
 */
data class InternalLoginAudit(
	val id: InternalLoginAuditId,
	val internalUserId: InternalUserId?,
	val attemptedLoginId: LoginId,
	val outcome: LoginOutcome,
	val clientIp: String?,
	val occurredAt: Instant,
)
