package paytech.practice.pay.architecture

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.core.spec.style.FunSpec

/**
 * 영속성 Adapter 컨벤션(`backend/CLAUDE.md`의 "영속성 Adapter 컨벤션",
 * `docs/architecture/persistence-jooq.md`)을 강제한다.
 *
 * 가장 중요한 건 첫 번째 규칙이다 — jOOQ가 생성한 Record/Table 클래스가
 * `infra.persistence.jooq` 밖으로 새어 나가는 순간, 도메인이 DB 스키마에 직접 묶인다.
 */
class PersistenceAdapterTest :
	FunSpec({

		test("generated jOOQ code must stay inside the persistence adapter") {
			noClasses()
				.that()
				.resideOutsideOfPackage("${Packages.PERSISTENCE_ADAPTER}..")
				.should()
				.dependOnClassesThat()
				.resideInAnyPackage("${Packages.JOOQ_GENERATED}..")
				.because(
					"생성된 jOOQ Record는 영속성 Adapter 내부에서만 쓰고 domain/application 계층으로 " +
						"새어나가지 않는다(명시적 Mapper로 변환한다)",
				).check(productionClasses)
		}

		test("persistence adapters must be spring beans") {
			classes()
				.that()
				.resideInAPackage("${Packages.PERSISTENCE_ADAPTER}..")
				.and()
				.haveSimpleNameEndingWith("Adapter")
				.should()
				.beAnnotatedWith("org.springframework.stereotype.Repository")
				.orShould()
				.beAnnotatedWith("org.springframework.stereotype.Component")
				.because("Adapter는 앱의 컴포넌트 스캔으로 배선된다 — 앱이 수동으로 조립하지 않는다")
				.check(productionClasses)
		}

		test("persistence adapters must implement an outbound port") {
			classes()
				.that()
				.resideInAPackage("${Packages.PERSISTENCE_ADAPTER}..")
				.and()
				.haveSimpleNameEndingWith("Adapter")
				.should(implementAnOutboundPort)
				.because("Adapter는 application이 선언한 Port의 구현체다 — 그 반대가 아니다")
				.check(productionClasses)
		}
	})

/** `application.port.outbound`의 인터페이스를 실제로 구현하는지 검사하는 조건. */
private val implementAnOutboundPort =
	object : ArchCondition<JavaClass>("application.port.outbound의 Port를 구현해야 한다") {
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
					"${item.name}이(가) 구현하는 Port가 ${Packages.APPLICATION_PORT}에 없다",
				),
			)
		}
	}
