package paytech.practice.pay.infra.blockchain.web3j

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

/**
 * [Web3jBlockchainClient]가 쓸 [Web3j] Bean을 만든다.
 *
 * MVP는 Base Sepolia 하나만 지원해서([paytech.practice.pay.domain.shared.BlockchainNetwork.BASE_SEPOLIA])
 * RPC 엔드포인트를 네트워크별로 여러 개 관리하지 않고 값 하나(`app.blockchain.base-sepolia.rpc-url`)만
 * 받는다 — 다중 네트워크가 실제로 필요해지면 `BlockchainNetwork`별 `Web3j` Bean
 * 맵으로 넓힌다. 이 모듈 자체에는 `application.yaml`이 없다(라이브러리 모듈이라
 * 그 값을 가질 앱이 없다) — 이 Bean을 실제로 쓰는 앱의 `application.yaml`에
 * `app.blockchain.base-sepolia.rpc-url`을 정의해야 한다.
 */
@Configuration
class Web3jConfiguration {
	@Bean
	fun web3j(
		@Value("\${app.blockchain.base-sepolia.rpc-url}") rpcUrl: String,
	): Web3j = Web3j.build(HttpService(rpcUrl))
}
