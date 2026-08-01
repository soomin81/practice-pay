package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.application.port.outbound.InternalUserRepository
import paytech.practice.pay.application.port.outbound.InvitationTokenHasher
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.application.port.outbound.PasswordEncoder
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.identity.AccountInvitation
import paytech.practice.pay.domain.identity.AccountInvitationStatus
import paytech.practice.pay.domain.identity.InvitationAccountType
import paytech.practice.pay.domain.identity.LoginId
import java.time.Clock
import java.time.Instant

/**
 * "초대 수락(활성화)" Use Case다. `IssueInternalUserUseCase`가 만든
 * `InternalUser(INVITED)` + `AccountInvitation(PENDING)`(또는 향후 가맹점 등록
 * Use Case가 같은 모양으로 만들 `MerchantUser(INVITED)`)을 대상으로, 초대
 * 대상이 원문 Token과 새 비밀번호를 제출해 자기 계정을 `INVITED → ACTIVE`로
 * 전이시키는 흐름이다(`docs/domain/state-transitions.md`의 "활성화" 절: "유효한
 * 초대, 초대 만료 전, 비밀번호 설정 완료"가 조건이라고 명시한 그 지점).
 *
 * `AccountInvitation`이 `accountType`으로 `InternalUser`/`MerchantUser`를 이미
 * 구분하고 두 애그리게이트의 `activate(passwordHash, activatedAt)` 시그니처가
 * 완전히 같아서, Use Case 하나로 두 계정 유형을 함께 처리한다 — 거의 동일한
 * 로직을 두 Use Case로 중복시키지 않는다. `command.expectedAccountType`으로
 * 호출한 앱(`api-admin`은 `INTERNAL_USER`, `api-merchant`는 `MERCHANT_USER`)이
 * 기대하는 유형과 실제 `accountType`이 다르면 거부한다 — 다른 앱 경계의 초대
 * Token을 잘못 제출해도 그 경계를 넘지 않는다.
 *
 * **만료된 초대를 발견해도 `AccountInvitation.expire()`를 호출해 `EXPIRED`로
 * 갱신하지는 않는다** — `docs/database/database-design.md`의
 * `idx_account_invitation_pending(invitation_status, expires_at)` 인덱스가
 * 암시하는 별도의 만료 Sweep Worker(`apps:batch`의 `expireAccountInvitationsJob`)의 책임이다.
 * 이 Use Case는 만료 여부를 읽기 전용으로만 판단한다.
 *
 * `AccountInvitation + (InternalUser 또는 MerchantUser)`를 함께 저장하는 이
 * 트랜잭션 경계는 `docs/architecture/persistence-jooq.md`가 명시한 세 경계
 * 어디에도 없다 — `IssueInternalUserUseCase`가 발급 시점에 이미 같은 방식으로
 * 새 경계를 정의한 선례를 그대로 따른다.
 */
class AcceptAccountInvitationUseCase(
	private val accountInvitationRepository: AccountInvitationRepository,
	private val internalUserRepository: InternalUserRepository,
	private val merchantUserRepository: MerchantUserRepository,
	private val invitationTokenHasher: InvitationTokenHasher,
	private val passwordEncoder: PasswordEncoder,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	fun execute(command: AcceptAccountInvitationCommand): AcceptAccountInvitationResult {
		val tokenHash = invitationTokenHasher.hash(command.invitationToken)
		val invitation =
			accountInvitationRepository.findByTokenHash(tokenHash)
				?: throw InvalidInvitationException()

		val now = clock.instant()
		if (invitation.accountType != command.expectedAccountType ||
			invitation.status != AccountInvitationStatus.PENDING ||
			now.isAfter(invitation.expiresAt)
		) {
			throw InvalidInvitationException()
		}

		val passwordHash = passwordEncoder.encode(command.newPassword)

		return when (invitation.accountType) {
			InvitationAccountType.INTERNAL_USER -> acceptForInternalUser(invitation, passwordHash, now)
			InvitationAccountType.MERCHANT_USER -> acceptForMerchantUser(invitation, passwordHash, now)
		}
	}

	private fun acceptForInternalUser(
		invitation: AccountInvitation,
		passwordHash: String,
		now: Instant,
	): AcceptAccountInvitationResult {
		val internalUser =
			internalUserRepository.findById(requireNotNull(invitation.internalUserId))
				?: error("AccountInvitation(${invitation.id.value})의 InternalUser(${invitation.internalUserId?.value})를 찾을 수 없습니다.")
		internalUser.activate(passwordHash, now)
		invitation.accept(now)

		return transactionManager.runInTransaction {
			internalUserRepository.save(internalUser)
			accountInvitationRepository.save(invitation)
			resultOf(internalUser.loginId, now)
		}
	}

	private fun acceptForMerchantUser(
		invitation: AccountInvitation,
		passwordHash: String,
		now: Instant,
	): AcceptAccountInvitationResult {
		val merchantUser =
			merchantUserRepository.findById(requireNotNull(invitation.merchantUserId))
				?: error("AccountInvitation(${invitation.id.value})의 MerchantUser(${invitation.merchantUserId?.value})를 찾을 수 없습니다.")
		merchantUser.activate(passwordHash, now)
		invitation.accept(now)

		return transactionManager.runInTransaction {
			merchantUserRepository.save(merchantUser)
			accountInvitationRepository.save(invitation)
			resultOf(merchantUser.loginId, now)
		}
	}

	private fun resultOf(
		loginId: LoginId,
		activatedAt: Instant,
	): AcceptAccountInvitationResult = AcceptAccountInvitationResult(loginId = loginId, activatedAt = activatedAt)
}
