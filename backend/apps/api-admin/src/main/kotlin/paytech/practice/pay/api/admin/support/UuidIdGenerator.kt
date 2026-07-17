package paytech.practice.pay.api.admin.support

import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.IdGenerator
import java.util.UUID

/**
 * [IdGenerator] Port를 `UUID`로 구현한다(`apps:api-payment`의 `UuidIdGenerator`와
 * 같은 방식).
 *
 * 하이픈을 뺀 32자 16진 문자열을 반환한다 — Use Case가 여기에 각 Aggregate ID의
 * 접두어(`iu_`, `ai_` 등)를 붙여 최종 공개 ID를 만든다([IdGenerator]의 KDoc 참고).
 */
@Component
class UuidIdGenerator : IdGenerator {
	override fun newId(): String = UUID.randomUUID().toString().replace("-", "")
}
