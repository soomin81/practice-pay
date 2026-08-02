package paytech.practice.pay.api.admin.web

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.api.admin.security.InternalUserPrincipal
import paytech.practice.pay.application.payment.MarkTransactionReorgedCommand
import paytech.practice.pay.application.payment.MarkTransactionReorgedUseCase
import paytech.practice.pay.domain.blockchain.BlockchainTransactionId

/**
 * 확정된 입금이 **체인 재구성으로 사라졌다**고 표시하는 API를 노출하는 inbound Adapter다.
 *
 * **`SUPER_ADMIN`만 실행한다** — 이 콘솔의 다른 운영 동작(가맹점 계정 관리, Webhook 재전송)이
 * `SUPER_ADMIN`/`OPERATOR`인 것보다 좁다. 되돌릴 수 없고 **가맹점에게 지급될 돈을 직접
 * 막는** 동작이라, 잘못 눌렀을 때 가맹점이 받을 정산이 멈춘다.
 *
 * 무엇이 바뀌고 무엇이 그대로인지는 `MarkTransactionReorgedUseCase`의 KDoc에 있다 —
 * 요약하면 **되돌리지 않고 정산만 막는다**(`docs/decisions/ADR-007-onchain-irreversibility.md`).
 */
@RestController
@RequestMapping("/admin/blockchain-transactions")
class BlockchainTransactionController(
	private val markTransactionReorgedUseCase: MarkTransactionReorgedUseCase,
) {
	@PostMapping("/{blockchainTransactionId}/mark-reorged")
	fun markReorged(
		@PathVariable blockchainTransactionId: String,
		@AuthenticationPrincipal principal: InternalUserPrincipal,
	): MarkTransactionReorgedResponse {
		// 실행자는 요청이 아니라 인증 주체에서 온다 — 이 값이 settlement_hold_audit에 남는다.
		val result =
			markTransactionReorgedUseCase.execute(
				MarkTransactionReorgedCommand(
					blockchainTransactionId = BlockchainTransactionId(blockchainTransactionId),
					actorInternalUserId = principal.internalUserId,
				),
			)

		return MarkTransactionReorgedResponse(
			blockchainTransactionId = result.blockchainTransactionId.value,
			paymentId = result.paymentId.value,
			settlementHeld = result.settlementHeld,
		)
	}
}

/**
 * 확정 이후 reorg 표시 결과다.
 *
 * @property settlementHeld 딸린 정산 채권을 실제로 막았는지. **`false`면 아직 채권이 없다는
 * 뜻이고, 그건 매도 Worker가 이 결제를 집어 채권을 만들 수 있다는 뜻이라 더 위험하다** —
 * 화면은 이 값이 `false`일 때 반드시 경고해야 한다.
 */
data class MarkTransactionReorgedResponse(
	val blockchainTransactionId: String,
	val paymentId: String,
	val settlementHeld: Boolean,
)
