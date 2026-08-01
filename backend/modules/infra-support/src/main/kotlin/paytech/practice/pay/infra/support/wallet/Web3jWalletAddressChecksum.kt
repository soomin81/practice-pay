package paytech.practice.pay.infra.support.wallet

import org.springframework.stereotype.Component
import org.web3j.crypto.Keys
import paytech.practice.pay.application.port.outbound.WalletAddressChecksum

/**
 * web3j로 [WalletAddressChecksum] Port를 구현한다.
 *
 * `Keys.toChecksumAddress`는 EIP-55 그대로다 — 주소를 소문자로 만든 뒤 keccak256 해시의
 * 각 니블로 대소문자를 정한다. **직접 구현하지 않는 이유**는 이 값이 틀리면 정상 주소를
 * 거부하거나 오타를 통과시키는데, 그 오류를 우리 테스트가 잡아주지 못하기 때문이다
 * (검증 대상이 곧 검증 도구가 된다). 이미 실제 체인과 맞물려 검증된 구현을 쓴다.
 *
 * `web3j-core`가 아니라 `web3j-crypto`만 받는다 — RPC 클라이언트가 필요 없다.
 */
@Component
class Web3jWalletAddressChecksum : WalletAddressChecksum {
	override fun toChecksumAddress(address: String): String = Keys.toChecksumAddress(address)
}
