package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.checkout.CheckoutSessionId

/**
 * [SubmitPaymentTransactionUseCase]의 입력이다.
 *
 * 고객 지갑이 USDC 전송을 브로드캐스트한 뒤, 체크아웃 프런트엔드가 그 결과로 받은
 * Transaction Hash를 PG에 알려주는 시점의 입력이다 — `docs/domain/state-transitions.md`의
 * `CheckoutSession` 상태 중 `WALLET_CONNECTED → PAYMENT_SUBMITTED` 전이를 일으킨다.
 *
 * 고객 지갑 주소는 별도로 받지 않는다 — `CheckoutSession.connectWallet()`이 이미
 * 저장해 둔 `connectedWallet`을 그대로 쓴다(지갑 연결은 이 Use Case보다 먼저,
 * 별도 Use Case가 처리했다고 전제한다 — 범위 밖).
 */
data class SubmitPaymentTransactionCommand(
	val checkoutSessionId: CheckoutSessionId,
	val transactionHash: TransactionHash,
)
