package paytech.practice.pay.domain.merchant

/**
 * [Merchant]의 상태를 표현한다.
 *
 * `docs/domain/state-transitions.md`는 Merchant의 상태 전이를 다루지 않는다 —
 * 이 enum과 [Merchant]의 전이 메서드는 `merchant_status` 컬럼의 CHECK 제약
 * (`ACTIVE`, `SUSPENDED`, `TERMINATED`)과 domain-model.md의 "가맹점 식별, 상태,
 * 결제 가능 여부, Webhook 설정을 관리한다"는 설명을 근거로 직접 설계한 것이다.
 *
 * `ACTIVE`만 결제를 받을 수 있다([Merchant.canAcceptPayments]). `TERMINATED`는
 * 종료 상태이며 재사용하지 않는다 — `ACTIVE`↔`SUSPENDED`는 서로 오갈 수 있지만
 * `TERMINATED`에서 되돌아갈 수는 없다.
 */
enum class MerchantStatus {
	ACTIVE,
	SUSPENDED,
	TERMINATED,
}
