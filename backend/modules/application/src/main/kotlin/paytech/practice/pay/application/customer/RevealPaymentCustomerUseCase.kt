package paytech.practice.pay.application.customer

import paytech.practice.pay.application.port.outbound.CustomerPiiAccessAuditRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.PaymentCustomerRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.customer.CustomerPiiAccessAudit
import paytech.practice.pay.domain.customer.CustomerPiiAccessAuditId
import java.time.Clock

/**
 * 내부 운영자가 **구매자 원본을 열람하는** Use Case다
 * (`POST /admin/payment-customers/{paymentId}/reveal`,
 * `docs/architecture/admin-console-api.md`의 4.8).
 *
 * ## 이 저장소에서 복호화를 부르는 유일한 자리다
 *
 * 목록·상세·엑셀·검색은 전부 `*_masked` 컬럼을 읽으므로 복호화 경로를 아예 타지 않는다
 * (ADR-008의 4). 여기가 늘어나면 그만큼 원문이 샐 자리가 늘어난다 —
 * `PaymentCustomerCrypto.decrypt`의 호출부를 하나로 유지하는 것이 이 설계의 핵심이다.
 *
 * ## 기록에 실패하면 열람도 실패한다
 *
 * 복호화와 감사 기록을 **같은 트랜잭션**에 묶는다. 원문은 이미 화면에 나갔는데 기록만
 * 빠지면 "누가 봤나"에 영영 답할 수 없고, 그러면 `customer_pii_access_audit`을 둔 이유가
 * 사라진다. 상태를 바꾸지 않는데도 감사를 붙인 이 저장소의 유일한 자료다(ADR-008의 6).
 *
 * ## 없는 결제와 없는 구매자 정보를 구분하지 않는다
 *
 * 둘 다 [PaymentCustomerNotFoundException]이다. 나눠서 알려주면 "그 결제는 존재한다"가
 * 응답으로 새어 나간다. **결제 존재 여부를 먼저 확인하는 이유**는 그 구분을 위해서가 아니라,
 * `PaymentCustomerRepository`의 어댑터가 없는 결제 ID에 대해 예외로 죽기 때문이다
 * (`payment_seq` 해석이 실패한다) — 그대로 두면 `404`여야 할 요청이 `500`이 된다.
 */
class RevealPaymentCustomerUseCase(
	private val paymentRepository: PaymentRepository,
	private val paymentCustomerRepository: PaymentCustomerRepository,
	private val paymentCustomerCrypto: PaymentCustomerCrypto,
	private val customerPiiAccessAuditRepository: CustomerPiiAccessAuditRepository,
	private val idGenerator: IdGenerator,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	fun execute(command: RevealPaymentCustomerCommand): RevealPaymentCustomerResult {
		require(command.reason.isNotBlank()) { "열람 사유(reason)는 공백일 수 없습니다." }

		if (paymentRepository.findById(command.paymentId) == null) {
			throw PaymentCustomerNotFoundException(command.paymentId)
		}

		return transactionManager.runInTransaction {
			val encrypted =
				paymentCustomerRepository.findByPaymentId(command.paymentId)
					?: throw PaymentCustomerNotFoundException(command.paymentId)

			val now = clock.instant()
			val customer = paymentCustomerCrypto.decrypt(encrypted)

			customerPiiAccessAuditRepository.append(
				CustomerPiiAccessAudit(
					id = CustomerPiiAccessAuditId("cpa_" + idGenerator.newId()),
					internalUserId = command.actorInternalUserId,
					paymentId = command.paymentId,
					reason = command.reason,
					clientIp = command.clientIp,
					occurredAt = now,
				),
			)

			RevealPaymentCustomerResult(
				paymentId = command.paymentId,
				name = customer.name,
				email = customer.email,
				phone = customer.phone,
				revealedAt = now,
			)
		}
	}
}
