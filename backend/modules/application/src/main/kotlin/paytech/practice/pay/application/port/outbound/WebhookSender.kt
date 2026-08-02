package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.shared.HttpUrl

/**
 * 가맹점 서버로 Webhook을 실제로 전송하는 Outbound Port다.
 * `modules:infra-blockchain`이 온체인 RPC를 web3j로 감추는 것과 같은 자리에서,
 * 이 Port는 실제 HTTP 전송(예: JDK `java.net.http.HttpClient`)을 감춘다.
 *
 * **HTTP 응답을 받은 것(`Responded`, 상태 코드가 4xx/5xx여도 포함)과 애초에
 * 응답을 못 받은 것(`Failed`, 연결 실패·타임아웃)을 구분한다** — 어느 쪽이든
 * "성공"으로 칠지, 재시도할지, 최종 실패로 볼지는 이 Port가 아니라 호출부
 * (`PublishOutboxEventUseCase`)의 판단이다. 이 Port는 "무슨 일이 있었는지"만
 * 사실대로 돌려준다.
 */
fun interface WebhookSender {
	/**
	 * [destinationUrl]로 [payload](JSON 문자열)를 전송한다.
	 *
	 * [signatureHeaderValue]는 [WEBHOOK_SIGNATURE_HEADER]에 그대로 실린다 —
	 * 값을 만드는 것은 [WebhookSigner]의 몫이고, 이 Port는 그것을 헤더에 얹어
	 * 보내기만 한다(전송 수단은 서명 방식을 몰라야 한다).
	 */
	fun send(
		destinationUrl: HttpUrl,
		payload: String,
		signatureHeaderValue: String,
	): WebhookSendResult
}

/** [WebhookSender.send]의 결과다. */
sealed interface WebhookSendResult {
	/** 응답을 받았다 — [httpStatus]가 2xx가 아니어도 이 값이다(호출부가 성공 여부를 판단한다). */
	data class Responded(
		val httpStatus: Int,
	) : WebhookSendResult

	/** 연결 실패, 타임아웃 등으로 응답 자체를 받지 못했다. */
	data class Failed(
		val errorMessage: String,
	) : WebhookSendResult
}
