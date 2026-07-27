package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.identity.LoginOutcome
import paytech.practice.pay.domain.identity.MerchantLoginAuditId
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Instant

/**
 * 가맹점 로그인 감사 로그 조회를 위한 전용 jOOQ Projection Outbound Port다
 * ([InternalLoginAuditProjection]의 가맹점판). 내부 운영자 콘솔(admin)이 **전 가맹점**의
 * 로그인을 감독하려고 조회하므로 범위를 좁히는 인자가 없다.
 */
fun interface MerchantLoginAuditProjection {
	/** 최근 로그인 감사 기록을 최신순(`occurred_at DESC`)으로 최대 [limit]건 돌려준다. */
	fun findRecent(limit: Int): List<MerchantLoginAuditEntry>
}

/**
 * [MerchantLoginAuditProjection]이 돌려주는 조회 전용 읽기 모델이다.
 *
 * @property merchantName 시도가 가리킨 가맹점의 이름. **없는 `merchantCode`로의 시도면 `null`**
 * (`merchant` LEFT JOIN이 매칭되지 않는다).
 * @property userName 시도가 가리킨 계정의 이름. 없는 계정이면 `null`(`merchant_user` LEFT JOIN).
 */
data class MerchantLoginAuditEntry(
	val auditId: MerchantLoginAuditId,
	val merchantId: MerchantId?,
	val merchantName: String?,
	val attemptedMerchantCode: String,
	val attemptedLoginId: String,
	val userName: String?,
	val outcome: LoginOutcome,
	val clientIp: String?,
	val occurredAt: Instant,
)
