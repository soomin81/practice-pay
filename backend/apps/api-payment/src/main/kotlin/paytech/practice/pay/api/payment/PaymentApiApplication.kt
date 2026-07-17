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
 * 클래스의 패키지(`paytech.practice.pay.api.payment`)와 형제 관계이기 때문에, 실제
 * 컨트롤러/Use Case가 추가되면 `@SpringBootApplication(scanBasePackages = [...])`로
 * 스캔 범위를 명시해야 한다. 지금은 부팅 가능한 최소 골격만 갖춘 상태다.
 */
@SpringBootApplication
class PaymentApiApplication

fun main(args: Array<String>) {
	runApplication<PaymentApiApplication>(*args)
}
