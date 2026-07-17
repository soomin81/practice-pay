package paytech.practice.pay.infra.persistence.jooq

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * 도메인의 [Instant]와 `DATETIME(6)` 컬럼(UTC로 저장)에 매핑되는 [LocalDateTime]
 * 사이를 변환한다(`docs/architecture/persistence-jooq.md`의 `DATETIME(6) UTC ↔
 * 애플리케이션 시간 타입` 규칙). 모든 Repository Adapter가 공유해서 쓴다.
 */
fun Instant.toUtcLocalDateTime(): LocalDateTime = LocalDateTime.ofInstant(this, ZoneOffset.UTC)

/** [toUtcLocalDateTime]의 역변환이다. */
fun LocalDateTime.toUtcInstant(): Instant = this.toInstant(ZoneOffset.UTC)
