package paytech.practice.pay.application.checkout

import paytech.practice.pay.application.customer.PaymentCustomerCrypto
import paytech.practice.pay.application.port.outbound.CheckoutSessionRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.PaymentCustomerRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.customer.PaymentCustomer
import paytech.practice.pay.domain.customer.PaymentCustomerId
import java.time.Clock

/**
 * "체크아웃 구매자 정보 입력" Use Case다 — 고객이 체크아웃 페이지에서 이름·이메일·휴대전화를
 * 직접 입력하는 시점을 구현한다(ADR-008, `docs/architecture/checkout-api.md`의 4.3).
 *
 * ## 지갑 연결보다 **앞**이다
 *
 * 서명 이후에 입력을 요구하면 **돈은 나갔는데 결제가 미완인 창**이 생긴다. 그래서 이 단계가
 * 체크아웃에서 고객이 처음 취하는 행동이고, `CREATED`였던 세션을 `open()`하는 것도 이제
 * 여기다 — [ConnectCheckoutWalletUseCase]가 하던 것과 같은 처리를 한 칸 앞으로 옮긴 것이
 * 아니라, **두 곳 모두** 갖는다(고객이 입력을 건너뛰고 지갑부터 연결하는 순서가 API 상으로는
 * 여전히 가능하기 때문이다 — 순서를 강제하는 것은 프론트다).
 *
 * ## 다시 부르면 덮어쓴다
 *
 * 고객이 오타를 냈을 때 결제를 처음부터 다시 만들게 하는 것은 과하다. 이미 있으면
 * `PaymentCustomer.change`로 고치고, 없으면 새로 만든다 — `payment_seq`가 `UNIQUE`라
 * 결제 1건당 1건이 스키마로 보장된다.
 *
 * **어느 항목을 바꿨는지는 남기지 않는다.** 남기려면 옛 값을 어딘가 보관해야 하는데, 그건
 * 지우려고 만든 구조(파기 가능한 별도 테이블)와 정면으로 어긋난다.
 *
 * ## 이 Use Case는 평문을 다루는 몇 안 되는 자리다
 *
 * 암호화는 [PaymentCustomerCrypto]가 하고, 저장은 이미 암호화된 값만 오간다
 * ([PaymentCustomerRepository]의 KDoc 참고). 여기서 평문이 머무는 시간은 검증부터 암호화까지의
 * 한순간이고, 결과로 나가는 것은 마스킹된 값뿐이다.
 */
class SubmitCheckoutCustomerUseCase(
	private val checkoutSessionRepository: CheckoutSessionRepository,
	private val paymentCustomerRepository: PaymentCustomerRepository,
	private val paymentCustomerCrypto: PaymentCustomerCrypto,
	private val idGenerator: IdGenerator,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	/**
	 * 로드부터 저장까지를 **한 트랜잭션 안에서** 수행한다 — 변경할 목적의 읽기를 잠그고
	 * ([CheckoutSessionRepository.findByIdForUpdate]) 그 잠금을 저장까지 유지하기 위해서다.
	 * 여기서는 이유가 하나 더 있다: 같은 세션에 대한 중복 제출이 겹치면 `payment_customer`가
	 * 한 행을 두고 경합하는데, 세션 행 잠금이 그 경합의 직렬화 지점이 된다.
	 */
	fun execute(command: SubmitCheckoutCustomerCommand): SubmitCheckoutCustomerResult =
		transactionManager.runInTransaction {
			val checkoutSession =
				checkoutSessionRepository.findByIdForUpdate(command.checkoutSessionId)
					?: throw CheckoutSessionNotFoundException(command.checkoutSessionId)

			val now = clock.instant()
			if (now.isAfter(checkoutSession.expiresAt)) {
				throw CheckoutSessionExpiredException(command.checkoutSessionId)
			}
			if (!checkoutSession.status.acceptsCustomerInfo()) {
				throw CheckoutCustomerNotEditableException(command.checkoutSessionId, checkoutSession.status)
			}

			if (checkoutSession.status == CheckoutSessionStatus.CREATED) {
				checkoutSession.open(now)
				checkoutSessionRepository.save(checkoutSession)
			}

			val existing = paymentCustomerRepository.findByPaymentId(checkoutSession.paymentId)
			val customer =
				if (existing == null) {
					PaymentCustomer.create(
						id = PaymentCustomerId("pcu_" + idGenerator.newId()),
						paymentId = checkoutSession.paymentId,
						name = command.name,
						email = command.email,
						phone = command.phone,
						createdAt = now,
					)
				} else {
					// 복호화하지 않고 고친다 — 어차피 세 항목을 전부 새 값으로 덮어쓰므로
					// 옛 평문이 필요 없다. 수정 경로가 복호화를 타지 않는 편이 낫다.
					PaymentCustomer
						.reconstitute(
							id = existing.id,
							paymentId = existing.paymentId,
							name = command.name,
							email = command.email,
							phone = command.phone,
							createdAt = existing.createdAt,
							updatedAt = existing.updatedAt,
						).apply { change(command.name, command.email, command.phone, now) }
				}

			paymentCustomerRepository.save(paymentCustomerCrypto.encrypt(customer))

			SubmitCheckoutCustomerResult(
				checkoutSessionId = checkoutSession.id,
				checkoutSessionStatus = checkoutSession.status,
				nameMasked = customer.name.masked,
				emailMasked = customer.email.masked,
				phoneMasked = customer.phone.masked,
			)
		}
}

/**
 * 구매자 정보를 받을 수 있는 상태인지 — 경계는 취소와 같은 `PAYMENT_SUBMITTED`다
 * ([CheckoutCustomerNotEditableException]의 KDoc 참고).
 *
 * `CheckoutSession`에는 이 입력에 해당하는 상태 전이가 없어서 도메인의
 * `checkTransition`이 뒤에서 받쳐 주지 않는다 — 여기서 통과시키면 그대로 저장된다.
 */
private fun CheckoutSessionStatus.acceptsCustomerInfo(): Boolean =
	this == CheckoutSessionStatus.CREATED ||
		this == CheckoutSessionStatus.OPEN ||
		this == CheckoutSessionStatus.WALLET_CONNECTED
