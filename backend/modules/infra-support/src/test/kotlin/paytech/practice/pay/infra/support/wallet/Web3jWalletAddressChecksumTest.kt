package paytech.practice.pay.infra.support.wallet

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * EIP-55 검증의 목적은 **오타를 잡는 것**이다. 그래서 "정규 형태를 통과시킨다"보다
 * "한 글자가 틀리면 걸린다"가 더 중요한 단언이다.
 *
 * 기준값은 이 프로젝트가 실제로 쓰는 두 주소다 — Base Sepolia USDC Contract(Circle 공식
 * 문서 값)와 실물 검증에 쓴 수취 지갑.
 */
class Web3jWalletAddressChecksumTest :
	FunSpec({
		val checksum = Web3jWalletAddressChecksum()

		test("normalizes any case to the same checksummed form") {
			val canonical = "0x036CbD53842c5426634e7929541eC2318f3dCF7e"

			checksum.toChecksumAddress(canonical.lowercase()) shouldBe canonical
			checksum.toChecksumAddress(canonical.uppercase().replace("0X", "0x")) shouldBe canonical
			checksum.toChecksumAddress(canonical) shouldBe canonical
		}

		test("accepts a checksummed address") {
			checksum.isChecksummed("0x036CbD53842c5426634e7929541eC2318f3dCF7e") shouldBe true
			checksum.isChecksummed("0x9dC10cd9f75B98DE43c8B8B40D4c6B4DA5Cab9e1") shouldBe true
		}

		/**
		 * **이 테스트가 이 기능의 존재 이유다.** 마지막 글자만 `e` → `f`로 바꾼 주소는 형식
		 * 검증(`0x` + 40 hex)을 그대로 통과하지만 체크섬은 어긋난다 — 그 오타를 잡지 못하면
		 * 고객이 보낸 USDC가 아무도 통제하지 못하는 주소로 간다.
		 */
		test("rejects a one-character typo that passes format validation") {
			val typo = "0x036CbD53842c5426634e7929541eC2318f3dCF7f"

			typo.length shouldBe 42
			checksum.isChecksummed(typo) shouldBe false
		}

		// 체크섬 정보가 없는 주소(전부 소문자)는 오타를 검증할 수 없으므로 통과시키지 않는다.
		test("rejects an address carrying no checksum information") {
			checksum.isChecksummed("0x036cbd53842c5426634e7929541ec2318f3dcf7e") shouldBe false
		}
	})
