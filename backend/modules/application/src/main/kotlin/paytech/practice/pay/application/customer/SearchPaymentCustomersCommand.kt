package paytech.practice.pay.application.customer

import paytech.practice.pay.domain.customer.CustomerEmail
import paytech.practice.pay.domain.customer.CustomerPhone

/**
 * [SearchPaymentCustomersUseCase]의 입력이다.
 *
 * **둘 중 정확히 하나만 채운다.** 둘 다 주면 AND 조합 탐색이 가능해지는데, 그건 "찾기"가
 * 아니라 "대조"다 — 이미 아는 이메일과 전화가 같은 사람의 것인지 확인할 수 있게 된다.
 * 검증은 Use Case가 한다(`docs/architecture/admin-console-api.md`의 4.7).
 *
 * 평문 Value Object를 담는 이유는 [CustomerEmail.normalized]/[CustomerPhone.normalized]가
 * Blind Index의 입력이기 때문이다 — 정규화를 거치지 않으면 `A@b.com`으로 검색했을 때
 * `a@b.com`으로 저장된 사람이 걸리지 않는다.
 */
data class SearchPaymentCustomersCommand(
	val email: CustomerEmail?,
	val phone: CustomerPhone?,
)
