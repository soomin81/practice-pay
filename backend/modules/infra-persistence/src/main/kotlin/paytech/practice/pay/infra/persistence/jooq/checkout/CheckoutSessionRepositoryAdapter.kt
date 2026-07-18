package paytech.practice.pay.infra.persistence.jooq.checkout

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.CheckoutSessionRepository
import paytech.practice.pay.dbcore.jooq.tables.CheckoutSession.Companion.CHECKOUT_SESSION
import paytech.practice.pay.dbcore.jooq.tables.Payment.Companion.PAYMENT
import paytech.practice.pay.dbcore.jooq.tables.records.CheckoutSessionRecord
import paytech.practice.pay.domain.checkout.CheckoutSession
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.shared.WalletAddress
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [CheckoutSessionRepository] Port를 구현한다.
 *
 * `save`의 낙관적 잠금 한계는 [paytech.practice.pay.infra.persistence.jooq.payment.PaymentRepositoryAdapter]와
 * 동일하다 — 도메인 [CheckoutSession]이 자신의 `version`을 모르기 때문에, DB에서
 * 방금 읽은 version을 그대로 +1 해서 쓴다.
 */
@Repository
class CheckoutSessionRepositoryAdapter(
	private val dsl: DSLContext,
) : CheckoutSessionRepository {
	override fun save(checkoutSession: CheckoutSession) {
		val existing =
			dsl
				.selectFrom(CHECKOUT_SESSION)
				.where(CHECKOUT_SESSION.CHECKOUT_SESSION_ID.eq(checkoutSession.id.value))
				.fetchOne()

		if (existing == null) {
			dsl
				.newRecord(CHECKOUT_SESSION)
				.apply {
					fillFrom(checkoutSession)
					version = 0L
				}.insert()
		} else {
			dsl
				.update(CHECKOUT_SESSION)
				.set(CHECKOUT_SESSION.CHECKOUT_STATUS, checkoutSession.status.name)
				.set(CHECKOUT_SESSION.CONNECTED_WALLET_ADDRESS, checkoutSession.connectedWallet?.value)
				.set(CHECKOUT_SESSION.OPENED_AT, checkoutSession.openedAt?.toUtcLocalDateTime())
				.set(CHECKOUT_SESSION.WALLET_CONNECTED_AT, checkoutSession.walletConnectedAt?.toUtcLocalDateTime())
				.set(CHECKOUT_SESSION.PAYMENT_SUBMITTED_AT, checkoutSession.paymentSubmittedAt?.toUtcLocalDateTime())
				.set(CHECKOUT_SESSION.COMPLETED_AT, checkoutSession.completedAt?.toUtcLocalDateTime())
				.set(CHECKOUT_SESSION.UPDATED_AT, checkoutSession.updatedAt.toUtcLocalDateTime())
				.set(CHECKOUT_SESSION.VERSION, (existing.version ?: 0L) + 1)
				.where(CHECKOUT_SESSION.CHECKOUT_SESSION_SEQ.eq(existing.checkoutSessionSeq))
				.and(CHECKOUT_SESSION.VERSION.eq(existing.version))
				.execute()
				.also { updatedRows ->
					check(updatedRows == 1) {
						"CheckoutSession(${checkoutSession.id.value}) 저장에 실패했습니다 — " +
							"동시에 변경된 것으로 보입니다(예상 version=${existing.version})."
					}
				}
		}
	}

	override fun findById(checkoutSessionId: CheckoutSessionId): CheckoutSession? =
		dsl
			.selectFrom(CHECKOUT_SESSION)
			.where(CHECKOUT_SESSION.CHECKOUT_SESSION_ID.eq(checkoutSessionId.value))
			.fetchOne()
			?.let { it.toDomain(resolvePaymentId(it.paymentSeq!!)) }

	override fun findByPaymentId(paymentId: PaymentId): CheckoutSession? =
		dsl
			.selectFrom(CHECKOUT_SESSION)
			.where(CHECKOUT_SESSION.PAYMENT_SEQ.eq(resolvePaymentSeq(paymentId.value)))
			.fetchOne()
			?.toDomain(paymentId)

	private fun resolvePaymentSeq(paymentId: String): Long =
		dsl
			.select(PAYMENT.PAYMENT_SEQ)
			.from(PAYMENT)
			.where(PAYMENT.PAYMENT_ID.eq(paymentId))
			.fetchOne(PAYMENT.PAYMENT_SEQ)
			?: error("Payment($paymentId)를 찾을 수 없습니다.")

	private fun resolvePaymentId(paymentSeq: Long): PaymentId =
		dsl
			.select(PAYMENT.PAYMENT_ID)
			.from(PAYMENT)
			.where(PAYMENT.PAYMENT_SEQ.eq(paymentSeq))
			.fetchOne(PAYMENT.PAYMENT_ID)
			?.let { PaymentId(it) }
			?: error("Payment(seq=$paymentSeq)를 찾을 수 없습니다.")

	private fun CheckoutSessionRecord.fillFrom(checkoutSession: CheckoutSession) {
		checkoutSessionId = checkoutSession.id.value
		paymentSeq = resolvePaymentSeq(checkoutSession.paymentId.value)
		checkoutStatus = checkoutSession.status.name
		successUrl = checkoutSession.successUrl.value
		cancelUrl = checkoutSession.cancelUrl?.value
		connectedWalletAddress = checkoutSession.connectedWallet?.value
		openedAt = checkoutSession.openedAt?.toUtcLocalDateTime()
		walletConnectedAt = checkoutSession.walletConnectedAt?.toUtcLocalDateTime()
		paymentSubmittedAt = checkoutSession.paymentSubmittedAt?.toUtcLocalDateTime()
		completedAt = checkoutSession.completedAt?.toUtcLocalDateTime()
		expiresAt = checkoutSession.expiresAt.toUtcLocalDateTime()
		createdAt = checkoutSession.createdAt.toUtcLocalDateTime()
		updatedAt = checkoutSession.updatedAt.toUtcLocalDateTime()
	}

	private fun CheckoutSessionRecord.toDomain(paymentId: PaymentId): CheckoutSession =
		CheckoutSession.reconstitute(
			id = CheckoutSessionId(checkoutSessionId!!),
			paymentId = paymentId,
			successUrl = HttpUrl(successUrl!!),
			cancelUrl = cancelUrl?.let { HttpUrl(it) },
			expiresAt = expiresAt!!.toUtcInstant(),
			createdAt = createdAt!!.toUtcInstant(),
			connectedWallet = connectedWalletAddress?.let { WalletAddress(it) },
			status = CheckoutSessionStatus.valueOf(checkoutStatus!!),
			openedAt = openedAt?.toUtcInstant(),
			walletConnectedAt = walletConnectedAt?.toUtcInstant(),
			paymentSubmittedAt = paymentSubmittedAt?.toUtcInstant(),
			completedAt = completedAt?.toUtcInstant(),
			updatedAt = updatedAt!!.toUtcInstant(),
		)
}
