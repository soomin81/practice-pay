package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.identity.InternalLoginAuditId
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginOutcome
import java.time.Instant

/**
 * 로그인 감사 로그 조회를 위한 전용 jOOQ Projection Outbound Port다
 * ([MerchantUserListProjection]과 같은 이유로 Command Repository에 `findAll`류를 두지 않는다).
 */
fun interface InternalLoginAuditProjection {
	/** 최근 로그인 감사 기록을 최신순(`occurred_at DESC`)으로 최대 [limit]건 돌려준다. */
	fun findRecent(limit: Int): List<InternalLoginAuditEntry>
}

/**
 * [InternalLoginAuditProjection]이 돌려주는 조회 전용 읽기 모델이다.
 *
 * @property userName 시도가 가리킨 계정의 이름. **없는 `loginId`로의 시도면 `null`이다**
 * (`internal_user` LEFT JOIN이 매칭되지 않는다) — 화면이 "알 수 없는 계정" 시도를 구분해
 * 보여줄 수 있다.
 */
data class InternalLoginAuditEntry(
	val auditId: InternalLoginAuditId,
	val internalUserId: InternalUserId?,
	val attemptedLoginId: String,
	val userName: String?,
	val outcome: LoginOutcome,
	val clientIp: String?,
	val occurredAt: Instant,
)
