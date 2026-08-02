package paytech.practice.pay.domain.settlement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.exchange.ExchangeOrderId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.SignedMoney
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private val CREATED_AT: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val ELIGIBLE_DATE: LocalDate = LocalDate.of(2026, 7, 18)

private fun newReceivable(adjustmentAmount: SignedMoney = SignedMoney.ZERO): SettlementReceivable =
	SettlementReceivable.create(
		id = SettlementReceivableId("stl_test_001"),
		paymentId = PaymentId("pay_test_001"),
		merchantId = MerchantId("mrc_test_001"),
		grossAmount = Money(100_000),
		feeRate = BigDecimal("0.02500000"),
		feeAmount = Money(2_500),
		adjustmentAmount = adjustmentAmount,
		eligibleDate = ELIGIBLE_DATE,
		createdAt = CREATED_AT,
	)

class SettlementReceivableTest :
	FunSpec({

		test("create starts in PENDING with netAmount computed from the formula") {
			val receivable = newReceivable()

			receivable.status shouldBe SettlementReceivableStatus.PENDING
			receivable.netAmount shouldBe Money(97_500)
			receivable.exchangeOrderId.shouldBeNull()
			receivable.updatedAt shouldBe CREATED_AT
		}

		test("create supports a negative adjustment amount") {
			val receivable = newReceivable(adjustmentAmount = SignedMoney(-1_000))

			receivable.netAmount shouldBe Money(96_500)
		}

		test("create supports a positive adjustment amount") {
			val receivable = newReceivable(adjustmentAmount = SignedMoney(1_000))

			receivable.netAmount shouldBe Money(98_500)
		}

		test("create rejects a non-positive grossAmount") {
			shouldThrow<IllegalArgumentException> {
				SettlementReceivable.create(
					id = SettlementReceivableId("stl_test_002"),
					paymentId = PaymentId("pay_test_001"),
					merchantId = MerchantId("mrc_test_001"),
					grossAmount = Money(0),
					feeRate = BigDecimal.ZERO,
					feeAmount = Money(0),
					adjustmentAmount = SignedMoney.ZERO,
					eligibleDate = ELIGIBLE_DATE,
					createdAt = CREATED_AT,
				)
			}
		}

		test("create rejects a negative feeRate") {
			shouldThrow<IllegalArgumentException> {
				SettlementReceivable.create(
					id = SettlementReceivableId("stl_test_003"),
					paymentId = PaymentId("pay_test_001"),
					merchantId = MerchantId("mrc_test_001"),
					grossAmount = Money(100_000),
					feeRate = BigDecimal("-0.01"),
					feeAmount = Money(2_500),
					adjustmentAmount = SignedMoney.ZERO,
					eligibleDate = ELIGIBLE_DATE,
					createdAt = CREATED_AT,
				)
			}
		}

		test("reconstitute rejects a netAmount that doesn't match the formula") {
			shouldThrow<IllegalArgumentException> {
				SettlementReceivable.reconstitute(
					id = SettlementReceivableId("stl_test_004"),
					paymentId = PaymentId("pay_test_001"),
					merchantId = MerchantId("mrc_test_001"),
					grossAmount = Money(100_000),
					feeRate = BigDecimal("0.025"),
					feeAmount = Money(2_500),
					adjustmentAmount = SignedMoney.ZERO,
					netAmount = Money(1),
					eligibleDate = ELIGIBLE_DATE,
					createdAt = CREATED_AT,
					exchangeOrderId = null,
					exchangeReceivedAmount = null,
					exchangeProfitLossAmount = null,
					status = SettlementReceivableStatus.PENDING,
					holdReasonCode = null,
					updatedAt = CREATED_AT,
				)
			}
		}

		test("markReady moves PENDING to READY and records the exchange result") {
			val receivable = newReceivable()
			val changedAt = CREATED_AT.plusSeconds(1)

			receivable.markReady(
				exchangeOrderId = ExchangeOrderId("exo_test_001"),
				exchangeReceivedAmount = Money(97_800),
				exchangeProfitLossAmount = SignedMoney(300),
				changedAt = changedAt,
			)

			receivable.status shouldBe SettlementReceivableStatus.READY
			receivable.exchangeOrderId shouldBe ExchangeOrderId("exo_test_001")
			receivable.exchangeReceivedAmount shouldBe Money(97_800)
			receivable.exchangeProfitLossAmount shouldBe SignedMoney(300)
			receivable.updatedAt shouldBe changedAt
		}

		test("markReady fails when not PENDING") {
			val receivable = newReceivable()
			receivable.markReady(ExchangeOrderId("exo_test_001"), Money(97_800), null, CREATED_AT.plusSeconds(1))

			shouldThrow<IllegalStateException> {
				receivable.markReady(ExchangeOrderId("exo_test_002"), Money(97_800), null, CREATED_AT.plusSeconds(2))
			}
		}

		test("hold moves PENDING or READY to HELD with a reason") {
			val fromPending = newReceivable()
			fromPending.hold("MERCHANT_UNDER_REVIEW", CREATED_AT.plusSeconds(1))
			fromPending.status shouldBe SettlementReceivableStatus.HELD
			fromPending.holdReasonCode shouldBe "MERCHANT_UNDER_REVIEW"

			val fromReady = newReceivable()
			fromReady.markReady(ExchangeOrderId("exo_test_001"), Money(97_800), null, CREATED_AT.plusSeconds(1))
			fromReady.hold("SUSPECTED_FRAUD", CREATED_AT.plusSeconds(2))
			fromReady.status shouldBe SettlementReceivableStatus.HELD
		}

		test("hold rejects a blank reason code") {
			val receivable = newReceivable()

			shouldThrow<IllegalArgumentException> { receivable.hold("   ", CREATED_AT.plusSeconds(1)) }
		}

		/**
		 * **돌아갈 상태를 저장해 두지 않고 `exchangeOrderId`에서 파생한다는 것**이 이 전이의
		 * 핵심이라, 두 갈래를 모두 고정한다. 매도 전에 막힌 채권을 `READY`로 되돌리면
		 * 근거 없는 정산 금액이 생긴다.
		 */
		test("release returns a held receivable to READY when the exchange already completed") {
			val receivable = newReceivable()
			receivable.markReady(ExchangeOrderId("exo_test_001"), Money(97_800), null, CREATED_AT.plusSeconds(1))
			receivable.hold("TRANSACTION_REORGED", CREATED_AT.plusSeconds(2))
			val changedAt = CREATED_AT.plusSeconds(3)

			receivable.release(changedAt)

			receivable.status shouldBe SettlementReceivableStatus.READY
			receivable.holdReasonCode shouldBe null
			receivable.updatedAt shouldBe changedAt
			// 해제가 환전 결과를 지우지 않는다 — READY의 근거 자체다.
			receivable.exchangeOrderId shouldBe ExchangeOrderId("exo_test_001")
		}

		test("release returns a held receivable to PENDING when the exchange hasn't happened") {
			val receivable = newReceivable()
			receivable.hold("TRANSACTION_REORGED", CREATED_AT.plusSeconds(1))

			receivable.release(CREATED_AT.plusSeconds(2))

			receivable.status shouldBe SettlementReceivableStatus.PENDING
			receivable.holdReasonCode shouldBe null
		}

		test("release fails when not HELD") {
			val receivable = newReceivable()

			shouldThrow<IllegalStateException> { receivable.release(CREATED_AT.plusSeconds(1)) }
		}

		/** 종료 상태는 재사용하지 않는다 — 취소된 채권을 해제로 되살릴 수 없다. */
		test("release cannot revive a cancelled receivable") {
			val receivable = newReceivable()
			receivable.hold("TRANSACTION_REORGED", CREATED_AT.plusSeconds(1))
			receivable.cancel(CREATED_AT.plusSeconds(2))

			shouldThrow<IllegalStateException> { receivable.release(CREATED_AT.plusSeconds(3)) }
		}

		test("cancel moves PENDING, READY or HELD to CANCELLED") {
			val fromPending = newReceivable()
			fromPending.cancel(CREATED_AT.plusSeconds(1))
			fromPending.status shouldBe SettlementReceivableStatus.CANCELLED

			val fromHeld = newReceivable()
			fromHeld.hold("REASON", CREATED_AT.plusSeconds(1))
			fromHeld.cancel(CREATED_AT.plusSeconds(2))
			fromHeld.status shouldBe SettlementReceivableStatus.CANCELLED
		}

		test("cancel fails once already CANCELLED") {
			val receivable = newReceivable()
			receivable.cancel(CREATED_AT.plusSeconds(1))

			shouldThrow<IllegalStateException> { receivable.cancel(CREATED_AT.plusSeconds(2)) }
		}

		test("reconstitute restores a READY receivable faithfully") {
			val updatedAt = CREATED_AT.plusSeconds(10)

			val receivable =
				SettlementReceivable.reconstitute(
					id = SettlementReceivableId("stl_test_005"),
					paymentId = PaymentId("pay_test_001"),
					merchantId = MerchantId("mrc_test_001"),
					grossAmount = Money(100_000),
					feeRate = BigDecimal("0.025"),
					feeAmount = Money(2_500),
					adjustmentAmount = SignedMoney.ZERO,
					netAmount = Money(97_500),
					eligibleDate = ELIGIBLE_DATE,
					createdAt = CREATED_AT,
					exchangeOrderId = ExchangeOrderId("exo_test_001"),
					exchangeReceivedAmount = Money(97_800),
					exchangeProfitLossAmount = SignedMoney(300),
					status = SettlementReceivableStatus.READY,
					holdReasonCode = null,
					updatedAt = updatedAt,
				)

			receivable.status shouldBe SettlementReceivableStatus.READY
			receivable.netAmount shouldBe Money(97_500)
			receivable.exchangeOrderId shouldBe ExchangeOrderId("exo_test_001")
		}
	})
