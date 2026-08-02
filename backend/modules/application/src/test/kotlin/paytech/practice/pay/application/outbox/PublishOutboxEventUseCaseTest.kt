package paytech.practice.pay.application.outbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.application.port.outbound.WebhookDeliveryRepository
import paytech.practice.pay.application.port.outbound.WebhookSendResult
import paytech.practice.pay.application.port.outbound.WebhookSender
import paytech.practice.pay.application.port.outbound.WebhookSigner
import paytech.practice.pay.domain.merchant.Merchant
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.outbox.OutboxEvent
import paytech.practice.pay.domain.outbox.OutboxEventStatus
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.EventId
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import paytech.practice.pay.domain.webhook.WebhookDelivery
import paytech.practice.pay.domain.webhook.WebhookDeliveryId
import paytech.practice.pay.domain.webhook.WebhookDeliveryStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val EVENT_ID = EventId("evt_test_001")
private val PAYMENT_ID = PaymentId("pay_test_001")
private val MERCHANT_ID = MerchantId("mrc_test_001")
private val WEBHOOK_URL = HttpUrl("https://merchant.example.com/webhooks")

private fun newOutboxEvent(): OutboxEvent =
	OutboxEvent.create(
		eventId = EVENT_ID,
		aggregateType = "Payment",
		aggregateId = PAYMENT_ID.value,
		eventType = "payment.succeeded",
		payload = """{"paymentId":"${PAYMENT_ID.value}"}""",
		occurredAt = NOW.minusSeconds(10),
		createdAt = NOW.minusSeconds(10),
	)

private fun newPayment(): Payment =
	Payment.create(
		id = PAYMENT_ID,
		merchantId = MERCHANT_ID,
		merchantOrderId = MerchantOrderId("order-001"),
		orderName = "테스트 주문",
		orderAmount = Money(10_000),
		paymentAsset = Asset.USDC,
		paymentAmount = TokenAmount(6_666_667),
		tokenDecimals = 6,
		network = BlockchainNetwork.BASE_SEPOLIA,
		receivingWallet = WalletAddress("0x" + "a".repeat(40)),
		expiresAt = NOW.plusSeconds(1_800),
		createdAt = NOW.minusSeconds(60),
	)

private fun newMerchant(webhookUrl: HttpUrl?): Merchant =
	Merchant.create(
		id = MERCHANT_ID,
		code = MerchantCode("merchant-001"),
		name = "테스트 가맹점",
		webhookUrl = webhookUrl,
		createdAt = NOW.minusSeconds(3_600),
	)

private class PublishImmediateTransactionManager : TransactionManager {
	override fun <T> runInTransaction(block: () -> T): T = block()
}

private class PublishFakeIdGenerator : IdGenerator {
	private var counter = 0

	override fun newId(): String {
		counter += 1
		return "id$counter"
	}
}

/**
 * 서명 자체의 정확성은 `HmacWebhookSignerTest`가 지킨다 — 여기서는 Use Case가
 * **서명을 만들어 전송에 넘기는지**만 보면 되므로, 입력을 그대로 드러내는 가짜를 쓴다.
 */
private class FakeWebhookSigner : WebhookSigner {
	override fun deriveSecret(
		merchantId: MerchantId,
		secretVersion: Int,
	): String = "whsec_${merchantId.value}_$secretVersion"

	override fun signatureHeaderValue(
		merchantId: MerchantId,
		secretVersion: Int,
		payload: String,
		signedAt: Instant,
	): String = "t=${signedAt.epochSecond},v1=${merchantId.value}:$secretVersion:${payload.length}"
}

private fun newUseCase(
	outboxEventRepository: OutboxEventRepository,
	webhookDeliveryRepository: WebhookDeliveryRepository = mockk(relaxed = true),
	paymentRepository: PaymentRepository = mockk(relaxed = true),
	merchantRepository: MerchantRepository = mockk(relaxed = true),
	webhookSender: WebhookSender = mockk(),
): PublishOutboxEventUseCase =
	PublishOutboxEventUseCase(
		outboxEventRepository = outboxEventRepository,
		webhookDeliveryRepository = webhookDeliveryRepository,
		paymentRepository = paymentRepository,
		merchantRepository = merchantRepository,
		webhookSender = webhookSender,
		webhookSigner = FakeWebhookSigner(),
		idGenerator = PublishFakeIdGenerator(),
		transactionManager = PublishImmediateTransactionManager(),
		clock = FIXED_CLOCK,
	)

