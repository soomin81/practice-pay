package paytech.practice.pay.infra.support.webhook

import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.WEBHOOK_SIGNATURE_HEADER
import paytech.practice.pay.application.port.outbound.WebhookSendResult
import paytech.practice.pay.application.port.outbound.WebhookSender
import paytech.practice.pay.domain.shared.HttpUrl
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * [WebhookSender] Port를 JDK 내장 `java.net.http.HttpClient`로 구현한다 — 이
 * 프로젝트에서 처음으로 아웃바운드 HTTP 호출이 필요해졌지만, 유일한 사용처인
 * `apps:batch`가 웹 앱이 아니라서(`spring-boot-starter-web*` 없음) Spring의
 * `RestClient`/`WebClient`를 새로 끌어오는 대신 별도 의존성이 필요 없는 JDK 내장
 * 클라이언트를 썼다.
 *
 * 필수 설정값을 요구하지 않아서(위 두 Hasher와 달리 `@Value`가 없다) 아무 앱이
 * 스캔해도 안전하지만, 쓰지도 않는 Bean을 만들 이유가 없어 `apps:batch`만
 * 이 하위 패키지를 스캔한다.
 *
 * [HttpClient]는 스레드 안전하고 재사용을 전제로 설계된 타입이라 인스턴스 하나를
 * 필드로 유지한다.
 */
@Component
class HttpWebhookSender : WebhookSender {
	private val httpClient: HttpClient =
		HttpClient
			.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.build()

	override fun send(
		destinationUrl: HttpUrl,
		payload: String,
		signatureHeaderValue: String,
	): WebhookSendResult {
		val request =
			HttpRequest
				.newBuilder(URI.create(destinationUrl.value))
				.timeout(REQUEST_TIMEOUT)
				.header("Content-Type", "application/json")
				.header(WEBHOOK_SIGNATURE_HEADER, signatureHeaderValue)
				.POST(HttpRequest.BodyPublishers.ofString(payload))
				.build()

		return try {
			val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
			WebhookSendResult.Responded(response.statusCode())
		} catch (ex: IOException) {
			WebhookSendResult.Failed(ex.message ?: "연결에 실패했습니다.")
		} catch (ex: InterruptedException) {
			Thread.currentThread().interrupt()
			WebhookSendResult.Failed(ex.message ?: "요청이 중단됐습니다.")
		}
	}

	companion object {
		private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
		private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
	}
}
