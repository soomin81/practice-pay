package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.WalletAddress

/**
 * [CreatePaymentUseCase]의 입력이다. Command라 상태 변경 결과가 아니라 실행에
 * 필요한 값만 담는다(CQS).
 *
 * [network]와 [receivingWallet]을 호출부가 직접 넘기지만, **이건 임시 단순화이고
 * 닫아야 할 gap이다.** 수취 지갑은 PG가 수탁하는 지갑이라
 * (`docs/architecture/mvp-scope.md`의 "수취 지갑 귀속") 원래 호출부가 정할 값이 아니다 —
 * 가맹점이 자기 주소를 넣으면 USDC를 직접 받으면서 `SettlementReceivable`로 KRW까지
 * 받아 같은 대금이 두 번 나간다. 실제 자금이 오가기 전에 네트워크별 PG 지갑 설정에서
 * 주입하는 것으로 바꾸고 이 두 필드를 없앤다.
 *
 * @property merchantId 결제를 생성하는 가맹점.
 * @property merchantOrderId 가맹점이 부여한 주문 식별자. `(merchantId, merchantOrderId)`가 멱등성 키다.
 * @property orderName 주문명.
 * @property orderAmount KRW 주문 금액.
 * @property network 결제를 받을 블록체인 네트워크.
 * @property receivingWallet 결제를 받을 지갑 주소.
 * @property successUrl 결제 완료 후 Redirect할 URL.
 * @property cancelUrl 고객이 결제를 취소했을 때 Redirect할 URL. 선택값이다.
 */
data class CreatePaymentCommand(
	val merchantId: MerchantId,
	val merchantOrderId: MerchantOrderId,
	val orderName: String,
	val orderAmount: Money,
	val network: BlockchainNetwork,
	val receivingWallet: WalletAddress,
	val successUrl: HttpUrl,
	val cancelUrl: HttpUrl?,
)
