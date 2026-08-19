package paytech.practice.pay.domain.customer

import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.payment.PaymentId
import java.time.Instant

/**
 * **마스킹되지 않은 구매자 원본을 한 번 열람했다**는 사실을 남기는 불변 감사 기록이다
 * (`customer_pii_access_audit` 테이블).
 *
 * 상태 전이가 없는 append-only 스냅샷이라 `SettlementHoldAudit`/`InternalLoginAudit`처럼
 * 공개 생성자를 가진 평범한 `data class`로 둔다(`backend/CLAUDE.md`의 "도메인 코드 컨벤션").
 *
 * ## 이 저장소에서 **읽기에 감사를 붙인 유일한 자료**다
 *
 * 다른 감사 기록(로그인, 정산 보류)은 전부 상태를 바꾸는 행위를 남긴다. 여기서는 아무것도
 * 바뀌지 않는데도 남긴다 — 원본을 봤다는 사실 자체가 사건이기 때문이다(ADR-008의 6).
 * 그래서 **기록에 실패하면 열람도 실패해야 한다**: 같은 트랜잭션 안에서 남긴다.
 *
 * ## 무엇을 남기고 무엇을 남기지 않나
 *
 * **열람한 값 자체는 남기지 않는다.** 남기면 지우려고 만든 구조(파기 가능한 별도 테이블)가
 * 무의미해진다 — `payment_customer` 행을 지워도 원본이 감사 로그에 남아 있으면 파기가
 * 반쪽이 된다. 무엇을 봤는지는 [paymentId]로 되짚는다.
 *
 * @property internalUserId 열람한 내부 운영자. **`null`이 될 수 없다** — 인증된
 * `SUPER_ADMIN`만 실행할 수 있는 행위다.
 * @property reason 왜 봤는지. 자동 경로가 없는 행위라 **실행한 사람 말고는 이유를 아는 곳이
 * 없어서** Use Case가 필수로 요구한다(정산 보류 해제의 `note`와 같은 판단이다).
 * @property clientIp 요청이 온 IP. 프록시 뒤에서는 알 수 없을 수 있어 nullable이다 —
 * 없다고 열람을 막지는 않는다(막으면 기록이 아니라 인가 수단이 된다).
 */
data class CustomerPiiAccessAudit(
	val id: CustomerPiiAccessAuditId,
	val internalUserId: InternalUserId,
	val paymentId: PaymentId,
	val reason: String,
	val clientIp: String?,
	val occurredAt: Instant,
) {
	init {
		require(reason.isNotBlank()) { "개인정보 원본 열람 사유는 공백일 수 없습니다." }
		require(reason.length <= MAX_REASON_LENGTH) {
			"개인정보 원본 열람 사유는 ${MAX_REASON_LENGTH}자를 초과할 수 없습니다."
		}
		require(clientIp == null || clientIp.length <= MAX_CLIENT_IP_LENGTH) {
			"client IP는 ${MAX_CLIENT_IP_LENGTH}자를 초과할 수 없습니다."
		}
	}

	companion object {
		/** `customer_pii_access_audit.reason` 컬럼의 최대 길이(`VARCHAR(500)`)와 동일하다. */
		private const val MAX_REASON_LENGTH = 500

		/** `VARCHAR(45)` — IPv6를 문자열로 담을 수 있는 최대 길이다. */
		private const val MAX_CLIENT_IP_LENGTH = 45
	}
}