class PublishOutboxEventUseCaseTest :
	FunSpec({

		test("merchant without a webhook url publishes without creating a WebhookDelivery") {
			val outboxEventRepository = mockk<OutboxEventRepository>(relaxed = true)
			val webhookDeliveryRepository = mockk<WebhookDeliveryRepository>(relaxed = true)
			val paymentRepository = mockk<PaymentRepository>()
			val merchantRepository = mockk<MerchantRepository>()
			every { outboxEventRepository.findById(EVENT_ID) } returns newOutboxEvent()
			every { paymentRepository.findById(PAYMENT_ID) } returns newPayment()
			every { merchantRepository.findById(MERCHANT_ID) } returns newMerchant(webhookUrl = null)

			val result =
				newUseCase(outboxEventRepository, webhookDeliveryRepository, paymentRepository, merchantRepository)
					.execute(PublishOutboxEventCommand(EVENT_ID))

			result.outboxEventStatus shouldBe OutboxEventStatus.PUBLISHED
			verify(exactly = 0) { webhookDeliveryRepository.findByEventIdAndMerchantId(any(), any()) }
			verify(exactly = 0) { webhookDeliveryRepository.save(any()) }
		}

		test("a 2xx webhook response succeeds both the WebhookDelivery and the OutboxEvent") {
			val outboxEventRepository = mockk<OutboxEventRepository>(relaxed = true)
			val webhookDeliveryRepository = mockk<WebhookDeliveryRepository>(relaxed = true)
			val paymentRepository = mockk<PaymentRepository>()
			val merchantRepository = mockk<MerchantRepository>()
			val webhookSender = mockk<WebhookSender>()
			every { outboxEventRepository.findById(EVENT_ID) } returns newOutboxEvent()
			every { paymentRepository.findById(PAYMENT_ID) } returns newPayment()
			every { merchantRepository.findById(MERCHANT_ID) } returns newMerchant(webhookUrl = WEBHOOK_URL)
			every { webhookDeliveryRepository.findByEventIdAndMerchantId(EVENT_ID, MERCHANT_ID) } returns null
			every { webhookSender.send(WEBHOOK_URL, any(), any()) } returns WebhookSendResult.Responded(200)
			val savedDeliveries = mutableListOf<WebhookDelivery>()
			every { webhookDeliveryRepository.save(capture(savedDeliveries)) } returns Unit

			val result =
				newUseCase(outboxEventRepository, webhookDeliveryRepository, paymentRepository, merchantRepository, webhookSender)
					.execute(PublishOutboxEventCommand(EVENT_ID))

			result.outboxEventStatus shouldBe OutboxEventStatus.PUBLISHED
			savedDeliveries.single().status shouldBe WebhookDeliveryStatus.SUCCEEDED
		}

		/**
		 * **서명 없이 나가면 이 기능 전체가 무의미하다** — 가맹점은 받은 요청이 PG에서
		 * 온 것인지 확인할 수 없고, 수신 URL만 아는 누구나 `payment.succeeded`를 위조할
		 * 수 있다. 그래서 "전송에 서명이 실려 나간다"를 회귀로 고정한다.
		 */
		test("the outgoing webhook carries a signature derived from the merchant's secret version") {
			val outboxEventRepository = mockk<OutboxEventRepository>(relaxed = true)
			val webhookDeliveryRepository = mockk<WebhookDeliveryRepository>(relaxed = true)
			val paymentRepository = mockk<PaymentRepository>()
			val merchantRepository = mockk<MerchantRepository>()
			val webhookSender = mockk<WebhookSender>()
			val rotatedMerchant =
				newMerchant(webhookUrl = WEBHOOK_URL).apply { rotateWebhookSecret(NOW) }
			every { outboxEventRepository.findById(EVENT_ID) } returns newOutboxEvent()
			every { paymentRepository.findById(PAYMENT_ID) } returns newPayment()
			every { merchantRepository.findById(MERCHANT_ID) } returns rotatedMerchant
			every { webhookDeliveryRepository.findByEventIdAndMerchantId(EVENT_ID, MERCHANT_ID) } returns null
			val signatures = mutableListOf<String>()
			every { webhookSender.send(WEBHOOK_URL, any(), capture(signatures)) } returns WebhookSendResult.Responded(200)

			newUseCase(outboxEventRepository, webhookDeliveryRepository, paymentRepository, merchantRepository, webhookSender)
				.execute(PublishOutboxEventCommand(EVENT_ID))

			// 교체된 세대(2)가 서명에 반영돼야 한다 — 반영되지 않으면 교체가 아무것도
			// 무효화하지 못한다.
			signatures.single() shouldBe "t=${NOW.epochSecond},v1=${MERCHANT_ID.value}:2:${newOutboxEvent().payload.length}"
		}

		test("a non-2xx webhook response below the attempt limit schedules a retry on both aggregates") {
			val outboxEventRepository = mockk<OutboxEventRepository>(relaxed = true)
			val webhookDeliveryRepository = mockk<WebhookDeliveryRepository>(relaxed = true)
			val paymentRepository = mockk<PaymentRepository>()
			val merchantRepository = mockk<MerchantRepository>()
			val webhookSender = mockk<WebhookSender>()
			every { outboxEventRepository.findById(EVENT_ID) } returns newOutboxEvent()
			every { paymentRepository.findById(PAYMENT_ID) } returns newPayment()
			every { merchantRepository.findById(MERCHANT_ID) } returns newMerchant(webhookUrl = WEBHOOK_URL)
			every { webhookDeliveryRepository.findByEventIdAndMerchantId(EVENT_ID, MERCHANT_ID) } returns null
			every { webhookSender.send(WEBHOOK_URL, any(), any()) } returns WebhookSendResult.Responded(500)
			val savedDeliveries = mutableListOf<WebhookDelivery>()
			val savedOutboxEvents = mutableListOf<OutboxEvent>()
			every { webhookDeliveryRepository.save(capture(savedDeliveries)) } returns Unit
			every { outboxEventRepository.save(capture(savedOutboxEvents)) } returns Unit

			val result =
				newUseCase(outboxEventRepository, webhookDeliveryRepository, paymentRepository, merchantRepository, webhookSender)
					.execute(PublishOutboxEventCommand(EVENT_ID))

			result.outboxEventStatus shouldBe OutboxEventStatus.RETRY_WAITING
			savedDeliveries.single().status shouldBe WebhookDeliveryStatus.RETRY_WAITING
			savedOutboxEvents.single().status shouldBe OutboxEventStatus.RETRY_WAITING
		}

		test("a resumed WebhookDelivery at the attempt limit fails both aggregates instead of retrying") {
			val existingDelivery =
				WebhookDelivery.create(
					id = WebhookDeliveryId("wh_test_001"),
					merchantId = MERCHANT_ID,
					eventId = EVENT_ID,
					eventType = "payment.succeeded",
					aggregateType = "Payment",
					aggregateId = PAYMENT_ID.value,
					destinationUrl = WEBHOOK_URL,
					payload = """{"paymentId":"${PAYMENT_ID.value}"}""",
					createdAt = NOW.minusSeconds(600),
				)
			repeat(4) {
				existingDelivery.startDelivering(NOW.minusSeconds(500))
				existingDelivery.scheduleRetry(500, "boom", NOW.minusSeconds(400), NOW.minusSeconds(400))
			}
			val outboxEventRepository = mockk<OutboxEventRepository>(relaxed = true)
			val webhookDeliveryRepository = mockk<WebhookDeliveryRepository>(relaxed = true)
			val paymentRepository = mockk<PaymentRepository>()
			val merchantRepository = mockk<MerchantRepository>()
			val webhookSender = mockk<WebhookSender>()
			every { outboxEventRepository.findById(EVENT_ID) } returns newOutboxEvent()
			every { paymentRepository.findById(PAYMENT_ID) } returns newPayment()
			every { merchantRepository.findById(MERCHANT_ID) } returns newMerchant(webhookUrl = WEBHOOK_URL)
			every { webhookDeliveryRepository.findByEventIdAndMerchantId(EVENT_ID, MERCHANT_ID) } returns existingDelivery
			every { webhookSender.send(WEBHOOK_URL, any(), any()) } returns WebhookSendResult.Failed("connection refused")
			val savedDeliveries = mutableListOf<WebhookDelivery>()
			val savedOutboxEvents = mutableListOf<OutboxEvent>()
			every { webhookDeliveryRepository.save(capture(savedDeliveries)) } returns Unit
			every { outboxEventRepository.save(capture(savedOutboxEvents)) } returns Unit

			val result =
				newUseCase(outboxEventRepository, webhookDeliveryRepository, paymentRepository, merchantRepository, webhookSender)
					.execute(PublishOutboxEventCommand(EVENT_ID))

			result.outboxEventStatus shouldBe OutboxEventStatus.FAILED
			savedDeliveries.single().status shouldBe WebhookDeliveryStatus.FAILED
			savedOutboxEvents.single().status shouldBe OutboxEventStatus.FAILED
		}

		test("throws OutboxEventNotFoundException when the id does not exist") {
			val outboxEventRepository = mockk<OutboxEventRepository>()
			every { outboxEventRepository.findById(EVENT_ID) } returns null

			shouldThrow<OutboxEventNotFoundException> {
				newUseCase(outboxEventRepository).execute(PublishOutboxEventCommand(EVENT_ID))
			}
		}

		test("throws when the OutboxEvent is already in a terminal or in-flight state") {
			val outboxEvent = newOutboxEvent()
			outboxEvent.startPublishing(NOW.minusSeconds(5))
			val outboxEventRepository = mockk<OutboxEventRepository>()
			every { outboxEventRepository.findById(EVENT_ID) } returns outboxEvent

			shouldThrow<IllegalStateException> {
				newUseCase(outboxEventRepository).execute(PublishOutboxEventCommand(EVENT_ID))
			}
		}
	})
