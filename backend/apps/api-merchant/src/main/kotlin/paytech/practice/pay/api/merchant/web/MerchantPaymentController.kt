package paytech.practice.pay.api.merchant.web

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.api.merchant.security.MerchantUserPrincipal
import paytech.practice.pay.application.payment.ExportMerchantPaymentsUseCase
import paytech.practice.pay.application.payment.GetMerchantPaymentDetailUseCase
import paytech.practice.pay.application.payment.ListMerchantPaymentsUseCase
import paytech.practice.pay.application.payment.ListPaymentsCommand
import paytech.practice.pay.application.payment.PaymentNotFoundException
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import java.time.Clock
import java.time.Instant

/**
 * 가맹점 콘솔의 **결제 내역 조회** API를 노출하는 inbound Adapter다
 * (`docs/architecture/merchant-console-api.md`).
 *
 * **`merchantId`는 요청 파라미터가 아니라 [MerchantUserPrincipal]에서 온다** — 요청으로
 * 받으면 호출자가 남의 가맹점 결제를 조회할 수 있다. `ListMerchantPaymentsUseCase`가
 * 이 값을 필수 인자로 받아 Command의 같은 필드를 덮어쓰므로, 여기서 실수로 흘려보내도
 * 범위가 넓어지지 않는다(이중 방어).
 *
 * **역할로 좁히지 않는다** — `SecurityConfig`가 `/merchant/payments`를 따로 잠그지 않아
 * `anyRequest authenticated`에 걸리고, `VIEWER`도 조회할 수 있다. API Key 관리
 * (`OWNER`/`ADMIN` 전용)와 달리 결제 내역은 조회 전용 역할이 봐야 하는 대표적인 자료다.
 */
@RestController
@RequestMapping("/merchant/payments")
class MerchantPaymentController(
	private val listMerchantPaymentsUseCase: ListMerchantPaymentsUseCase,
	private val exportMerchantPaymentsUseCase: ExportMerchantPaymentsUseCase,
	private val getMerchantPaymentDetailUseCase: GetMerchantPaymentDetailUseCase,
	private val clock: Clock,
) {
	/**
	 * 결제 한 건의 전체 맥락을 돌려준다.
	 *
	 * **없는 결제와 다른 가맹점의 결제가 똑같이 `404`다** — `403`으로 나누면 "그 결제는
	 * 존재한다"가 새어 나가고, 그것만으로 식별자를 훑어 다른 가맹점의 거래를 추정할 수 있다.
	 * 소유 확인은 `GetMerchantPaymentDetailUseCase`가 한다.
	 *
	 * **`/export`와 겹치지 않는다** — 리터럴 세그먼트가 경로 변수보다 우선한다(회귀 테스트로
	 * 고정했다).
	 */
	@GetMapping("/{paymentId}")
	fun getPaymentDetail(
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
		@PathVariable paymentId: String,
	): PaymentDetailResponse {
		val view =
			getMerchantPaymentDetailUseCase.execute(principal.merchantId, PaymentId(paymentId))
				?: throw PaymentNotFoundException(paymentId)

		return toResponse(view)
	}

	/**
	 * 현재 필터에 걸린 **자기 가맹점** 결제를 `.xlsx`로 내려준다. 조회와 같은 필터를 받되
	 * 페이징 파라미터는 받지 않는다(내보내기는 조건 전체가 대상이다).
	 *
	 * 조회와 마찬가지로 `merchantId`는 인증 주체에서 온다 — **내보내기에서는 이 구분이 더
	 * 중요하다**: 범위가 새면 남의 가맹점 결제가 파일로 통째로 빠져나간다.
	 */
	@GetMapping("/export")
	fun exportPayments(
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
		@RequestParam(required = false) status: String?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
	): ResponseEntity<ByteArray> {
		val result =
			exportMerchantPaymentsUseCase.execute(
				merchantId = principal.merchantId,
				command =
					ListPaymentsCommand(
						status = status?.takeIf { it.isNotBlank() }?.let { PaymentStatus.valueOf(it) },
						createdFrom = from,
						createdTo = to,
					),
			)

		return spreadsheetDownload(result, filePrefix = "payments", clock = clock)
	}

	@GetMapping
	fun listPayments(
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
		@RequestParam(required = false) status: String?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "50") size: Int,
	): ListPaymentsResponse {
		val result =
			listMerchantPaymentsUseCase.execute(
				merchantId = principal.merchantId,
				command =
					ListPaymentsCommand(
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
