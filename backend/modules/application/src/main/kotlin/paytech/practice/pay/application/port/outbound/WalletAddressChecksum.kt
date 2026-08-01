package paytech.practice.pay.application.port.outbound

/**
 * EVM 주소의 **EIP-55 체크섬**을 계산하는 Outbound Port다.
 *
 * **왜 Port인가**: 체크섬 계산에 keccak256이 필요한데 그건 외부 라이브러리다.
 * `modules:application`은 인프라 라이브러리에 의존할 수 없으므로(`ApplicationPurityTest`)
 * 경계를 Port로 끊는다 — `PaymentExportWriter`(Apache POI)와 같은 판단이다. 구현은
 * `modules:infra-support`의 `Web3jWalletAddressChecksum`이다.
 *
 * **무엇을 막으려는 것인가**: 운영자가 수취 지갑 주소를 **오타로** 설정하는 것이다.
 * EIP-55는 주소의 대소문자에 체크섬을 실어 한 글자만 틀려도 드러나게 만든 규약이라,
 * 사람이 손으로 옮겨 적는 값에 대한 방어로 정확히 이 용도다.
 *
 * `domain`의 `WalletAddress`는 형식(`0x` + 40 hex)만 검증하고 체크섬은 보지 않는다
 * (의도적 — "EIP-55 checksum 검증은 하지 않는다"). 그 판단은 그대로 두고, **사람이 설정하는
 * 값에 대해서만** 이 Port로 한 겹 더 검증한다.
 */
fun interface WalletAddressChecksum {
	/**
	 * [address]를 EIP-55 체크섬 형태(대소문자가 섞인 정규 형태)로 돌려준다.
	 * 입력의 대소문자는 무시한다 — 형식이 올바른 주소면 언제나 같은 결과가 나온다.
	 */
	fun toChecksumAddress(address: String): String

	/**
	 * [address]가 이미 체크섬 형태인지. **`false`는 "오타이거나, 체크섬 없이 적힌 주소"를
	 * 뜻한다** — 둘을 구분할 방법은 없고, 구분할 필요도 없다. 어느 쪽이든 사람이 지갑에서
	 * 복사한 값을 그대로 넣게 만드는 것이 옳은 대응이다.
	 */
	fun isChecksummed(address: String): Boolean = toChecksumAddress(address) == address
}
