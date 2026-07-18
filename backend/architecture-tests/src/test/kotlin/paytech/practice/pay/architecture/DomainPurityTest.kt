package paytech.practice.pay.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.core.spec.style.FunSpec

/**
 * `modules:domain`이 **순수 Kotlin 외에는 아무것도 모른다**는 규칙
 * (`backend/CLAUDE.md`의 Architecture 절, `docs/domain/domain-model.md`의
 * "도메인은 Spring과 jOOQ에 의존하지 않는다")을 강제한다.
 *
 * 계층 사이의 의존 *방향*은 `HexagonalLayerTest`가 본다 — 이 Spec은 도메인이
 * 프레임워크·인프라 라이브러리·영속성 전용 타입에 오염되지 않았는지만 본다.
 */
class DomainPurityTest :
	FunSpec({

		test("domain must not depend on frameworks or infrastructure libraries") {
			noClasses()
				.that()
				.resideInAPackage("${Packages.DOMAIN}..")
				.should()
				.dependOnClassesThat()
				.resideInAnyPackage(*FORBIDDEN_IN_PURE_LAYERS)
				.because("도메인은 순수 Kotlin 외에는 아무것도 의존하지 않는다")
				.check(productionClasses)
		}

		test("domain must not use persistence-specific date types") {
			noClasses()
				.that()
				.resideInAPackage("${Packages.DOMAIN}..")
				.should()
				.dependOnClassesThat()
				.haveFullyQualifiedName("java.time.LocalDateTime")
				.orShould()
				.dependOnClassesThat()
				.haveFullyQualifiedName("java.util.Date")
				.because(
					"도메인 시각은 항상 UTC `Instant`다 — `DATETIME(6)`용 `LocalDateTime` 변환은 " +
						"`infra.persistence.jooq.InstantMapping`(Adapter 경계)의 책임이다",
				).check(productionClasses)
		}

		// docs/domain/domain-model.md: "애그리게이트는 다른 애그리게이트를 항상 ID로만
		// 참조하고, 객체 참조로는 참조하지 않는다".
		test("aggregates must reference other aggregates by id only") {
			classes()
				.that()
				.resideInAPackage("${Packages.DOMAIN}..")
				.should(referenceOtherAggregatesByIdOnly)
				.check(productionClasses)
		}
	})

/**
 * 도메인/애플리케이션 계층 어느 쪽에도 있어서는 안 되는 패키지들.
 *
 * `docs/domain/domain-model.md`가 금지하는 "프레임워크와 인프라"를 이 프로젝트가 실제로
 * 쓰는 라이브러리로 구체화한 목록이다 — 새 인프라 라이브러리를 도입하면 여기에 추가한다.
 */
internal val FORBIDDEN_IN_PURE_LAYERS =
	arrayOf(
		"org.springframework..",
		"org.jooq..",
		"org.web3j..",
		"tools.jackson..",
		"com.fasterxml.jackson..",
		"jakarta..",
		"java.sql..",
		"javax.sql..",
		"java.net.http..",
	)

/**
 * "다른 애그리게이트는 `*Id` 값 객체로만 참조한다"를 검사하는 조건.
 *
 * 같은 애그리게이트 패키지 안의 참조와, 모든 애그리게이트가 공유하도록 만들어진
 * [Packages.DOMAIN_SHARED]의 값 객체(`Money`/`WalletAddress` 등)는 허용한다.
 */
private val referenceOtherAggregatesByIdOnly =
	object : ArchCondition<JavaClass>("다른 애그리게이트를 ID 값 객체로만 참조해야 한다") {
		override fun check(
			item: JavaClass,
			events: ConditionEvents,
		) {
			item.directDependenciesFromSelf
				.filter { dependency ->
					val targetPackage = dependency.targetClass.packageName
					targetPackage.startsWith("${Packages.DOMAIN}.") &&
						targetPackage != Packages.DOMAIN_SHARED &&
						targetPackage != item.packageName
				}.filterNot { dependency -> dependency.targetClass.topLevelSimpleName().endsWith("Id") }
				.forEach { dependency ->
					events.add(
						SimpleConditionEvent.violated(
							item,
							"${item.name}이(가) 다른 애그리게이트를 객체로 참조한다: ${dependency.description}",
						),
					)
				}
		}
	}
