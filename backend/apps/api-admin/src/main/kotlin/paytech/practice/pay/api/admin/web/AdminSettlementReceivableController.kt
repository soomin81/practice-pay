package paytech.practice.pay.api.admin.web

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.api.admin.security.InternalUserPrincipal
import paytech.practice.pay.application.settlement.CancelSettlementReceivableCommand
import paytech.practice.pay.application.settlement.CancelSettlementReceivableUseCase
import paytech.practice.pay.application.settlement.ExportSettlementReceivablesUseCase
import paytech.practice.pay.application.settlement.ListSettlementHoldHistoryUseCase
import paytech.practice.pay.application.settlement.ListSettlementReceivablesCommand
import paytech.practice.pay.application.settlement.ListSettlementReceivablesUseCase
import paytech.practice.pay.application.settlement.ReleaseSettlementHoldCommand
import paytech.practice.pay.application.settlement.ReleaseSettlementHoldUseCase
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import java.time.Clock
import java.time.LocalDate

/**
 * 내부 운영자 콘솔의 **정산 채권 조회와 보류 해제·취소** API를 노출하는 inbound Adapter다
 * (`docs/architecture/admin-console-api.md`).
 *
 * **조회는 인증된 내부 사용자 전원이 할 수 있고**(`VIEWER` 포함 — `GET /admin/payments`와 같은
 * 스코핑) **상태를 바꾸는 두 동작은 `SUPER_ADMIN` 전용이다.** 막는 쪽(`mark-reorged`)과 같은
 * 등급이어야 한다 — 푸는 쪽만 넓히면 좁게 잡은 의미가 없어진다. 인가는 `SecurityConfig`의
 * `HttpMethod.POST` 스코핑이 지므로 여기서 다시 확인하지 않는다.
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
	private val releaseSettlementHoldUseCase: ReleaseSettlementHoldUseCase,
	private val cancelSettlementReceivableUseCase: CancelSettlementReceivableUseCase,
	private val listSettlementHoldHistoryUseCase: ListSettlementHoldHistoryUseCase,
	private val clock: Clock,
) {
	/**
	 * 현재 필터에 걸린 정산 채권을 `.xlsx`로 내려준다. 조회와 **같은 필터**를 받되 페이징
	 * 파라미터는 받지 않는다 — 내보내기는 페이지 단위가 아니라 조건 전체가 대상이다
	 * (상한은 `SettlementExportPolicy.MAX_EXPORT_ROWS`, 넘치면 응답 헤더로 알린다).
	 *
	 * **`/export`가 목록 경로보다 먼저 선언될 필요는 없다** — 아래에 경로 변수를 쓰는 매핑이
	 * 생겼지만 전부 두 세그먼트(`/{id}/release` 등)라 한 세그먼트인 이 경로를 잡아먹지 않는다.
	 * 나중에 `GET /{settlementReceivableId}` 같은 **한 세그먼트 경로 변수**가 생기면 그때는
	 * 순서가 문제가 된다(결제 쪽이 그런 경우다).
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

	/**
	 * 보류를 푼다. **돌아갈 상태는 요청이 정하지 않는다** — 서버가 `exchangeOrderId` 유무로
	 * `READY`/`PENDING`을 고르고 응답으로 알려준다(화면이 정하게 두면 매도가 끝나지 않은
	 * 채권이 `READY`가 되어 근거 없는 정산 금액이 생긴다).
	 *
	 * `note`는 필수다 — 자동 경로가 없는 전이라 실행한 사람 말고는 이유를 아는 곳이 없다.
	 */
	@PostMapping("/{settlementReceivableId}/release")
	fun releaseSettlementHold(
		@PathVariable settlementReceivableId: String,
		@RequestBody request: SettlementHoldActionRequest,
		@AuthenticationPrincipal principal: InternalUserPrincipal,
	): SettlementHoldActionResponse {
		val result =
			releaseSettlementHoldUseCase.execute(
				ReleaseSettlementHoldCommand(
					settlementReceivableId = SettlementReceivableId(settlementReceivableId),
					actorInternalUserId = principal.internalUserId,
					note = request.note,
				),
			)

		return SettlementHoldActionResponse(
			settlementReceivableId = result.settlementReceivableId.value,
			status = result.status.name,
		)
	}

	/** 그 돈을 정산하지 않기로 확정한다. `CANCELLED`는 종료 상태라 되돌릴 수 없다. */
	@PostMapping("/{settlementReceivableId}/cancel")
	fun cancelSettlementReceivable(
		@PathVariable settlementReceivableId: String,
		@RequestBody request: SettlementHoldActionRequest,
		@AuthenticationPrincipal principal: InternalUserPrincipal,
	): SettlementHoldActionResponse {
		val result =
			cancelSettlementReceivableUseCase.execute(
				CancelSettlementReceivableCommand(
					settlementReceivableId = SettlementReceivableId(settlementReceivableId),
					actorInternalUserId = principal.internalUserId,
					note = request.note,
				),
			)

		return SettlementHoldActionResponse(
			settlementReceivableId = result.settlementReceivableId.value,
			status = result.status.name,
		)
	}

	/**
	 * 채권 한 건의 보류·해제·취소 이력을 최신순으로 준다.
	 *
	 * **`VIEWER`도 읽을 수 있다** — 이력을 읽는 것과 상태를 바꾸는 것은 다른 권한이다
	 * (`SecurityConfig`가 `POST`만 좁힌 이유).
	 */
	@GetMapping("/{settlementReceivableId}/hold-history")
	fun listSettlementHoldHistory(
		@PathVariable settlementReceivableId: String,
	): ListSettlementHoldHistoryResponse {
		val result = listSettlementHoldHistoryUseCase.execute(SettlementReceivableId(settlementReceivableId))

		return ListSettlementHoldHistoryResponse(
			history =
				result.entries.map { entry ->
					SettlementHoldAuditResponse(
						auditId = entry.auditId.value,
						internalUserId = entry.internalUserId.value,
						internalUserName = entry.internalUserName,
						action = entry.action.name,
						reasonCode = entry.reasonCode,
						note = entry.note,
						occurredAt = entry.occurredAt,
					)
				},
		)
	}
}
