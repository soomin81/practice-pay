package paytech.practice.pay.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent

/**
 * 모든 ArchUnit 규칙이 공유하는 패키지 상수다.
 *
 * 규칙마다 패키지 문자열을 직접 적으면 패키지를 옮겼을 때 규칙이 조용히
 * "0개 클래스에 적용되는" 상태가 된다(ArchUnit은 대상이 하나도 없어도 통과한다) —
 * 그래서 문자열을 한 곳에 모으고, [productionClasses]와 함께
 * `HexagonalLayerTest`의 "레이어가 비어 있지 않다" 가드로 그 상황을 잡는다.
 *
 * 값에는 `..`(하위 패키지 전체) 접미사를 붙이지 않는다 — 규칙에서는 `"$DOMAIN.."`처럼
 * 필요할 때 붙이고, 가드에서는 `startsWith`로 그대로 쓴다.
 */
internal object Packages {
	/** `modules:domain` — 순수 Kotlin 도메인 계층 */
	const val DOMAIN = "paytech.practice.pay.domain"

	/** `modules:domain`의 공용 값 객체(`Money`/`WalletAddress` 등) — 모든 애그리게이트가 공유한다 */
	const val DOMAIN_SHARED = "paytech.practice.pay.domain.shared"

	/** `modules:application` — Use Case + outbound Port */
	const val APPLICATION = "paytech.practice.pay.application"

	/** outbound Port 선언 위치(구현체는 `modules:infra-*`와 각 앱의 `support` 패키지에 있다) */
	const val APPLICATION_PORT = "paytech.practice.pay.application.port.outbound"

	/** `modules:infra-persistence` + `modules:infra-blockchain` — outbound Adapter */
	const val INFRA = "paytech.practice.pay.infra"

	/** jOOQ Repository Adapter */
	const val PERSISTENCE_ADAPTER = "paytech.practice.pay.infra.persistence.jooq"

	/** `apps:api-payment`/`api-admin`/`api-merchant` — inbound(HTTP) Adapter */
	const val API_APPS = "paytech.practice.pay.api"

	/** `apps:batch` — inbound(스케줄러/Job) Adapter */
	const val BATCH_APP = "paytech.practice.pay.batch"

	/**
	 * `db-core`가 생성한 jOOQ 코드.
	 *
	 * 아래 [productionClasses]가 이 패키지를 **임포트하지 않는다**(규칙의 검사 대상이 아니다) —
	 * 하지만 다른 클래스가 이 패키지를 참조하는지는 클래스 이름만으로 판단할 수 있어서
	 * `PersistenceAdapterTest`의 격리 규칙은 정상 동작한다.
	 */
	const val JOOQ_GENERATED = "paytech.practice.pay.dbcore.jooq"
}

/**
 * 검사 대상이 되는 모든 프로덕션 클래스.
 *
 * Spec마다 다시 임포트하면 같은 클래스를 반복해서 읽게 되므로 한 번만 읽어서 공유한다.
 * 테스트 클래스(`DO_NOT_INCLUDE_TESTS`)와 jOOQ 생성 코드([Packages.JOOQ_GENERATED],
 * 임포트 목록에 넣지 않는 것으로 제외)는 대상이 아니다 — 둘 다 우리가 손으로 쓰는
 * 코드가 아니라서 아키텍처 규칙을 적용할 대상이 아니다.
 */
internal val productionClasses: JavaClasses by lazy {
	ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages(
			Packages.DOMAIN,
			Packages.APPLICATION,
			Packages.INFRA,
			Packages.API_APPS,
			Packages.BATCH_APP,
		)
}

/**
 * `application.port.outbound`의 Port를 실제로 구현하는지 검사하는 조건.
 *
 * `PersistenceAdapterTest`는 "Adapter는 Port를 구현해야 한다"로, `HexagonalLayerTest`는
 * `noClasses().should(...)`로 뒤집어 "앱은 Port를 구현하면 안 된다"로 쓴다.
 */
internal val implementAnOutboundPort =
	object : ArchCondition<JavaClass>("application.port.outbound의 Port를 구현한다") {
		override fun check(
			item: JavaClass,
			events: ConditionEvents,
		) {
			val implementsPort =
				item.allRawInterfaces.any { it.packageName.startsWith(Packages.APPLICATION_PORT) }
			events.add(
				SimpleConditionEvent(
					item,
					implementsPort,
					"${item.name}이(가) ${Packages.APPLICATION_PORT}의 Port를 구현한다",
				),
			)
		}
	}

/**
 * 중첩 클래스(Kotlin `companion object`가 만드는 `...$Companion` 등)를 감안한 최상위 클래스 이름.
 *
 * `JavaClass.simpleName`은 `PaymentId.Companion`에 대해 `"Companion"`을 돌려주기 때문에,
 * "다른 애그리게이트는 `*Id`로만 참조한다" 같은 이름 기반 규칙이 오탐을 낸다.
 */
internal fun JavaClass.topLevelSimpleName(): String {
	var current = this
	while (current.enclosingClass.isPresent) {
		current = current.enclosingClass.get()
	}
	return current.simpleName
}
