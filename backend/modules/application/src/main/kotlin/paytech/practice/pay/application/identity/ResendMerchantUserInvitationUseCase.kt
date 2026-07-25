package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.InvitationTokenHasher
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.identity.AccountInvitation
import paytech.practice.pay.domain.identity.AccountInvitationId
import paytech.practice.pay.domain.identity.AccountStatus
import java.time.Clock
import java.time.Duration

/**
 * "초대 재발송" Use Case다. 초대 Token은 Hash만 저장돼 원문을 다시 볼 수 없으므로
 * (`docs/architecture/identity-access-api-key.md`의 "6.4"와 같은 정신), **재발송은
 * 기존 링크를 다시 보여주는 게 아니라 새 Token을 발급하는 것**이다.
 *
 * 그래서 기존 `PENDING` 초대를 [AccountInvitation.revoke]로 무효화하고 새 초대를
 * 만든다 — **이전 링크는 이 시점부터 동작하지 않는다**(수락 시 `PENDING`이 아니라서
 * 거부된다). 두 쓰기가 함께 반영되어야 하므로 [TransactionManager]로 묶는다
 * ([InviteMerchantSubAccountUseCase]가 계정+초대를 함께 저장하는 것과 같은 이유).
 *
 * 접근 판단(요청자 권한, 테넌시, 자기 자신 차단, ADMIN→OWNER 차단)은
 * [MerchantUserManagementGuard]로 계정 관리 Use Case들과 공유한다. **"최소 하나의
 * 활성 OWNER" 불변식은 부르지 않는다** — 초대 조작은 활성 OWNER 수를 바꾸지 않는다.
 *
 * Token 생성 방식과 유효 기간은 [InviteMerchantSubAccountUseCase]의 것을 그대로
 * 따른다 — 재발송이라고 다른 값을 쓸 이유가 없다.
 */
class ResendMerchantUserInvitationUseCase(
	private val merchantUserRepository: MerchantUserRepository,
	private val accountInvitationRepository: AccountInvitationRepository,
	private val invitationTokenHasher: InvitationTokenHasher,
	private val idGenerator: IdGenerator,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	fun execute(command: ResendMerchantUserInvitationCommand): ResendMerchantUserInvitationResult {
		val requester =
			MerchantUserManagementGuard.loadAuthorizedRequester(
				merchantUserRepository,
				command.requestedByMerchantUserId,
			)
		val target =
			MerchantUserManagementGuard.loadManageableTarget(
				merchantUserRepository,
				requester,
				command.targetMerchantUserId,
			)

		if (target.status != AccountStatus.INVITED) {
			throw InvitationNotManageableException(
				"MerchantUser(${target.id.value})는 초대를 재발송할 수 있는 상태가 아닙니다(status=${target.status}).",
			)
		}

		val now = clock.instant()
		val existing = accountInvitationRepository.findPendingByMerchantUserId(target.id)
		val rawInvitationToken = idGenerator.newId()
		val invitation =
			AccountInvitation.forMerchantUser(
				id = AccountInvitationId("ai_" + idGenerator.newId()),
				merchantUserId = target.id,
				tokenHash = invitationTokenHasher.hash(rawInvitationToken),
				expiresAt = now.plus(INVITATION_VALIDITY),
				createdAt = now,
			)

		return transactionManager.runInTransaction {
			// 기존 초대를 먼저 무효화한다 — 이전 링크가 살아 있으면 안 된다.
			existing?.let {
				it.revoke()
				accountInvitationRepository.save(it)
			}
			accountInvitationRepository.save(invitation)
			ResendMerchantUserInvitationResult(
				merchantUserId = target.id,
				invitationToken = rawInvitationToken,
				invitationExpiresAt = invitation.expiresAt,
			)
		}
	}

	companion object {
		/** [InviteMerchantSubAccountUseCase]와 같은 값이다. */
		private val INVITATION_VALIDITY: Duration = Duration.ofDays(7)
	}
}
