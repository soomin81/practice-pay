package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.blockchain.ChainId
import paytech.practice.pay.domain.blockchain.ContractAddress
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress

/**
 * 특정 온체인 거래(Transaction Hash)의 현재 상태를 조회하는 Outbound Port다.
 * `modules:infra-blockchain`이 실제 RPC 클라이언트(예: web3j)로 구현할 자리다
 * (`modules:infra-persistence`가 [PaymentRepository] 등을 jOOQ로 구현하는 것과
 * 같은 자리).
 *
 * **"조회 대상은 항상 이미 알고 있는 Transaction Hash다."** 임의의 들어오는 전송을
 * 감시(Watch)하는 Port가 아니다 — [paytech.practice.pay.domain.blockchain.BlockchainTransaction.create]가
 * `transactionHash`를 필수 파라미터로 요구하고 최초 상태가 `SUBMITTED`인 것 자체가,
 * `BlockchainTransaction`이 생성되는 시점에는 이미 어떤 Hash를 봐야 하는지 알고
 * 있다는 뜻이다(체크아웃에서 고객 지갑이 전송을 브로드캐스트한 뒤 그 Hash를
 * `CheckoutSession`의 `PAYMENT_SUBMITTED` 단계로 PG에 알리는 흐름을 전제한다 —
 * `docs/domain/state-transitions.md`의 `CheckoutSession` 상태, 루트 `CLAUDE.md`의
 * 전체 흐름 "고객 지갑 연결 → USDC 전송 → BlockchainTransaction 감지 및 Confirm"
 * 참고). 그래서 이 Port는 "이 Hash 지금 상태가 뭐야?"를 반복해서 묻는 모양이고,
 * `docs/database/database-design.md`의 "Confirm Worker: `transaction_status +
 * updated_at`" 인덱스가 암시하는 폴링(Polling) 방식의 확인 배치와 맞물려 동작할
 * 것을 전제로 설계했다 — Worker가 `CONFIRMING` 상태인 `BlockchainTransaction`을
 * 주기적으로 훑으면서 [findTransaction]을 다시 호출해 `confirmationCount`를
 * 갱신하고, 임계치에 도달하면 `confirm()`을 호출하는 그림이다.
 *
 * 이 Port가 돌려주는 값은 순수한 온체인 사실(Receipt 성공 여부, Confirm 수, 검출된
 * 토큰 전송 내역)뿐이다 — 그 값이 기대한 Network/Chain ID/Contract/수취 지갑/금액과
 * 실제로 일치하는지 판단하는 건 이 Port나 어댑터가 아니라
 * `PaymentTransactionValidator` 같은 도메인 서비스의 책임이다
 * (`docs/domain/domain-model.md`의 "Domain Service" 절, `docs/domain/state-transitions.md`의
 * `CONFIRMING → SUCCEEDED` 조건 목록과 그대로 대응한다). 이 경계를 지키려고
 * [OnChainTransaction.tokenTransfers]를 하나로 좁히지 않고 리스트로 둔다 — 한
 * Receipt 안에 ERC-20 `Transfer` 이벤트가 여럿 있을 수 있고(중개 Contract를 거치는
 * 경우 등), "그중 어떤 게 우리가 찾는 전송인지" 고르는 것 자체가 검증 로직이라
 * 어댑터가 미리 판단해버리면 안 된다.
 */
fun interface BlockchainClient {
	/**
	 * [network]에서 [transactionHash]에 해당하는 거래의 현재 상태를 조회한다.
	 *
	 * 아직 블록에 포함되지 않았으면(Mempool에 있거나, 아직 노드에 전파되지 않았거나,
	 * 애초에 존재하지 않으면) `null`이다 — 이 경우 호출부는 `BlockchainTransaction`을
	 * 여전히 `SUBMITTED`로 두고 다음 폴링을 기다린다. RPC 호출 자체가 실패하면(노드
	 * 응답 없음, 타임아웃 등) `null`이 아니라 [BlockchainClientException]을 던진다 —
	 * "거래가 아직 없다"와 "지금 조회에 실패했다"는 호출부의 재시도 판단이 달라야
	 * 하는 서로 다른 상황이라 구분한다.
	 */
	fun findTransaction(
		network: BlockchainNetwork,
		transactionHash: TransactionHash,
	): OnChainTransaction?
}

