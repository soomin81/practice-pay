package paytech.practice.pay.infra.blockchain.web3j

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.web3j.abi.EventEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Event
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.response.EthBlockNumber
import org.web3j.protocol.core.methods.response.EthChainId
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt
import org.web3j.protocol.core.methods.response.Log
import org.web3j.protocol.core.methods.response.TransactionReceipt
import paytech.practice.pay.application.port.outbound.BlockchainClientException
import paytech.practice.pay.domain.blockchain.ChainId
import paytech.practice.pay.domain.blockchain.ContractAddress
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import java.io.IOException
import java.math.BigInteger
import java.util.Optional

private val TRANSFER_EVENT =
	Event(
		"Transfer",
		listOf(
			object : TypeReference<Address>(true) {},
			object : TypeReference<Address>(true) {},
			object : TypeReference<Uint256>(false) {},
		),
	)

/** 프로덕션 코드와 별개로 같은 방식으로 topic0을 계산해, 어댑터가 실제로 그 값과 맞춰 필터링하는지 확인하는 독립적인 기준값이다. */
private val TRANSFER_TOPIC = EventEncoder.encode(TRANSFER_EVENT)

private val HASH = TransactionHash("0x" + "ab".repeat(32))
private val USDC_CONTRACT = ContractAddress("0x" + "cd".repeat(20))
private val FROM = WalletAddress("0x" + "11".repeat(20))
private val TO = WalletAddress("0x" + "22".repeat(20))

private fun topicFromAddress(wallet: WalletAddress): String = "0x" + wallet.value.removePrefix("0x").padStart(64, '0')

private fun dataFromAmount(amount: Long): String = "0x" + BigInteger.valueOf(amount).toString(16).padStart(64, '0')

private fun transferLog(
	contract: ContractAddress = USDC_CONTRACT,
	from: WalletAddress = FROM,
	to: WalletAddress = TO,
	amount: Long = 1_000_000,
): Log =
	mockk<Log> {
		every { address } returns contract.value
		every { topics } returns listOf(TRANSFER_TOPIC, topicFromAddress(from), topicFromAddress(to))
		every { data } returns dataFromAmount(amount)
	}

private fun mockReceipt(
	blockNumber: Long = 100,
	statusOk: Boolean = true,
	logs: List<Log> = listOf(transferLog()),
): TransactionReceipt =
	mockk<TransactionReceipt> {
		every { this@mockk.blockNumber } returns BigInteger.valueOf(blockNumber)
		every { isStatusOK } returns statusOk
		every { this@mockk.logs } returns logs
	}

private class FakeWeb3j(
	private val receiptResult: Optional<TransactionReceipt>,
	private val receiptError: Boolean = false,
	private val latestBlockNumber: Long = 105,
	private val chainIdValue: Long = 84532,
	private val receiptSendThrows: IOException? = null,
) {
	fun install(web3j: Web3j) {
		val receiptResponse =
			mockk<EthGetTransactionReceipt> {
				every { hasError() } returns receiptError
				every { error } returns mockk { every { message } returns "rpc error" }
				every { transactionReceipt } returns receiptResult
			}
		val receiptRequest = mockk<Request<String, EthGetTransactionReceipt>>()
		if (receiptSendThrows != null) {
			every { receiptRequest.send() } throws receiptSendThrows
		} else {
			every { receiptRequest.send() } returns receiptResponse
		}
		every { web3j.ethGetTransactionReceipt(HASH.value) } returns receiptRequest

		val blockNumberResponse =
			mockk<EthBlockNumber> {
				every { hasError() } returns false
				every { blockNumber } returns BigInteger.valueOf(latestBlockNumber)
			}
		val blockNumberRequest = mockk<Request<String, EthBlockNumber>>()
		every { blockNumberRequest.send() } returns blockNumberResponse
		every { web3j.ethBlockNumber() } returns blockNumberRequest

		val chainIdResponse =
			mockk<EthChainId> {
				every { hasError() } returns false
				every { this@mockk.chainId } returns BigInteger.valueOf(chainIdValue)
			}
		val chainIdRequest = mockk<Request<String, EthChainId>>()
		every { chainIdRequest.send() } returns chainIdResponse
		every { web3j.ethChainId() } returns chainIdRequest
	}
}

