package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Instant

/**
 * 가맹점별 Webhook 서명 비밀을 파생하고, 그 비밀로 요청 본문에 서명하는 Outbound Port다.
 *
 * ## 왜 필요한가
 *
 * 서명이 없으면 **가맹점은 받은 Webhook이 PG에서 온 것인지 확인할 방법이 없다.**
 * 수신 URL만 알면 누구나 `payment.succeeded`를 위조해 보낼 수 있고, 그걸 믿은
 * 가맹점은 받지도 않은 돈에 대해 상품을 내보낸다 — 손해가 **가맹점 쪽에** 생긴다.
 *
 * ## 왜 비밀을 저장하지 않고 파생하나
 *
 * 이 시스템의 다른 자격증명(비밀번호, 초대 Token, API Key)은 전부 **검증만** 하면
 * 되므로 Hash로 저장한다(`docs/architecture/persistence-jooq.md`의 "인증 정보 저장
 * 규칙"). Webhook 서명 비밀은 다르다 — PG가 **직접 서명하는 데 써야** 해서 원문을
 * 되찾을 수 있어야 하고, Hash로는 그럴 수 없다.
 *
 * 그렇다고 평문 컬럼에 두면 **DB 유출 하나가 곧 전 가맹점 Webhook 위조**가 된다.
 * 그래서 저장하는 대신 서버 Pepper로부터 파생한다:
 *
 * ```
 * secret = "whsec_" + base64url(HMAC-SHA256(pepper, "{merchantId}:{version}"))
 * ```
 *
 * 결과적으로 **DB에는 비밀이 아예 없다** — 통째로 유출돼도 Pepper 없이는 아무것도
 * 위조할 수 없고, 저장된 `webhook_secret_version`은 세대 번호일 뿐이라 무해하다.
 *
 * ## 서명 형식
 *
 * ```
 * X-PracticePay-Signature: t=1754092800,v1=9f86d081884c7d65...,v1=3ba3edfd7a7b12b2...
 * ```
 *
 * 서명 대상은 본문만이 아니라 **`"{t}.{본문}"`**이다. 타임스탬프를 서명 안에 넣지
 * 않으면 가로챈 요청을 그대로 다시 보내는 **재전송 공격**을 가맹점이 막을 수 없다 —
 * 본문만 서명하면 그 서명은 영원히 유효하기 때문이다. `t`를 함께 서명해 두면
 * 가맹점은 "서명이 맞고 **동시에** `t`가 충분히 최근인가"로 판단할 수 있다.
 *
 * **`v1`이 여러 개일 수 있다.** 비밀을 교체하면 겹침 기간 동안 새 비밀과 직전 비밀로
 * 각각 서명해 둘 다 싣는다 — 가맹점은 **하나라도 맞으면** 받아들이면 된다. 그래야
 * 새 비밀을 자기 서버에 반영하는 동안에도 Webhook을 놓치지 않는다. 그러니 파싱할 때
 * `v1`을 **하나로 가정하지 않는다.**
 *
 * `v1=`은 형식 버전이기도 하다. 나중에 알고리즘을 바꿔야 할 때 `v2`를 **함께** 실어
 * 보내 가맹점이 옮겨갈 시간을 벌 수 있다(지금은 `v1`만 쓴다) — 그때도 **모르는 항목은
 * 무시**하면 된다.
 *
 * 전체 계약과 가맹점 측 검증 방법은 `docs/architecture/webhook-api.md`에 있다.
 */
interface WebhookSigner {
	/**
	 * [merchantId]/[secretVersion]에 해당하는 서명 비밀을 파생한다.
	 *
	 * 가맹점 콘솔이 화면에 보여주기 위해 쓴다 — 가맹점이 자기 서버에 넣어야
	 * 검증할 수 있으므로, API Key와 달리 **언제든 다시 볼 수 있어야 한다**
	 * (파생이라 "한 번만 보여주고 버린다"가 애초에 불가능하기도 하다).
	 */
	fun deriveSecret(
		merchantId: MerchantId,
		secretVersion: Int,
	): String

	/**
	 * [payload]에 대한 `X-PracticePay-Signature` **헤더 값 전체**를 만든다
	 * (`t=...,v1=...[,v1=...]`).
	 *
	 * [secretVersions]에 담긴 **세대마다 서명을 하나씩** 만들어 순서대로 싣는다 —
	 * 겹침 기간에는 `[현재, 직전]` 두 개가 온다(`Merchant.activeWebhookSecretVersions`).
	 * 비어 있으면 서명할 비밀이 없다는 뜻이라 호출 자체가 잘못이다.
	 *
	 * 헤더 이름이 아니라 값을 돌려주는 이유는, 헤더 이름이 전송 수단이 아니라
	 * **가맹점과의 계약**이라 애플리케이션 계층([WEBHOOK_SIGNATURE_HEADER])에
	 * 있어야 하기 때문이다.
	 */
	fun signatureHeaderValue(
		merchantId: MerchantId,
		secretVersions: List<Int>,
		payload: String,
		signedAt: Instant,
	): String
}

/**
 * 서명이 실리는 헤더 이름이다. 전송 구현이 아니라 여기 있는 이유는 이것이
 * **가맹점이 코드에 적어 넣는 공개 계약**이기 때문이다 — 바꾸면 모든 가맹점의
 * 검증이 깨진다.
 */
const val WEBHOOK_SIGNATURE_HEADER = "X-PracticePay-Signature"
