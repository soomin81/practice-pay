package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.InvitationTokenHasher
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.identity.AccountInvitation
import paytech.practice.pay.domain.identity.AccountInvitationId
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.identity.MerchantUserId
import java.time.Clock
import java.time.Duration

/**
 * "하위 계정 발급" Use Case다(`docs/architecture/identity-access-api-key.md`의
 * "4.4 하위 계정 발급": "`OWNER`, `ADMIN`은 하위 계정을 발급할 수 있다"). 같은
 * 가맹점의 `OWNER` 또는 `ADMIN`이 `ADMIN`/`VIEWER` 하위 계정을 초대한다 —
 * [IssueInternalUserUseCase]/[RegisterMerchantUseCase]와 같은 "발급 + 초대"
 * 모양이지만, 이번엔 새 Aggregate가 아니라 **기존 가맹점에 딸린** `MerchantUser`를
 * 하나 더 만든다는 점이 다르다.
 *
 * **발급 권한을 정적 역할 검사가 아니라 [MerchantUser.canInviteSubAccounts]로
 * 동적으로 확인한다 — [IssueInternalUserUseCase]와 의도적으로 다른 선택이다.**
 * `IssueInternalUserCommand`의 KDoc은 "발급 권한 확인은 inbound Adapter(세션의
 * 역할)가 끝냈다고 전제한다"고 명시하는데, 여기서는 그 원칙 대신 [invitedByMerchantUserId]로
 * 요청자의 `MerchantUser`를 다시 읽어 `canInviteSubAccounts()`를 호출한다. 이렇게
 * 다르게 간 이유 둘:
 * 1. `canInviteSubAccounts()`가 이미 도메인에 존재하는데 어디서도 호출되지 않고
 *    있었다 — 정적 역할 검사(`SecurityConfig`의 `hasAnyRole("OWNER", "ADMIN")`)만으로
 *    충분했다면 이 메서드가 있을 이유가 없다. `ACTIVE` 상태까지 함께 검증하는 것
 *    자체가 세션의 역할 스냅샷만으로는 부족하다는 뜻으로 읽었다(세션이 살아있는
 *    동안 계정이 `SUSPENDED`돼도 정적 검사는 이를 알 수 없다).
 * 2. 이 Use Case는 **어느 가맹점에 계정을 만들지도 같은 조회로 함께 얻는다**
 *    (`inviter.merchantId`) — `RegisterMerchantUseCase`와 달리 새 가맹점을 만드는
 *    게 아니라 기존 가맹점에 끼워 넣는 것이라, 그 가맹점이 어디인지를 요청 본문이
 *    아니라 신뢰할 수 있는 곳(방금 DB에서 읽은 요청자 자신의 소속)에서 가져와야
 *    한다 — 요청 본문에 `merchantId`를 받으면 호출자가 임의의 값을 실어 보내
 *    남의 가맹점에 계정을 만드는 멀티테넌시 취약점이 생긴다.
 *
 * `OWNER`는 이 경로로 만들 수 없다 — [MerchantUser.inviteSubAccount] 자체가 그
 * 제약을 갖고 있다(`require(role != MerchantUserRole.OWNER)`).
 *
 * `loginId`/`email`은 같은 가맹점 안에서만 유일하면 되므로([merchantUserRepository]로
 * 사전 확인) 겹치면 [DuplicateMerchantUserException]을 던진다 — `RegisterMerchantUseCase`가
 * 항상 새 `merchant_seq`를 만들어서 이 확인이 필요 없었던 것과 다른 점이다(여기는
 * 기존 `merchant_seq`에 끼워 넣으므로 실제로 충돌할 수 있다).
 *
 * `OutboxEvent`를 만들지 않는 이유는 [RegisterMerchantUseCase]와 같다(알려진 gap —
 * 그 KDoc 참고). [INVITATION_VALIDITY]도 같은 값·같은 이유로 고정한 MVP 상수다.
 */
class InviteMerchantSubAccountUseCase(
	private val merchantUserRepository: MerchantUserRepository,
	private val accountInvitationRepository: AccountInvitationRepository,
	private val invitationTokenHasher: InvitationTokenHasher,
	private val idGenerator: IdGenerator,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	fun execute(command: InviteMerchantSubAccountCommand): InviteMerchantSubAccountResult {
		val inviter =
			checkNotNull(merchantUserRepository.findById(command.invitedByMerchantUserId)) {
				"인증된 세션의 MerchantUser(${command.invitedByMerchantUserId.value})를 찾을 수 없습니다."
			}

		if (!inviter.canInviteSubAccounts()) {
			throw MerchantUserCannotInviteSubAccountsException(
				"MerchantUser(${inviter.id.value})는 하위 계정을 발급할 권한이 없습니다(role=${inviter.role}, status=${inviter.status}).",
			)
		}

		merchantUserRepository.findByMerchantIdAndLoginId(inviter.merchantId, command.loginId)?.let {
			throw DuplicateMerchantUserException("로그인 아이디(${command.loginId.value})가 이 가맹점에서 이미 사용 중입니다.")
		}
		merchantUserRepository.findByMerchantIdAndEmail(inviter.merchantId, command.email)?.let {
			throw DuplicateMerchantUserException("이메일(${command.email.value})이 이 가맹점에서 이미 사용 중입니다.")
		}

		val now = clock.instant()

		val subAccount =
			MerchantUser.inviteSubAccount(
				id = MerchantUserId("mu_" + idGenerator.newId()),
				merchantId = inviter.merchantId,
				loginId = command.loginId,
				email = command.email,
				userName = command.userName,
				role = command.role,
				invitedByMerchantUserId = inviter.id,
				createdAt = now,
			)

		val rawInvitationToken = idGenerator.newId()
		val invitation =
			AccountInvitation.forMerchantUser(
				id = AccountInvitationId("ai_" + idGenerator.newId()),
				merchantUserId = subAccount.id,
				tokenHash = invitationTokenHasher.hash(rawInvitationToken),
				expiresAt = now.plus(INVITATION_VALIDITY),
				createdAt = now,
			)

		return transactionManager.runInTransaction {
			merchantUserRepository.save(subAccount)
			accountInvitationRepository.save(invitation)
			InviteMerchantSubAccountResult(
				merchantUserId = subAccount.id,
				loginId = subAccount.loginId,
				email = subAccount.email,
				userName = subAccount.userName,
				role = subAccount.role,
				invitationToken = rawInvitationToken,
				invitationExpiresAt = invitation.expiresAt,
			)
		}
	}

	companion object {
		/** 초대 Token의 유효 기간. [IssueInternalUserUseCase]/[RegisterMerchantUseCase]와 같은 값이다. */
		private val INVITATION_VALIDITY: Duration = Duration.ofDays(7)
	}
}
