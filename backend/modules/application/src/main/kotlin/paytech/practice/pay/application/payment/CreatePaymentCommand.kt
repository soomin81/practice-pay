package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.shared.Money

/**
 * [CreatePaymentUseCase]의 입력이다. Command라 상태 변경 결과가 아니라 실행에
 * 필요한 값만 담는다(CQS).
 *
 * **수취 지갑은 여기 없다** — [network]에 대응하는 PG 수취 지갑을
 * [ReceivingWalletRegistry]가 정한다(`docs/architecture/mvp-scope.md`의 "수취 지갑 귀속").
 * 반면 [network]는 남는다: 어느 체인으로 받을지는 가맹점의 정당한 선택이고, 수탁
 * 문제와 무관하다.
 *
 * @property merchantId 결제를 생성하는 가맹점.
 * @property merchantOrderId 가맹점이 부여한 주문 식별자. `(merchantId, merchantOrderId)`가 멱등성 키다.
 * @property orderName 주문명.
 * @property orderAmount KRW 주문 금액.
 * @property network 결제를 받을 블록체인 네트워크.
 * @property successUrl 결제 완료 후 Redirect할 URL.
 * @property cancelUrl 고객이 결제를 취소했을 때 Redirect할 URL. 선택값이다.
 */
data class CreatePaymentCommand(
	val merchantId: MerchantId,
	val merchantOrderId: MerchantOrderId,
	val orderName: String,
	val orderAmount: Money,
	val network: BlockchainNetwork,
	val successUrl: HttpUrl,
	val cancelUrl: HttpUrl?,
)
