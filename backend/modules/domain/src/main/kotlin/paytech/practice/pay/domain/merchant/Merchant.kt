package paytech.practice.pay.domain.merchant

import paytech.practice.pay.domain.shared.HttpUrl
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
	 * Webhook 서명 비밀을 교체한다 — 세대를 1 올리면 파생 입력이 달라져
	 * **이전 비밀은 즉시 무효가 된다.** 상태 전이는 아니다.
	 *
	 * 되돌릴 수 없다: 교체하는 순간 옛 비밀로 검증하던 가맹점 서버는 서명이
	 * 맞지 않아 전부 거부하게 된다. 그래서 화면은 이 동작을 확인 절차 뒤에 둔다.
	 */
	fun rotateWebhookSecret(changedAt: Instant) {
		webhookSecretVersion += 1
		updatedAt = changedAt
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
				updatedAt = updatedAt,
			)

		/** 새 가맹점의 첫 서명 비밀 세대. `V8` 마이그레이션의 `DEFAULT 1`과 같은 값이다. */
		private const val INITIAL_WEBHOOK_SECRET_VERSION = 1
	}
}
