package paytech.practice.pay.application.payment

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.BlockchainTransactionRepository
import paytech.practice.pay.application.port.outbound.SettlementHoldAuditRepository
import paytech.practice.pay.application.port.outbound.SettlementReceivableRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.blockchain.BlockchainTransaction
import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.blockchain.BlockchainTransactionStatus
import paytech.practice.pay.domain.blockchain.ChainId
import paytech.practice.pay.domain.blockchain.ContractAddress
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.blockchain.TransactionType
import paytech.practice.pay.domain.exchange.ExchangeOrderId
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.settlement.SettlementHoldAction
import paytech.practice.pay.domain.settlement.SettlementHoldAudit
import paytech.practice.pay.domain.settlement.SettlementReceivable
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.SignedMoney
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-08-02T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val TX_ID = BlockchainTransactionId("btx_test_001")
private val PAYMENT_ID = PaymentId("pay_test_001")
private val ACTOR_ID = InternalUserId("iu_test_001")

/** 같은 패키지의 다른 테스트가 가진 것은 private이라 여기에 따로 둔다. */
private class ReorgImmediateTransactionManager : TransactionManager {
	override fun <T> runInTransaction(block: () -> T): T = block()
}

private fun confirmedTransaction(): BlockchainTransaction =
	BlockchainTransaction
		.create(
			id = TX_ID,
			paymentId = PAYMENT_ID,
			transactionType = TransactionType.PAYMENT,
			network = BlockchainNetwork.BASE_SEPOLIA,
			chainId = ChainId(84_532),
			transactionHash = TransactionHash("0x" + "d".repeat(64)),
			fromAddress = WalletAddress("0x" + "a".repeat(40)),
			toAddress = WalletAddress("0x" + "b".repeat(40)),
			tokenContractAddress = ContractAddress("0x" + "c".repeat(40)),
			tokenAsset = Asset.USDC,
			amountMinor = TokenAmount(14_357_502),
			requiredConfirmationCount = 12,
			submittedAt = NOW.minusSeconds(600),
		).apply {
			detect(1_000L, NOW.minusSeconds(500))
			startConfirming(NOW.minusSeconds(400))
			confirm(NOW.minusSeconds(300))
		}

private fun readyReceivable(): SettlementReceivable =
	SettlementReceivable
		.create(
			id = SettlementReceivableId("stl_test_001"),
			paymentId = PAYMENT_ID,
			merchantId = MerchantId("mrc_test_001"),
			grossAmount = Money(20_000),
			feeRate = BigDecimal("0.015"),
			feeAmount = Money(300),
			adjustmentAmount = SignedMoney(0),
			eligibleDate = LocalDate.parse("2026-08-01"),
			createdAt = NOW.minusSeconds(200),
		).apply {
			markReady(
				exchangeOrderId = ExchangeOrderId("exo_test_001"),
				exchangeReceivedAmount = Money(20_101),
				exchangeProfitLossAmount = SignedMoney(101),
				changedAt = NOW.minusSeconds(100),
			)
		}

private fun newUseCase(
	transactionRepository: BlockchainTransactionRepository,
	receivableRepository: SettlementReceivableRepository = mockk(relaxed = true),
	auditRepository: SettlementHoldAuditRepository = mockk(relaxed = true),
): MarkTransactionReorgedUseCase =
	MarkTransactionReorgedUseCase(
		blockchainTransactionRepository = transactionRepository,
		settlementReceivableRepository = receivableRepository,
		settlementHoldAuditRepository = auditRepository,
		idGenerator = { "generated-id" },
		transactionManager = ReorgImmediateTransactionManager(),
		clock = FIXED_CLOCK,
	)

