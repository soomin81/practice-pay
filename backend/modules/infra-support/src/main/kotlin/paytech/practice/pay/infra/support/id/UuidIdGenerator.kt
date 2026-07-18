package paytech.practice.pay.infra.support.id

import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.IdGenerator
import java.util.UUID

/**
 * [IdGenerator] Port를 `UUID`로 구현한다.
 *
 * 하이픈을 뺀 32자 16진 문자열을 반환한다 — Use Case가 여기에 각 Aggregate ID의
 * 접두어(`pay_`, `iu_`, `evt_` 등)를 붙여 최종 공개 ID를 만든다([IdGenerator]의
 * KDoc 참고).
 *
 * 원래 `apps:api-payment`/`api-admin`/`batch` 세 곳에 똑같이 복제돼 있던 구현이다.
 */
@Component
class UuidIdGenerator : IdGenerator {
	override fun newId(): String = UUID.randomUUID().toString().replace("-", "")
}
