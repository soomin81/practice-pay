package paytech.practice.pay.api.admin.web

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.application.settlement.ExportSettlementReceivablesUseCase
import paytech.practice.pay.application.settlement.ListSettlementReceivablesCommand
import paytech.practice.pay.application.settlement.ListSettlementReceivablesUseCase
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import java.time.Clock
import java.time.LocalDate

/**
 * 내부 운영자 콘솔의 **정산 채권 조회** API를 노출하는 inbound Adapter다
 * (`docs/architecture/admin-console-api.md`).
 *
 * **인증된 내부 사용자 전원이 조회할 수 있다**(`VIEWER` 포함) — `GET /admin/payments`와
 * 같은 스코핑이다.
 *
 * 기간은 **정산 예정일(`eligibleDate`) 기준의 날짜**다 — 결제 목록이 생성 시각(ISO-8601
 * 순간)을 쓰는 것과 다르다. 정산에서 묻는 질문이 "언제 정산되나"이기 때문이고, 날짜라
 * 시간대 경계 문제도 없다.
 */
@RestController
@RequestMapping("/admin/settlement-receivables")
class AdminSettlementReceivableController(
	private val listSettlementReceivablesUseCase: ListSettlementReceivablesUseCase,
	private val exportSettlementReceivablesUseCase: ExportSettlementReceivablesUseCase,
	private val clock: Clock,
) {
	/**
	 * 현재 필터에 걸린 정산 채권을 `.xlsx`로 내려준다. 조회와 **같은 필터**를 받되 페이징
	 * 파라미터는 받지 않는다 — 내보내기는 페이지 단위가 아니라 조건 전체가 대상이다
	 * (상한은 `SettlementExportPolicy.MAX_EXPORT_ROWS`, 넘치면 응답 헤더로 알린다).
	 *
	 * **`/export`가 목록 경로보다 먼저 선언될 필요는 없다** — 이 컨트롤러에는 `/{id}` 같은
	 * 경로 변수가 없어서 잡아먹힐 일이 없다(결제 쪽과 다른 점).
	 */
	@GetMapping("/export")
	fun exportSettlementReceivables(
		@RequestParam(required = false) merchantId: String?,
		@RequestParam(required = false) status: String?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) eligibleFrom: LocalDate?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) eligibleTo: LocalDate?,
	): ResponseEntity<ByteArray> {
		val result =
			exportSettlementReceivablesUseCase.execute(
				ListSettlementReceivablesCommand(
					merchantId = merchantId?.takeIf { it.isNotBlank() }?.let { MerchantId(it) },
					status = status?.takeIf { it.isNotBlank() }?.let { SettlementReceivableStatus.valueOf(it) },
					eligibleFrom = eligibleFrom,
					eligibleTo = eligibleTo,
				),
			)

		return spreadsheetDownload(result.spreadsheet, result.truncated, filePrefix = "settlements", clock = clock)
	}

	@GetMapping
	fun listSettlementReceivables(
		@RequestParam(required = false) merchantId: String?,
		@RequestParam(required = false) status: String?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) eligibleFrom: LocalDate?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) eligibleTo: LocalDate?,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "50") size: Int,
	): ListSettlementReceivablesResponse {
		val result =
			listSettlementReceivablesUseCase.execute(
				ListSettlementReceivablesCommand(
					merchantId = merchantId?.takeIf { it.isNotBlank() }?.let { MerchantId(it) },
					status = status?.takeIf { it.isNotBlank() }?.let { SettlementReceivableStatus.valueOf(it) },
					eligibleFrom = eligibleFrom,
					eligibleTo = eligibleTo,
					page = page,
					size = size,
				),
			)

		return ListSettlementReceivablesResponse(
			settlementReceivables =
				result.entries.map { entry ->
					SettlementReceivableSummaryResponse(
						settlementReceivableId = entry.settlementReceivableId.value,
						merchantId = entry.merchantId.value,
						merchantName = entry.merchantName,
						paymentId = entry.paymentId.value,
						merchantOrderId = entry.merchantOrderId.value,
						status = entry.status.name,
						settlementCurrency = entry.settlementCurrency,
						grossAmount = entry.grossAmount,
						feeRate = entry.feeRate,
						feeAmount = entry.feeAmount,
						adjustmentAmount = entry.adjustmentAmount,
						netAmount = entry.netAmount,
						exchangeReceivedAmount = entry.exchangeReceivedAmount,
						exchangeProfitLossAmount = entry.exchangeProfitLossAmount,
						eligibleDate = entry.eligibleDate,
						createdAt = entry.createdAt,
					)
				},
			totalCount = result.totalCount,
			totalNetAmount = result.totalNetAmount,
			page = result.page,
			size = result.size,
		)
	}
}
