package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.InternalUserRepository
import paytech.practice.pay.application.port.outbound.InvitationTokenHasher
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.identity.AccountInvitation
import paytech.practice.pay.domain.identity.AccountInvitationId
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import java.time.Clock
import java.time.Duration

/**
 * "SUPER_ADMIN의 내부 계정 발급" Use Case다(`docs/architecture/identity-access-api-key.md`의
 * "3.3 발급 정책", "9. MVP와 후속 범위"). `InternalUser(INVITED)`와 그 계정을
 * 활성화할 `AccountInvitation(PENDING)`을 한 트랜잭션으로 함께 만든다 —
 * `docs/database/database-design.md`의 가맹점 등록 트랜잭션 예시
 * (`Merchant + MerchantUser(OWNER, INVITED) + AccountInvitation`)와 같은 모양이다.
 *
 * 초대를 수락해 비밀번호를 설정하고 `INVITED → ACTIVE`로 전이하는 흐름(활성화)은
 * 이 Use Case의 책임이 아니다 — 별도 Use Case로 다룬다.
 *
 * `loginId`/`email`은 DB Unique 제약([internalUserRepository]로 사전 확인)이 걸려
 * 있어 겹치면 [DuplicateInternalUserException]을 던진다. 다만 이 조회 후 판단은
 * DB Unique 제약만큼 원자적이지 않다 — [paytech.practice.pay.application.payment.CreatePaymentUseCase]의
 * 멱등성 체크와 같은 성격의 한계다.
 *
 * [INVITATION_VALIDITY]는 `docs/`에 값이 정해져 있지 않아 이 Use Case가 상수로
 * 고정했다 — `CreatePaymentUseCase`의 `PAYMENT_VALIDITY`와 같은 성격의 MVP 단순화다.
 */
class IssueInternalUserUseCase(
	private val internalUserRepository: InternalUserRepository,
	private val accountInvitationRepository: AccountInvitationRepository,
	private val invitationTokenHasher: InvitationTokenHasher,
	private val idGenerator: IdGenerator,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	fun execute(command: IssueInternalUserCommand): IssueInternalUserResult {
		internalUserRepository.findByLoginId(command.loginId)?.let {
			throw DuplicateInternalUserException("로그인 아이디(${command.loginId.value})가 이미 사용 중입니다.")
		}
		internalUserRepository.findByEmail(command.email)?.let {
			throw DuplicateInternalUserException("이메일(${command.email.value})이 이미 사용 중입니다.")
		}

		val now = clock.instant()

		val internalUser =
			InternalUser.invite(
				id = InternalUserId("iu_" + idGenerator.newId()),
				loginId = command.loginId,
				email = command.email,
				userName = command.userName,
				role = command.role,
				createdByInternalUserId = command.issuedByInternalUserId,
				createdAt = now,
			)

		val rawInvitationToken = idGenerator.newId()
		val invitation =
			AccountInvitation.forInternalUser(
				id = AccountInvitationId("ai_" + idGenerator.newId()),
				internalUserId = internalUser.id,
				tokenHash = invitationTokenHasher.hash(rawInvitationToken),
				expiresAt = now.plus(INVITATION_VALIDITY),
				createdAt = now,
			)

		return transactionManager.runInTransaction {
			internalUserRepository.save(internalUser)
			accountInvitationRepository.save(invitation)
			IssueInternalUserResult(
				internalUserId = internalUser.id,
				loginId = internalUser.loginId,
				email = internalUser.email,
				userName = internalUser.userName,
				role = internalUser.role,
				invitationToken = rawInvitationToken,
				invitationExpiresAt = invitation.expiresAt,
			)
		}
	}

	companion object {
		/** 초대 Token의 유효 기간. */
		private val INVITATION_VALIDITY: Duration = Duration.ofDays(7)
	}
}
