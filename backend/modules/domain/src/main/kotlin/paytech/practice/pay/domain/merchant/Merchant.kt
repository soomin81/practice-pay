package paytech.practice.pay.domain.merchant

import paytech.practice.pay.domain.shared.HttpUrl
import java.time.Duration
import java.time.Instant

/**
 * 가맹점(Merchant) Aggregate Root다.
 *
 * 가맹점 식별, 상태, 결제 가능 여부, Webhook 설정을 관리한다
 * (`docs/domain/domain-model.md`). 상태는 이 클래스의 메서드를 통해서만
 * 변경되고, 전이 전 현재 상태를 검증한다.
 *
 * `docs/domain/state-transitions.md`는 Merchant의 상태 전이를 다루지 않는다 —
 * 아래 전이 메서드들은 [MerchantStatus]의 KDoc에 적어 둔 근거로 직접 설계했다.
 *
 * 인스턴스는 [create]로 새로 만들거나 [reconstitute]로 저장된 값을 복원해서 얻는다.
 *
 * @see docs/domain/domain-model.md
 */
class Merchant private constructor(
	val id: MerchantId,
	val code: MerchantCode,
	val name: String,
	val createdAt: Instant,
	status: MerchantStatus,
	webhookUrl: HttpUrl?,
	webhookSecretVersion: Int,
	webhookSecretRotatedAt: Instant?,
	updatedAt: Instant,
) {
	var status: MerchantStatus = status
		private set

	var webhookUrl: HttpUrl? = webhookUrl
		private set

	/**
	 * Webhook 서명 비밀의 **세대**다. 비밀 자체는 이 애그리게이트에도 DB에도 없다 —
	 * 서명하는 쪽이 `(id, 이 값)`으로 매번 파생한다(`WebhookSigner`).
	 *
	 * **이 값은 비밀이 아니다.** 노출돼도 무해하고, 그래서 도메인이 들고 있어도
	 * 자격증명을 도메인에 새게 하지 않는다.
	 */
	var webhookSecretVersion: Int = webhookSecretVersion
		private set

	/**
	 * 서명 비밀을 마지막으로 교체한 시각. `null`이면 한 번도 교체하지 않았다는 뜻이다.
	 *
	 * **직전 세대를 따로 저장하지 않는 이유**: 세대는 1씩 올라가므로 직전은 언제나
	 * [webhookSecretVersion]` - 1`이다. 실제로 모르는 것은 "언제 교체했는가" 하나뿐이라
	 * 그것만 들고 [activeWebhookSecretVersions]로 계산한다.
	 */
	var webhookSecretRotatedAt: Instant? = webhookSecretRotatedAt
		private set

	var updatedAt: Instant = updatedAt
		private set

	init {
		require(name.isNotBlank()) { "name은 공백일 수 없습니다." }
		require(webhookSecretVersion >= 1) { "webhookSecretVersion은 1 이상이어야 합니다: $webhookSecretVersion" }
	}

	/** 지금 결제를 받을 수 있는 상태인지 확인한다. `ACTIVE`일 때만 `true`다. */
	fun canAcceptPayments(): Boolean = status == MerchantStatus.ACTIVE

	/** `ACTIVE` → `SUSPENDED`. */
	fun suspend(changedAt: Instant) {
		checkTransition(status == MerchantStatus.ACTIVE, MerchantStatus.SUSPENDED)
		status = MerchantStatus.SUSPENDED
		updatedAt = changedAt
	}

	/** `SUSPENDED` → `ACTIVE`. */
	fun reactivate(changedAt: Instant) {
		checkTransition(status == MerchantStatus.SUSPENDED, MerchantStatus.ACTIVE)
		status = MerchantStatus.ACTIVE
		updatedAt = changedAt
	}

	/** (`ACTIVE` 또는 `SUSPENDED`) → `TERMINATED`. 종료 상태이며 되돌릴 수 없다. */
	fun terminate(changedAt: Instant) {
		checkTransition(
			status == MerchantStatus.ACTIVE || status == MerchantStatus.SUSPENDED,
			MerchantStatus.TERMINATED,
		)
		status = MerchantStatus.TERMINATED
		updatedAt = changedAt
	}

	/** Webhook 수신 URL을 갱신한다. `null`을 넘기면 설정을 해제한다. 상태 전이는 아니다. */
	fun updateWebhookUrl(
		webhookUrl: HttpUrl?,
		changedAt: Instant,
	) {
		this.webhookUrl = webhookUrl
		updatedAt = changedAt
	}

	/**
	 * Webhook 서명 비밀을 교체한다 — 세대를 1 올리면 파생 입력이 달라져 새 비밀이 나온다.
	 * 상태 전이는 아니다.
	 *
	 * **직전 비밀이 곧바로 죽지는 않는다** — 교체 시각을 남겨 두고, 겹침 기간 동안은
	 * [activeWebhookSecretVersions]가 두 세대를 함께 돌려준다. 그러지 않으면 가맹점이
	 * 새 비밀을 자기 서버에 반영하기 전까지의 Webhook을 통째로 놓치는데, 그건 교체가
	 * 필요한 상황(비밀 노출)에서 가장 하고 싶지 않은 일이다.
	 *
	 * 되돌릴 수는 없다 — 겹침 기간이 지나면 옛 비밀은 영영 무효다.
	 */
	fun rotateWebhookSecret(changedAt: Instant) {
		webhookSecretVersion += 1
		webhookSecretRotatedAt = changedAt
		updatedAt = changedAt
	}

	/**
	 * [at] 시점에 **유효한 서명 비밀 세대**를 최신순으로 돌려준다.
	 *
	 * 겹침 기간 안이면 `[현재, 직전]`, 아니면 `[현재]` 하나다. 세대 1은 직전이 없어
	 * 언제나 하나다.
	 *
	 * **서명하는 쪽과 화면이 같은 답을 써야 한다** — "지금 어떤 비밀이 통하는가"를 두 곳이
	 * 따로 계산하면 콘솔이 "아직 유효하다"고 말하는 동안 전송은 이미 새 비밀만 쓰는 상황이
	 * 생긴다. 그래서 그 판단을 애그리게이트에 둔다.
	 *
	 * [overlap]을 인자로 받는 것은 **얼마나 겹칠지가 도메인 규칙이 아니라 운영 정책**이기
	 * 때문이다(`PublishOutboxEventUseCase`가 상수로 정한다) — 여기서는 그 값을 받아
	 * 계산만 한다.
	 */
	fun activeWebhookSecretVersions(
		at: Instant,
		overlap: Duration,
	): List<Int> {
		val rotatedAt = webhookSecretRotatedAt
		val previous = webhookSecretVersion - 1
		val stillOverlapping = rotatedAt != null && previous >= 1 && at < rotatedAt.plus(overlap)
		return if (stillOverlapping) listOf(webhookSecretVersion, previous) else listOf(webhookSecretVersion)
	}

	private fun checkTransition(
		allowed: Boolean,
		target: MerchantStatus,
	) {
		check(allowed) { "Merchant 상태를 $status 에서 $target (으)로 전이할 수 없습니다." }
	}

	companion object {
		/** 새 가맹점을 `ACTIVE` 상태로 생성한다. */
		fun create(
			id: MerchantId,
			code: MerchantCode,
			name: String,
			webhookUrl: HttpUrl?,
			createdAt: Instant,
		): Merchant =
			Merchant(
				id = id,
				code = code,
				name = name,
				createdAt = createdAt,
				status = MerchantStatus.ACTIVE,
				webhookUrl = webhookUrl,
				webhookSecretVersion = INITIAL_WEBHOOK_SECRET_VERSION,
				// 새 가맹점은 교체한 적이 없다 — 직전 세대가 없으므로 겹칠 것도 없다.
				webhookSecretRotatedAt = null,
				updatedAt = createdAt,
			)

		/** 영속 계층에 저장되어 있던 값으로 Aggregate를 복원한다. */
		fun reconstitute(
			id: MerchantId,
			code: MerchantCode,
			name: String,
			createdAt: Instant,
			status: MerchantStatus,
			webhookUrl: HttpUrl?,
			webhookSecretVersion: Int,
			webhookSecretRotatedAt: Instant?,
			updatedAt: Instant,
		): Merchant =
			Merchant(
				id = id,
				code = code,
				name = name,
				createdAt = createdAt,
				status = status,
				webhookUrl = webhookUrl,
				webhookSecretVersion = webhookSecretVersion,
				webhookSecretRotatedAt = webhookSecretRotatedAt,
				updatedAt = updatedAt,
			)

		/** 새 가맹점의 첫 서명 비밀 세대. `V8` 마이그레이션의 `DEFAULT 1`과 같은 값이다. */
		private const val INITIAL_WEBHOOK_SECRET_VERSION = 1
	}
}