/**
 * [BlockchainClient.findTransaction]이 돌려주는, 어떤 시점의 온체인 거래 상태
 * 스냅샷이다. `CONFIRMING` 동안 반복 조회되므로 [confirmationCount]는 호출마다
 * 달라질 수 있다.
 *
 * @property transactionHash 조회에 쓴 Hash를 그대로 반환한다(호출부가 결과를
 * 원래 요청과 대조하기 쉽도록).
 * @property chainId 노드가 실제로 응답한 Chain ID. `Payment → SUCCEEDED`의 "Network
 * 및 Chain ID 일치" 조건을 검증할 때 기대값과 비교한다 — RPC 엔드포인트 설정이
 * 잘못된 경우를 방어하기 위한 값이라, [BlockchainNetwork]로부터 유추하지 않고
 * 매 조회마다 노드로부터 직접 받는다.
 * @property blockNumber 이 거래가 포함된 블록 번호.
 * @property receiptSucceeded Receipt의 성공 여부(EVM의 `status` 필드) — 되돌려진
 * (Reverted) 거래는 `false`다. `false`면 호출부는 `BlockchainTransaction.confirm()`이
 * 아니라 `.fail()`로 보내야 한다.
 * @property confirmationCount 조회 시점 기준 누적 Confirm 수(현재 블록 높이 -
 * [blockNumber] + 1). 호출부가 별도로 최신 블록 번호를 조회해 계산할 필요가
 * 없도록 어댑터가 미리 계산해서 담아준다.
 * @property tokenTransfers 이 Receipt의 로그에서 디코딩한 ERC-20 `Transfer`
 * 이벤트 전부. 어떤 항목이 "우리가 기대하는 전송"인지 고르는 건 이 Port가 아니라
 * 호출부(도메인 서비스)의 책임이다 — 클래스 KDoc 참고.
 */
data class OnChainTransaction(
	val transactionHash: TransactionHash,
	val chainId: ChainId,
	val blockNumber: Long,
	val receiptSucceeded: Boolean,
	val confirmationCount: Int,
	val tokenTransfers: List<OnChainTokenTransfer>,
)

/**
 * [OnChainTransaction]의 Receipt 로그에서 디코딩한 ERC-20 `Transfer` 이벤트 하나다.
 *
 * @property contractAddress 이 전송을 발생시킨 토큰 Contract. "Token Symbol만으로
 * 자산을 판단하지 않는다"는 규칙에 따라 이 값과 [BlockchainNetwork]의 조합으로만
 * "이게 USDC인지"를 판단한다(`docs/domain/glossary.md`).
 * @property from 보낸 지갑 주소.
 * @property to 받은 지갑 주소. `Payment.receivingWallet`과 일치하는지 호출부가 검증한다.
 * @property amount 전송된 토큰 수량(Minor Unit). `Payment.paymentAmount`를 충족하는지 호출부가 검증한다.
 */
data class OnChainTokenTransfer(
	val contractAddress: ContractAddress,
	val from: WalletAddress,
	val to: WalletAddress,
	val amount: TokenAmount,
)

/**
 * [BlockchainClient]가 RPC 호출 자체에 실패했을 때(노드 응답 없음, 타임아웃,
 * 일시적 네트워크 오류 등) 던지는 예외다. "거래를 아직 못 찾음"(`null` 반환)과
 * 구분되는, 재시도가 의미 있는 일시적 실패를 표현한다.
 */
class BlockchainClientException(
	message: String,
	cause: Throwable? = null,
) : RuntimeException(message, cause)
