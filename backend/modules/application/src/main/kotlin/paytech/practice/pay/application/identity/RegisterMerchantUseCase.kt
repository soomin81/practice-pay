package paytech.practice.pay.application.identity

import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.InvitationTokenHasher
import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.identity.AccountInvitation
import paytech.practice.pay.domain.identity.AccountInvitationId
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.merchant.Merchant
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Clock
import java.time.Duration

/**
 * "가맹점 등록과 OWNER 생성" Use Case다(`docs/architecture/identity-access-api-key.md`의
 * "4.3 가맹점 등록과 OWNER 생성": "가맹점 등록 트랜잭션에서 `Merchant`와 최초
 * `MerchantUser(OWNER)`를 함께 생성한다"). `Merchant(ACTIVE)`, 최초
 * `MerchantUser(OWNER, INVITED)`, 그 계정을 활성화할 `AccountInvitation(PENDING)`을
 * 한 트랜잭션으로 함께 만든다 — [IssueInternalUserUseCase]가 `InternalUser +
 * AccountInvitation` 두 Aggregate로 확립한 "발급 + 초대" 패턴을 `Merchant`까지
 * 셋으로 넓힌 모양이다.
 *
 * 내부 운영자가 OWNER의 최종 비밀번호를 직접 정하지 않는다(같은 문서 "4.3") —
 * `MerchantUser.inviteInitialOwner`가 `passwordHash = null`인 `INVITED` 상태로
 * 만들고, 초대 Token을 받은 OWNER 본인이 `AcceptAccountInvitationUseCase`
 * (`api-merchant`가 노출)로 비밀번호를 설정해야 `ACTIVE`가 된다.
 *
 * **`OutboxEvent`는 만들지 않는다 — 알려진 gap이다.** `docs/database/database-design.md`의
 * "계정 생성 트랜잭션" 예시는 가맹점 등록에 `OutboxEvent INSERT`를 포함하지만,
 * `PublishOutboxEventUseCase.resolveMerchant()`는 오늘 `aggregateType="Payment"`만
 * 지원해서 다른 타입의 `OutboxEvent`를 만들면 `apps:batch`의 발행 Worker가 매
 * 폴링마다 예외를 던지며 영원히 재시도하는 상태로 남는다(발행 대상에서 스스로
 * 빠지지 않는다). 애초에 이 프로젝트에는 이메일 발송 인프라가 없어서 — 그
 * `OutboxEvent`가 실제로 무엇을 전달할지도 정해진 바 없다 — [IssueInternalUserUseCase]가
 * 이미 같은 이유로 `InternalUser` 초대에 `OutboxEvent`를 만들지 않은 선례를
 * 그대로 따른다: [RegisterMerchantResult.invitationToken] 원문을 API 응답으로
 * 직접 돌려주고, 호출한 내부 운영자가 OWNER에게 수동으로(Out-of-band) 전달한다.
 *
 * `merchantCode`는 DB Unique 제약([merchantRepository]로 사전 확인)이 걸려 있어
 * 겹치면 [DuplicateMerchantException]을 던진다 — [IssueInternalUserUseCase]의
 * `loginId`/`email` 중복 확인과 같은 한계(DB Unique 제약만큼 원자적이지 않다)를
 * 갖는다. `ownerLoginId`/`ownerEmail`은 이 Use Case가 항상 새로 만드는
 * `merchant_seq`에 대해서만 유일하면 되므로(`backend/CLAUDE.md`의 "Idempotency
 * keys") 사전 확인이 필요 없다 — 같은 값이 다른 가맹점에 이미 있어도 충돌이 아니다.
 *
 * [INVITATION_VALIDITY]는 [IssueInternalUserUseCase]와 같은 값·같은 이유로
 * 고정한 MVP 상수다.
 */
class RegisterMerchantUseCase(
	private val merchantRepository: MerchantRepository,
	private val merchantUserRepository: MerchantUserRepository,
	private val accountInvitationRepository: AccountInvitationRepository,
	private val invitationTokenHasher: InvitationTokenHasher,
	private val idGenerator: IdGenerator,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	fun execute(command: RegisterMerchantCommand): RegisterMerchantResult {
		merchantRepository.findByCode(command.merchantCode)?.let {
			throw DuplicateMerchantException("가맹점 코드(${command.merchantCode.value})가 이미 사용 중입니다.")
		}

		val now = clock.instant()

		val merchant =
			Merchant.create(
				id = MerchantId("mrc_" + idGenerator.newId()),
				code = command.merchantCode,
				name = command.merchantName,
				webhookUrl = command.webhookUrl,
				createdAt = now,
			)

		val owner =
			MerchantUser.inviteInitialOwner(
				id = MerchantUserId("mu_" + idGenerator.newId()),
				merchantId = merchant.id,
				loginId = command.ownerLoginId,
				email = command.ownerEmail,
				userName = command.ownerUserName,
				invitedByInternalUserId = command.registeredByInternalUserId,
				createdAt = now,
			)

		val rawInvitationToken = idGenerator.newId()
		val invitation =
			AccountInvitation.forMerchantUser(
				id = AccountInvitationId("ai_" + idGenerator.newId()),
				merchantUserId = owner.id,
				tokenHash = invitationTokenHasher.hash(rawInvitationToken),
				expiresAt = now.plus(INVITATION_VALIDITY),
				createdAt = now,
			)

		return transactionManager.runInTransaction {
			merchantRepository.save(merchant)
			merchantUserRepository.save(owner)
			accountInvitationRepository.save(invitation)
			RegisterMerchantResult(
				merchantId = merchant.id,
				merchantCode = merchant.code,
				merchantName = merchant.name,
				ownerMerchantUserId = owner.id,
				ownerLoginId = owner.loginId,
				ownerEmail = owner.email,
				invitationToken = rawInvitationToken,
				invitationExpiresAt = invitation.expiresAt,
			)
		}
	}

	companion object {
		/** 초대 Token의 유효 기간. [IssueInternalUserUseCase]와 같은 값이다. */
		private val INVITATION_VALIDITY: Duration = Duration.ofDays(7)
	}
}
