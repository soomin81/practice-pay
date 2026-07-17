package paytech.practice.pay.domain.apikey

/**
 * [MerchantApiKey]가 어느 결제 네트워크 환경용인지를 표현한다.
 *
 * 현재는 Base Sepolia(Testnet)만 지원하므로 `TEST` Key만 발급한다
 * (`docs/architecture/mvp-scope.md`). `LIVE`는 향후 Mainnet 지원 시 쓰인다 —
 * API Key 환경과 결제 네트워크 환경은 반드시 일치해야 한다
 * (`docs/architecture/identity-access-api-key.md`).
 */
enum class ApiEnvironment {
	TEST,
	LIVE,
}
