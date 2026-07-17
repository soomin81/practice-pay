package paytech.practice.pay.infra.blockchain.web3j

import org.springframework.stereotype.Component
import org.web3j.abi.EventEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.response.EthBlockNumber
import org.web3j.protocol.core.methods.response.EthChainId
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt
import org.web3j.protocol.core.methods.response.Log
import org.web3j.protocol.core.methods.response.TransactionReceipt
import paytech.practice.pay.application.port.outbound.BlockchainClient
import paytech.practice.pay.application.port.outbound.BlockchainClientException
import paytech.practice.pay.application.port.outbound.OnChainTokenTransfer
import paytech.practice.pay.application.port.outbound.OnChainTransaction
import paytech.practice.pay.domain.blockchain.ChainId
import paytech.practice.pay.domain.blockchain.ContractAddress
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import java.io.IOException
import java.math.BigInteger

/**
 * web3j로 [BlockchainClient] Port를 구현한다. MVP는 [BlockchainNetwork.BASE_SEPOLIA] 하나만
 * 지원한다 — 다른 네트워크가 오면 [IllegalArgumentException]을 던진다(`Web3jConfiguration`이
 * 지금 그 네트워크 하나의 [Web3j] Bean만 만든다).
 *
 * [findTransaction] 한 번 호출에서 JSON-RPC를 세 번 부른다(`eth_getTransactionReceipt`,
 * `eth_blockNumber`, `eth_chainId`) — Receipt 조회 자체는 하나로 되지만, Confirm 수 계산에
 * 필요한 최신 블록 번호와 방어적으로 확인할 Chain ID는 Receipt에 없어서 따로 받는다.
 * 이 세 호출을 하나의 배치 요청으로 묶지 않는 건 MVP 단순화다 — 폴링 주기가
 * 촘촘해지면(Confirm Worker) 그때 배치 호출로 바꾼다.
 */
@Component
class Web3jBlockchainClient(
	private val web3j: Web3j,
) : BlockchainClient {
	override fun findTransaction(
		network: BlockchainNetwork,
		transactionHash: TransactionHash,
	): OnChainTransaction? {
		require(network == BlockchainNetwork.BASE_SEPOLIA) {
			"Web3jBlockchainClient는 아직 BASE_SEPOLIA만 지원합니다: $network"
		}

		val receipt = fetchReceipt(transactionHash) ?: return null
		val latestBlockNumber = send(web3j.ethBlockNumber(), EthBlockNumber::getBlockNumber, "eth_blockNumber")
		val chainId = send(web3j.ethChainId(), EthChainId::getChainId, "eth_chainId")

		return OnChainTransaction(
			transactionHash = transactionHash,
			chainId = ChainId(chainId.toLong()),
			blockNumber = receipt.blockNumber.toLong(),
			receiptSucceeded = receipt.isStatusOK,
			confirmationCount = confirmationCount(latestBlockNumber, receipt.blockNumber),
			tokenTransfers = receipt.logs.mapNotNull { it.toTokenTransferOrNull() },
		)
	}

	private fun fetchReceipt(transactionHash: TransactionHash): TransactionReceipt? {
		val result =
			send(
				web3j.ethGetTransactionReceipt(transactionHash.value),
				EthGetTransactionReceipt::getTransactionReceipt,
				"eth_getTransactionReceipt",
			)
		return if (result.isPresent) result.get() else null
	}

	private fun confirmationCount(
		latestBlockNumber: BigInteger,
		transactionBlockNumber: BigInteger,
	): Int {
		val confirmations = latestBlockNumber.subtract(transactionBlockNumber).add(BigInteger.ONE)
		return confirmations.coerceAtLeast(BigInteger.ZERO).toInt()
	}

	/**
	 * Receipt의 로그 하나를 ERC-20 `Transfer`로 디코딩한다. Topic 모양이 안 맞거나
	 * (`Transfer`가 아니거나) 전송량이 [TokenAmount]가 표현할 수 있는 범위(Long)를
	 * 넘으면 `null`로 걸러낸다 — 뒷 경우는 실제로 겪는다: 18-decimals 토큰(대부분의
	 * ERC-20)의 전송량은 흔히 `Long.MAX_VALUE`를 넘고, `BigInteger.toLong()`은
	 * 예외 없이 조용히 하위 64비트로 잘라버려서(음수로 뒤집힐 수도 있다) 그대로
	 * 쓰면 안 된다. 같은 Receipt 안에 우리가 찾는 6-decimals USDC 전송이 함께 있을
	 * 수 있으니, 표현 못 하는 로그 하나 때문에 [findTransaction] 전체를 실패시키지
	 * 않고 그 로그만 조용히 건너뛴다.
	 */
	private fun Log.toTokenTransferOrNull(): OnChainTokenTransfer? {
		if (topics.size != TRANSFER_EVENT_TOPIC_COUNT || topics[0] != TRANSFER_EVENT_TOPIC) return null
		val amount = BigInteger(data.removePrefix("0x"), 16)
		if (amount < BigInteger.ZERO || amount > MAX_TOKEN_AMOUNT) return null
		return OnChainTokenTransfer(
			contractAddress = ContractAddress(address),
			from = WalletAddress(addressFromTopic(topics[1])),
			to = WalletAddress(addressFromTopic(topics[2])),
			amount = TokenAmount(amount.toLong()),
		)
	}

	/** 32바이트로 좌측 0-padding된 Topic에서 마지막 20바이트(40자리 16진수)만 주소로 뽑는다. */
	private fun addressFromTopic(topic: String): String = "0x" + topic.removePrefix("0x").takeLast(40)

	/**
	 * web3j `Request.send()`는 네트워크/노드 오류를 [IOException]으로 던지거나, 오류
	 * 없이 응답했지만 JSON-RPC 오류 필드가 채워진 상태로 돌아올 수 있다(`Response.hasError()`) —
	 * 두 경우 모두 [BlockchainClientException]으로 통일한다.
	 */
	private fun <T : org.web3j.protocol.core.Response<*>, R> send(
		request: org.web3j.protocol.core.Request<*, T>,
		extract: (T) -> R,
		rpcMethod: String,
	): R {
		val response =
			try {
				request.send()
			} catch (ex: IOException) {
				throw BlockchainClientException("$rpcMethod 호출에 실패했습니다.", ex)
			}
		if (response.hasError()) {
			throw BlockchainClientException("$rpcMethod 호출이 오류를 반환했습니다: ${response.error.message}")
		}
		return extract(response)
	}

	companion object {
		private val TRANSFER_EVENT =
			Event(
				"Transfer",
				listOf(
					object : TypeReference<Address>(true) {},
					object : TypeReference<Address>(true) {},
					object : TypeReference<Uint256>(false) {},
				),
			)
		private val TRANSFER_EVENT_TOPIC = EventEncoder.encode(TRANSFER_EVENT)
		private const val TRANSFER_EVENT_TOPIC_COUNT = 3
		private val MAX_TOKEN_AMOUNT: BigInteger = BigInteger.valueOf(Long.MAX_VALUE)
	}
}
