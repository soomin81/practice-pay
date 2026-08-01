package paytech.practice.pay.api.payment.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import paytech.practice.pay.application.port.outbound.WalletAddressChecksum
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.WalletAddress

/** 체크섬 판정만 흉내 낸다 — 실제 EIP-55 계산은 `Web3jWalletAddressChecksumTest`가 검증한다. */
private val CANONICAL = "0x036CbD53842c5426634e7929541eC2318f3dCF7e"

private val fakeChecksum = WalletAddressChecksum { CANONICAL }

/**
 * 수취 지갑 설정 검증은 **기동 시점에** 일어나야 한다 — 잘못된 주소로 떠 있으면 첫 결제가
 * 아무도 통제하지 못하는 주소로 흘러가고 되돌릴 수 없다.
 */
class ReceivingWalletConfigurationTest :
	FunSpec({
		val configuration = UseCaseConfiguration()

		test("registers the wallet when the configured address is checksummed") {
			val registry = configuration.receivingWalletRegistry(fakeChecksum, CANONICAL)

			registry.walletFor(BlockchainNetwork.BASE_SEPOLIA) shouldBe WalletAddress(CANONICAL)
		}

		/**
		 * **이 슬라이스의 핵심이다** — 형식은 맞지만 체크섬이 어긋나는 주소(= 오타)로는
		 * 컨텍스트가 뜨지 않아야 한다.
		 */
		test("fails startup when the configured address is not checksummed") {
			val typo = "0x036CbD53842c5426634e7929541eC2318f3dCF7f"

			val error = shouldThrow<IllegalStateException> { configuration.receivingWalletRegistry(fakeChecksum, typo) }

			error.message!! shouldContain "EIP-55"
			error.message!! shouldContain "다시 복사"
		}

		/**
		 * **정규 형태를 메시지에 찍지 않는다.** 찍으면 운영자가 그 값을 그대로 복사해 넣는데,
		 * 오타였다면 "체크섬만 맞는 남의 주소"라 오타를 확정시킨다.
		 */
		test("does not leak the corrected address in the error message") {
			val typo = "0x036CbD53842c5426634e7929541eC2318f3dCF7f"

			val error = shouldThrow<IllegalStateException> { configuration.receivingWalletRegistry(fakeChecksum, typo) }

			error.message!! shouldNotContain CANONICAL
		}

		// 설정이 비어 있으면 기동은 정상이다 — 실패는 결제를 만들 때 503으로 드러난다
		// ("환경변수 없이도 bootRun이 동작한다"를 지키기 위한 선택).
		test("an empty configuration still starts, registering nothing") {
			val registry = configuration.receivingWalletRegistry(fakeChecksum, "")

			shouldThrow<Exception> { registry.walletFor(BlockchainNetwork.BASE_SEPOLIA) }
		}

		// 형식 자체가 틀린 값은 체크섬 이전에 WalletAddress가 거부한다.
		test("a malformed address is rejected before the checksum check") {
			shouldThrow<IllegalArgumentException> { configuration.receivingWalletRegistry(fakeChecksum, "not-an-address") }
		}
	})
