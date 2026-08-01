package paytech.practice.pay.api.admin.web

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.application.payment.ExportPaymentsUseCase
import paytech.practice.pay.application.payment.GetPaymentDetailUseCase
import paytech.practice.pay.application.payment.ListPaymentsCommand
import paytech.practice.pay.application.payment.ListPaymentsUseCase
import paytech.practice.pay.application.payment.PaymentNotFoundException
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import java.time.Clock
import java.time.Instant

/**
 * 내부 운영자 콘솔의 **결제 내역 조회** API를 노출하는 inbound Adapter다
 * (`docs/architecture/admin-console-api.md`).
 *
 * **인증된 내부 사용자 전원이 조회할 수 있다**(`VIEWER` 포함) — `SecurityConfig`가
 * `/admin/payments`를 따로 좁히지 않아 `anyRequest authenticated`에 걸린다. 결제 내역은
 * 내부 운영의 기본 조회 대상이라 `GET /admin/merchants`와 같은 스코핑으로 뒀다.
 *
 * 상태 문자열이 [PaymentStatus]에 없는 값이면 `IllegalArgumentException`이 나고
 * [AdminApiExceptionHandler]가 `400`으로 옮긴다.
 */
@RestController
@RequestMapping("/admin/payments")
class AdminPaymentController(
	private val listPaymentsUseCase: ListPaymentsUseCase,
	private val exportPaymentsUseCase: ExportPaymentsUseCase,
	private val getPaymentDetailUseCase: GetPaymentDetailUseCase,
	private val clock: Clock,
) {
	/**
	 * 결제 한 건의 전체 맥락(견적·체크아웃·온체인·환전·정산·Webhook 전송)을 돌려준다.
	 * 없으면 `404`다 — [PaymentNotFoundException]을 [AdminApiExceptionHandler]가 옮긴다.
	 *
	 * **`/export`와 경로가 겹치지 않는다** — Spring의 경로 매칭이 리터럴 세그먼트를 경로
	 * 변수보다 먼저 고르기 때문이다(`/admin/payments/export`는 아래 내보내기로 간다).
	 * 이 우선순위에 기대고 있으므로 회귀 테스트로 고정해 뒀다.
	 */
	@GetMapping("/{paymentId}")
	fun getPaymentDetail(
		@PathVariable paymentId: String,
	): PaymentDetailResponse {
		val view =
			getPaymentDetailUseCase.execute(PaymentId(paymentId))
				?: throw PaymentNotFoundException(paymentId)

		return toResponse(view)
	}

	/**
	 * 현재 필터에 걸린 결제를 `.xlsx`로 내려준다. 조회와 **같은 필터**를 받되 페이징
	 * 파라미터는 받지 않는다 — 내보내기는 페이지 단위가 아니라 조건 전체가 대상이다
	 * (상한은 `PaymentExportPolicy.MAX_EXPORT_ROWS`, 넘치면 응답 헤더로 알린다).
	 */
	@GetMapping("/export")
	fun exportPayments(
		@RequestParam(required = false) merchantId: String?,
		@RequestParam(required = false) status: String?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
	): ResponseEntity<ByteArray> {
		val result =
			exportPaymentsUseCase.execute(
				ListPaymentsCommand(
					merchantId = merchantId?.takeIf { it.isNotBlank() }?.let { MerchantId(it) },
					status = status?.takeIf { it.isNotBlank() }?.let { PaymentStatus.valueOf(it) },
					createdFrom = from,
					createdTo = to,
				),
			)

		return spreadsheetDownload(result, filePrefix = "payments", clock = clock)
	}

	@GetMapping
	fun listPayments(
		@RequestParam(required = false) merchantId: String?,
		@RequestParam(required = false) status: String?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "50") size: Int,
	): ListPaymentsResponse {
		val result =
			listPaymentsUseCase.execute(
				ListPaymentsCommand(
					merchantId = merchantId?.takeIf { it.isNotBlank() }?.let { MerchantId(it) },
					status = status?.takeIf { it.isNotBlank() }?.let { PaymentStatus.valueOf(it) },
					createdFrom = from,
					createdTo = to,
					page = page,
					size = size,
				),
			)

		return ListPaymentsResponse(
			payments =
				result.entries.map { entry ->
					PaymentSummaryResponse(
						paymentId = entry.paymentId.value,
						merchantId = entry.merchantId.value,
						merchantName = entry.merchantName,
						merchantOrderId = entry.merchantOrderId.value,
						orderName = entry.orderName,
						orderAmount = entry.orderAmount.amount,
						paymentAsset = entry.paymentAsset.code,
						paymentAmount = entry.paymentAmount.amountMinor.toString(),
						tokenDecimals = entry.tokenDecimals,
						network = entry.network.code,
						status = entry.status.name,
						failureReason = entry.failureReason?.name,
						transactionHash = entry.transactionHash?.value,
						paidAt = entry.paidAt,
						createdAt = entry.createdAt,
					)
				},
			totalCount = result.totalCount,
			page = result.page,
			size = result.size,
		)
	}
}
