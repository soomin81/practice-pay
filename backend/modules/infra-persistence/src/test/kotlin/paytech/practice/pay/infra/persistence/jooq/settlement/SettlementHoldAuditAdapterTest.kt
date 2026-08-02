package paytech.practice.pay.infra.persistence.jooq.settlement

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.settlement.SettlementHoldAction
import paytech.practice.pay.domain.settlement.SettlementHoldAudit
import paytech.practice.pay.domain.settlement.SettlementHoldAuditId
import paytech.practice.pay.domain.settlement.SettlementReceivable
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.SignedMoney
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.identity.InternalUserRepositoryAdapter
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.insertTestPayment
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private val NOW: Instant = Instant.parse("2026-08-02T00:00:00Z")

private fun savedInternalUser(): InternalUser {
	val user =
		InternalUser.bootstrap(
			id = InternalUserId("iu_${uniqueSuffix()}"),
			loginId = LoginId("admin-${uniqueSuffix()}"),
			email = Email("${uniqueSuffix()}@example.com"),
			userName = "테스트 관리자",
			passwordHash = "hashed-password",
			createdAt = NOW,
		)
	InternalUserRepositoryAdapter(PersistenceTestSupport.dsl).save(user)
	return user
}

private fun savedReceivable(): SettlementReceivable {
	val merchantId = MerchantId(insertTestMerchant())
	val paymentId = PaymentId(insertTestPayment(merchantId.value))
	val receivable =
		SettlementReceivable.create(
			id = SettlementReceivableId("stl_${uniqueSuffix()}"),
			paymentId = paymentId,
			merchantId = merchantId,
			grossAmount = Money(20_000),
			feeRate = BigDecimal("0.015"),
			feeAmount = Money(300),
			adjustmentAmount = SignedMoney.ZERO,
			eligibleDate = LocalDate.of(2026, 8, 1),
			createdAt = NOW,
		)
	SettlementReceivableRepositoryAdapter(PersistenceTestSupport.dsl).save(receivable)
	return receivable
}

class SettlementHoldAuditAdapterTest :
	FunSpec({
		val recorder = SettlementHoldAuditRepositoryAdapter(PersistenceTestSupport.dsl)
		val projection = SettlementHoldAuditProjectionAdapter(PersistenceTestSupport.dsl)

		test("append persists an entry and findByReceivableId joins the operator name") {
			val user = savedInternalUser()
			val receivable = savedReceivable()
			recorder.append(
				SettlementHoldAudit(
					id = SettlementHoldAuditId("sha_${uniqueSuffix()}"),
					settlementReceivableId = receivable.id,
					internalUserId = user.id,
					action = SettlementHoldAction.HELD,
					reasonCode = "TRANSACTION_REORGED",
					note = null,
					occurredAt = NOW,
				),
			)

			val entry = projection.findByReceivableId(receivable.id).single()

			entry.internalUserId shouldBe user.id
			entry.internalUserName shouldBe "테스트 관리자"
			entry.action shouldBe SettlementHoldAction.HELD
			entry.reasonCode shouldBe "TRANSACTION_REORGED"
			entry.note.shouldBeNull()
			entry.occurredAt shouldBe NOW
		}

		/**
		 * **이력을 보는 자리가 "풀어도 되나"를 판단하는 화면이라** 최신 행위가 맨 위여야 한다.
		 * 같은 시각에 여러 행이 있어도 순서가 흔들리지 않는지까지 본다(seq가 tie-breaker다).
		 */
		test("findByReceivableId returns the newest action first") {
			val user = savedInternalUser()
			val receivable = savedReceivable()
			recorder.append(
				SettlementHoldAudit(
					id = SettlementHoldAuditId("sha_${uniqueSuffix()}"),
					settlementReceivableId = receivable.id,
					internalUserId = user.id,
					action = SettlementHoldAction.HELD,
					reasonCode = "TRANSACTION_REORGED",
					note = null,
					occurredAt = NOW,
				),
			)
			recorder.append(
				SettlementHoldAudit(
					id = SettlementHoldAuditId("sha_${uniqueSuffix()}"),
					settlementReceivableId = receivable.id,
					internalUserId = user.id,
					action = SettlementHoldAction.RELEASED,
					reasonCode = null,
					note = "탐지 오류로 확인되어 해제합니다.",
					occurredAt = NOW,
				),
			)

			val entries = projection.findByReceivableId(receivable.id)

			entries.map { it.action } shouldBe listOf(SettlementHoldAction.RELEASED, SettlementHoldAction.HELD)
			entries.first().note shouldBe "탐지 오류로 확인되어 해제합니다."
		}

		/** 다른 채권의 이력이 섞이면 "이 채권을 손댄 적이 있다"는 잘못된 판단을 부른다. */
		test("findByReceivableId does not leak another receivable's history") {
			val user = savedInternalUser()
			val mine = savedReceivable()
			val other = savedReceivable()
			recorder.append(
				SettlementHoldAudit(
					id = SettlementHoldAuditId("sha_${uniqueSuffix()}"),
					settlementReceivableId = other.id,
					internalUserId = user.id,
					action = SettlementHoldAction.HELD,
					reasonCode = "TRANSACTION_REORGED",
					note = null,
					occurredAt = NOW,
				),
			)

			projection.findByReceivableId(mine.id) shouldBe emptyList()
		}
	})
