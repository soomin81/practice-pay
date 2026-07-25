package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Instant

/**
 * 가맹점 사용자 목록 조회를 위한 전용 jOOQ Projection Outbound Port다.
 *
 * [MerchantListProjection]/[MerchantApiKeyListProjection]과 같은 이유로
 * [MerchantUserRepository](Command Repository)에 `findAll`류 메서드를 추가하지
 * 않는다(`docs/architecture/persistence-jooq.md`의 "복잡한 조회는 전용 jOOQ
 * Projection을 사용한다").
 *
 * **[MerchantUserSummary]는 `passwordHash`를 포함하지 않는다** —
 * [MerchantApiKeySummary]가 `secretHash`를 제외한 것과 같은 정신이다. 목록 화면이
 * 비밀번호 해시를 보여줄 이유가 없고, 응답에 실을 값이면 로그에도 남을 수 있으니
 * 애초에 값 자체를 옮기지 않는다. 잠금 관련 필드(`failedLoginCount`/`lockedUntil`)도
 * 지금 화면이 쓰지 않아 담지 않는다 — 필요해지면 그때 넓힌다.
 */
fun interface MerchantUserListProjection {
	/** 주어진 가맹점의 모든 가맹점 사용자를 최신 생성순(`created_at DESC`)으로 돌려준다. */
	fun findByMerchantId(merchantId: MerchantId): List<MerchantUserSummary>
}

/** [MerchantUserListProjection]이 돌려주는 목록 조회 전용 읽기 모델이다. */
data class MerchantUserSummary(
	val merchantUserId: MerchantUserId,
	val loginId: LoginId,
	val email: Email,
	val userName: String,
	val role: MerchantUserRole,
	val status: AccountStatus,
	val lastLoginAt: Instant?,
	val createdAt: Instant,
	/**
	 * 이 사용자의 **유효한(`PENDING`) 초대의 만료 시각**. 없으면 `null`이다.
	 *
	 * `INVITED` 사용자가 왜 아직 활성화되지 않았는지를 명부에서 바로 알기 위한 값이다 —
	 * `null`이면 "초대가 없거나 취소됨(재발송 필요)", 값이 과거면 "만료됨"이다. 만료는
	 * 수락 시점에만 검사되고 상태는 `PENDING`으로 남으므로(만료 배치가 없다) 화면이
	 * 이 시각을 현재와 비교해 판단한다.
	 */
	val pendingInvitationExpiresAt: Instant?,
)