class MarkTransactionReorgedUseCaseTest :
	FunSpec({

		/**
		 * **둘이 함께 일어나지 않으면 아무 돈도 막지 못한다.** 거래만 `REORGED`로 바꾸고
		 * 정산을 두면 표시만 바뀌고 지급은 그대로 나간다 — 이 기능의 목적 전체가 이
		 * 테스트다.
		 */
		test("marks the transaction reorged and holds the settlement receivable together") {
			val transactionRepository = mockk<BlockchainTransactionRepository>(relaxed = true)
			val receivableRepository = mockk<SettlementReceivableRepository>(relaxed = true)
			val transaction = confirmedTransaction()
			val receivable = readyReceivable()
			every { transactionRepository.findById(TX_ID) } returns transaction
			every { receivableRepository.findByPaymentId(PAYMENT_ID) } returns receivable

			val result = newUseCase(transactionRepository, receivableRepository).execute(MarkTransactionReorgedCommand(TX_ID, ACTOR_ID))

			transaction.status shouldBe BlockchainTransactionStatus.REORGED
			receivable.status shouldBe SettlementReceivableStatus.HELD
			result.settlementHeld shouldBe true
			verify(exactly = 1) { transactionRepository.save(transaction) }
			verify(exactly = 1) { receivableRepository.save(receivable) }
		}

		/** 사람이 나중에 "왜 막혔나"를 물을 때 이 코드 하나로 답이 되어야 한다. */
		test("records why the settlement was held") {
			val transactionRepository = mockk<BlockchainTransactionRepository>(relaxed = true)
			val receivableRepository = mockk<SettlementReceivableRepository>(relaxed = true)
			val receivable = readyReceivable()
			every { transactionRepository.findById(TX_ID) } returns confirmedTransaction()
			every { receivableRepository.findByPaymentId(PAYMENT_ID) } returns receivable

			newUseCase(transactionRepository, receivableRepository).execute(MarkTransactionReorgedCommand(TX_ID, ACTOR_ID))

			receivable.holdReasonCode shouldBe "TRANSACTION_REORGED"
		}

		/**
		 * `holdReasonCode`는 해제하면 지워지므로 **막았다는 사실 자체는 이력에만 남는다** —
		 * 누가 막았는지까지 남아야 나중에 해제와 나란히 읽힌다.
		 */
		test("records who held the settlement in the audit trail") {
			val transactionRepository = mockk<BlockchainTransactionRepository>(relaxed = true)
			val receivableRepository = mockk<SettlementReceivableRepository>(relaxed = true)
			val auditRepository = mockk<SettlementHoldAuditRepository>(relaxed = true)
			val audit = slot<SettlementHoldAudit>()
			every { transactionRepository.findById(TX_ID) } returns confirmedTransaction()
			every { receivableRepository.findByPaymentId(PAYMENT_ID) } returns readyReceivable()
			every { auditRepository.append(capture(audit)) } returns Unit

			newUseCase(transactionRepository, receivableRepository, auditRepository)
				.execute(MarkTransactionReorgedCommand(TX_ID, ACTOR_ID))

			audit.captured.action shouldBe SettlementHoldAction.HELD
			audit.captured.internalUserId shouldBe ACTOR_ID
			audit.captured.reasonCode shouldBe "TRANSACTION_REORGED"
			audit.captured.occurredAt shouldBe NOW
		}

		/** 막은 것이 없으면 남길 이력도 없다 — 빈 이력 행이 쌓이면 "언제 막혔나"가 흐려진다. */
		test("writes no audit row when there was nothing to hold") {
			val transactionRepository = mockk<BlockchainTransactionRepository>(relaxed = true)
			val receivableRepository = mockk<SettlementReceivableRepository>(relaxed = true)
			val auditRepository = mockk<SettlementHoldAuditRepository>(relaxed = true)
			every { transactionRepository.findById(TX_ID) } returns confirmedTransaction()
			every { receivableRepository.findByPaymentId(PAYMENT_ID) } returns null

			newUseCase(transactionRepository, receivableRepository, auditRepository)
				.execute(MarkTransactionReorgedCommand(TX_ID, ACTOR_ID))

			verify(exactly = 0) { auditRepository.append(any()) }
		}

		/**
		 * **채권이 아직 없는 쪽이 오히려 위험하다** — 매도 Worker가 이 결제를 집어 채권을
		 * 만들 수 있다는 뜻이다. 결과가 그 사실을 담아야 화면이 경고할 수 있다.
		 */
		test("reports that nothing was held when the receivable does not exist yet") {
			val transactionRepository = mockk<BlockchainTransactionRepository>(relaxed = true)
			val receivableRepository = mockk<SettlementReceivableRepository>(relaxed = true)
			every { transactionRepository.findById(TX_ID) } returns confirmedTransaction()
			every { receivableRepository.findByPaymentId(PAYMENT_ID) } returns null

			val result = newUseCase(transactionRepository, receivableRepository).execute(MarkTransactionReorgedCommand(TX_ID, ACTOR_ID))

			result.settlementHeld shouldBe false
			verify(exactly = 0) { receivableRepository.save(any()) }
		}

		/** 이미 막혔거나 취소된 채권은 다시 건드리지 않는다 — 상태 전이가 예외를 던진다. */
		test("leaves a receivable that is already held alone") {
			val transactionRepository = mockk<BlockchainTransactionRepository>(relaxed = true)
			val receivableRepository = mockk<SettlementReceivableRepository>(relaxed = true)
			val alreadyHeld = readyReceivable().apply { hold("SOMETHING_ELSE", NOW.minusSeconds(50)) }
			every { transactionRepository.findById(TX_ID) } returns confirmedTransaction()
			every { receivableRepository.findByPaymentId(PAYMENT_ID) } returns alreadyHeld

			val result = newUseCase(transactionRepository, receivableRepository).execute(MarkTransactionReorgedCommand(TX_ID, ACTOR_ID))

			result.settlementHeld shouldBe false
			// 먼저 기록된 사유를 덮어쓰지 않는다.
			alreadyHeld.holdReasonCode shouldBe "SOMETHING_ELSE"
		}

		test("an unknown transaction is reported as not found") {
			val transactionRepository = mockk<BlockchainTransactionRepository>()
			every { transactionRepository.findById(TX_ID) } returns null

			shouldThrow<BlockchainTransactionNotFoundException> {
				newUseCase(transactionRepository).execute(MarkTransactionReorgedCommand(TX_ID, ACTOR_ID))
			}
		}

		/**
		 * **확정 전에는 사람이 끼어들 이유가 없다** — 자동 경로(Confirm 폴링)가 유예를 두고
		 * 판단한다. 여기서 막지 않으면 노드가 잠깐 뒤처졌을 때 멀쩡한 결제의 정산이 막힌다.
		 */
		test("refuses a transaction that has not been confirmed and says its current status") {
			val transactionRepository = mockk<BlockchainTransactionRepository>(relaxed = true)
			val receivableRepository = mockk<SettlementReceivableRepository>(relaxed = true)
			val confirming =
				confirmedTransaction().let {
					// 아직 확정되지 않은 거래를 따로 만든다.
					BlockchainTransaction
						.create(
							id = TX_ID,
							paymentId = PAYMENT_ID,
							transactionType = TransactionType.PAYMENT,
							network = BlockchainNetwork.BASE_SEPOLIA,
							chainId = ChainId(84_532),
							transactionHash = TransactionHash("0x" + "e".repeat(64)),
							fromAddress = null,
							toAddress = null,
							tokenContractAddress = null,
							tokenAsset = Asset.USDC,
							amountMinor = null,
							requiredConfirmationCount = 12,
							submittedAt = NOW.minusSeconds(600),
						).apply {
							detect(1_000L, NOW.minusSeconds(500))
							startConfirming(NOW.minusSeconds(400))
						}
				}
			every { transactionRepository.findById(TX_ID) } returns confirming

			val thrown =
				shouldThrow<TransactionNotReorgeableException> {
					newUseCase(transactionRepository, receivableRepository).execute(MarkTransactionReorgedCommand(TX_ID, ACTOR_ID))
				}

			thrown.status shouldBe BlockchainTransactionStatus.CONFIRMING
			// 거절했으면 아무것도 저장하지 않아야 한다.
			verify(exactly = 0) { transactionRepository.save(any()) }
			verify(exactly = 0) { receivableRepository.save(any()) }
		}
	})
