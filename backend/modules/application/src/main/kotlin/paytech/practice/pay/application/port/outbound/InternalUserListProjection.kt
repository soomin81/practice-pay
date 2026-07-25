package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import java.time.Instant

/**
 * 내부 운영자 목록 조회를 위한 전용 jOOQ Projection Outbound Port다.
 *
 * [MerchantUserListProjection]과 같은 이유로 [InternalUserRepository](Command Repository)에
 * `findAll`류를 추가하지 않는다(`docs/architecture/persistence-jooq.md`의 "복잡한 조회는
 * 전용 jOOQ Projection을 사용한다").
 *
 * **[InternalUserSummary]는 `passwordHash`를 포함하지 않는다** — [MerchantUserSummary]가
 * 같은 값을 제외한 것과 같은 정신이다(응답에 실을 값이면 로그에도 남을 수 있으니 애초에
 * 값 자체를 옮기지 않는다). 잠금 관련 필드도 지금 화면이 쓰지 않아 담지 않는다.
 *
 * **가맹점 Projection들과 달리 가맹점 범위가 없다** — 내부 운영자는 특정 가맹점에 속하지
 * 않으므로 전체를 돌려준다(`docs/architecture/identity-access-api-key.md`의 "2. 도메인 경계").
 */
fun interface InternalUserListProjection {
	/** 모든 내부 운영자를 최신 생성순(`created_at DESC`)으로 돌려준다. */
	fun findAll(): List<InternalUserSummary>
}

/** [InternalUserListProjection]이 돌려주는 목록 조회 전용 읽기 모델이다. */
data class InternalUserSummary(
	val internalUserId: InternalUserId,
	val loginId: LoginId,
	val email: Email,
	val userName: String,
	val role: InternalUserRole,
	val status: AccountStatus,
	val lastLoginAt: Instant?,
	val createdAt: Instant,
)
