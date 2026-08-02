package paytech.practice.pay.api.merchant.web

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.api.merchant.security.MerchantUserPrincipal
import paytech.practice.pay.application.settlement.ExportMerchantSettlementReceivablesUseCase
import paytech.practice.pay.application.settlement.ListMerchantSettlementReceivablesUseCase
import paytech.practice.pay.application.settlement.ListSettlementReceivablesCommand
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import java.time.Clock
import java.time.LocalDate

/**
 * 가맹점 콘솔의 **정산 채권 조회** API를 노출하는 inbound Adapter다
 * (`docs/architecture/merchant-console-api.md`).
 *
 * **`merchantId`는 요청 파라미터가 아니라 [MerchantUserPrincipal]에서 온다.** 정산은 결제보다
 * 민감도가 한 단계 높다 — 범위가 새면 남의 **매출과 수취 예정 금액**이 드러난다.
 * `ListMerchantSettlementReceivablesUseCase`가 이 값을 필수 인자로 받아 Command의 같은 필드를
 * 덮어쓰므로 이중 방어가 된다.
 *
 * **역할로 좁히지 않는다** — `VIEWER`도 조회할 수 있다(결제 내역과 같은 판단).
 */
@RestController
@RequestMapping("/merchant/settlement-receivables")
class MerchantSettlementReceivableController(
	private val listMerchantSettlementReceivablesUseCase: ListMerchantSettlementReceivablesUseCase,
	private val exportMerchantSettlementReceivablesUseCase: ExportMerchantSettlementReceivablesUseCase,
	private val clock: Clock,
) {
	/**
	 * 현재 필터에 걸린 **자기 가맹점** 정산 채권을 `.xlsx`로 내려준다. 페이징 파라미터는
	 * 받지 않는다 — 내보내기는 조건 전체가 대상이다(서버가 최대
	 * `SettlementExportPolicy.MAX_EXPORT_ROWS`에서 자르고, 잘렸으면 응답 헤더로 알린다).
	 *
	 * 조회와 마찬가지로 **`merchantId`는 인증 주체에서만 온다** — 파일로 빠져나가는
	 * 산출물이라 범위가 새면 되돌릴 수 없다.
	 */
	@GetMapping("/export")
	fun exportSettlementReceivables(
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
		@RequestParam(required = false) status: String?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) eligibleFrom: LocalDate?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) eligibleTo: LocalDate?,
	): ResponseEntity<ByteArray> {
		val result =
			exportMerchantSettlementReceivablesUseCase.execute(
				merchantId = principal.merchantId,
				command =
					ListSettlementReceivablesCommand(
						status = status?.takeIf { it.isNotBlank() }?.let { SettlementReceivableStatus.valueOf(it) },
						eligibleFrom = eligibleFrom,
						eligibleTo = eligibleTo,
					),
			)

		return spreadsheetDownload(result.spreadsheet, result.truncated, filePrefix = "settlements", clock = clock)
	}

	@GetMapping
	fun listSettlementReceivables(
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
		@RequestParam(required = false) status: String?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) eligibleFrom: LocalDate?,
		@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) eligibleTo: LocalDate?,
		@RequestParam(defaultValue = "0") page: Int,
		@RequestParam(defaultValue = "50") size: Int,
	): ListSettlementReceivablesResponse {
		val result =
			listMerchantSettlementReceivablesUseCase.execute(
				merchantId = principal.merchantId,
				command =
					ListSettlementReceivablesCommand(
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
						holdReasonCode = entry.holdReasonCode,
						createdAt = entry.createdAt,
					)
				},
			totalCount = result.totalCount,
			totalNetAmount = result.totalNetAmount,
			heldCount = result.heldCount,
			heldNetAmount = result.heldNetAmount,
			page = result.page,
			size = result.size,
		)
	}
}