class Web3jBlockchainClientTest :
	FunSpec({

		test("returns a snapshot with decoded token transfers when the receipt is mined") {
			val web3j = mockk<Web3j>()
			FakeWeb3j(receiptResult = Optional.of(mockReceipt(blockNumber = 100)), latestBlockNumber = 105, chainIdValue = 84532)
				.install(web3j)

			val result = Web3jBlockchainClient(web3j).findTransaction(BlockchainNetwork.BASE_SEPOLIA, HASH)

			result.shouldNotBeNull()
			result.transactionHash shouldBe HASH
			result.chainId shouldBe ChainId(84532)
			result.blockNumber shouldBe 100L
			result.receiptSucceeded shouldBe true
			result.confirmationCount shouldBe 6 // 105 - 100 + 1
			result.tokenTransfers shouldBe
				listOf(
					paytech.practice.pay.application.port.outbound.OnChainTokenTransfer(
						contractAddress = USDC_CONTRACT,
						from = FROM,
						to = TO,
						amount = TokenAmount(1_000_000),
					),
				)
		}

		test("a reverted receipt is reported as receiptSucceeded = false") {
			val web3j = mockk<Web3j>()
			FakeWeb3j(receiptResult = Optional.of(mockReceipt(statusOk = false))).install(web3j)

			val result = Web3jBlockchainClient(web3j).findTransaction(BlockchainNetwork.BASE_SEPOLIA, HASH)

			result.shouldNotBeNull()
			result.receiptSucceeded shouldBe false
		}

		test("a Transfer log whose amount overflows Long is skipped instead of failing the whole lookup") {
			// 실제 Base Sepolia 온체인 데이터로 재현한 경우다 — 18-decimals 토큰 전송량은
			// 흔히 Long.MAX_VALUE를 넘는다(라이브 스모크 테스트로 처음 발견됨).
			val web3j = mockk<Web3j>()
			val hugeAmountLog = transferLog(amount = Long.MAX_VALUE)
			val hugeAmountData =
				"0x" +
					BigInteger
						.valueOf(Long.MAX_VALUE)
						.add(BigInteger.ONE)
						.toString(16)
						.padStart(64, '0')
			every { hugeAmountLog.data } returns hugeAmountData
			FakeWeb3j(receiptResult = Optional.of(mockReceipt(logs = listOf(hugeAmountLog, transferLog(amount = 1_000_000)))))
				.install(web3j)

			val result = Web3jBlockchainClient(web3j).findTransaction(BlockchainNetwork.BASE_SEPOLIA, HASH)

			result.shouldNotBeNull()
			result.tokenTransfers shouldBe
				listOf(
					paytech.practice.pay.application.port.outbound.OnChainTokenTransfer(
						contractAddress = USDC_CONTRACT,
						from = FROM,
						to = TO,
						amount = TokenAmount(1_000_000),
					),
				)
		}

		test("logs that are not the ERC-20 Transfer event are ignored") {
			val web3j = mockk<Web3j>()
			val unrelatedLog =
				mockk<Log> {
					every { address } returns USDC_CONTRACT.value
					every { topics } returns listOf("0x" + "00".repeat(32))
					every { data } returns dataFromAmount(1)
				}
			FakeWeb3j(receiptResult = Optional.of(mockReceipt(logs = listOf(unrelatedLog)))).install(web3j)

			val result = Web3jBlockchainClient(web3j).findTransaction(BlockchainNetwork.BASE_SEPOLIA, HASH)

			result.shouldNotBeNull()
			result.tokenTransfers shouldBe emptyList()
		}

		test("returns null when the transaction is not yet mined") {
			val web3j = mockk<Web3j>()
			FakeWeb3j(receiptResult = Optional.empty()).install(web3j)

			Web3jBlockchainClient(web3j).findTransaction(BlockchainNetwork.BASE_SEPOLIA, HASH).shouldBeNull()
		}

		test("an IOException from the RPC call is wrapped in BlockchainClientException") {
			val web3j = mockk<Web3j>()
			FakeWeb3j(receiptResult = Optional.empty(), receiptSendThrows = IOException("connection refused")).install(web3j)

			shouldThrow<BlockchainClientException> {
				Web3jBlockchainClient(web3j).findTransaction(BlockchainNetwork.BASE_SEPOLIA, HASH)
			}
		}

		test("a JSON-RPC error response is wrapped in BlockchainClientException") {
			val web3j = mockk<Web3j>()
			FakeWeb3j(receiptResult = Optional.empty(), receiptError = true).install(web3j)

			shouldThrow<BlockchainClientException> {
				Web3jBlockchainClient(web3j).findTransaction(BlockchainNetwork.BASE_SEPOLIA, HASH)
			}
		}

		test("an unsupported network throws IllegalArgumentException") {
			val web3j = mockk<Web3j>()

			shouldThrow<IllegalArgumentException> {
				Web3jBlockchainClient(web3j).findTransaction(BlockchainNetwork("ETHEREUM_MAINNET"), HASH)
			}
		}
	})
