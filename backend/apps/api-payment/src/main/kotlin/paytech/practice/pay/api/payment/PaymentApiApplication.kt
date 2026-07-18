package paytech.practice.pay.api.payment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 가맹점의 결제 API 서버(`apps/api-payment`)다. `modules:application`의
 * `CreatePaymentUseCase` 등 결제 관련 Use Case를 HTTP로 노출하는 진입점이며,
 * `modules:infra-persistence`가 그 Use Case의 outbound port를 구현한다.
 *
 * `@SpringBootApplication`의 컴포넌트 스캔 기본 범위(이 클래스가 속한 패키지의
 * 하위 패키지 전체)는 `paytech.practice.pay`로는 확장되지 않는다 — `infra-persistence`의
 * Adapter들이 `paytech.practice.pay.infra.persistence.jooq` 패키지에 있어서 이
 * 클래스의 패키지(`paytech.practice.pay.api.payment`)와 형제 관계이기 때문에,
 * `scanBasePackages`로 두 패키지를 모두 명시했다 — 이게 없으면 `MerchantRepositoryAdapter`
 * 등의 `@Repository` Bean이 인식되지 않는다.
 */
@SpringBootApplication(
	scanBasePackages = [
		"paytech.practice.pay.api.payment",
		"paytech.practice.pay.infra.persistence.jooq",
		// modules:infra-support는 통째로 스캔하지 않고 이 앱이 쓰는 Port 구현만
		// 고른다 — `infra.support.security`의 HmacInvitationTokenHasher가 초대
		// 흐름 전용 설정값(app.invitation-token.pepper)을 요구해서, 그것까지
		// 스캔하면 이 앱의 컨텍스트가 뜨지 않는다.
		"paytech.practice.pay.infra.support.id",
		"paytech.practice.pay.infra.support.exchange",
	],
)
class PaymentApiApplication

fun main(args: Array<String>) {
	runApplication<PaymentApiApplication>(*args)
}
