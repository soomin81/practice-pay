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
 * [network]와 [receivingWallet]을 호출부가 직접 넘긴다 — 가맹점별 수취 지갑을
 * 어디서 설정·조회하는지는 `docs/`에 아직 정의돼 있지 않다(`payment.receiving_wallet_address`
 * 컬럼만 있고, 이 값의 출처가 되는 가맹점 지갑 설정 테이블이나 문서가 없다). 이
 * 슬라이스에서는 그 설정 메커니즘을 새로 만들지 않고 입력으로 받는 것으로
 * 단순화했다 — 나중에 가맹점 설정 조회 Port가 생기면 이 필드들을 그 조회 결과로
 * 대체할 수 있다.
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
