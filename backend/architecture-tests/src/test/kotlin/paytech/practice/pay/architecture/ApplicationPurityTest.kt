package paytech.practice.pay.architecture

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.core.spec.style.FunSpec

/**
 * `modules:application`도 도메인과 같은 순수성 규칙을 지킨다는 것을 강제한다
 * (`backend/CLAUDE.md`의 "애플리케이션 계층 컨벤션": outbound Port는 "Spring/jOOQ
 * 의존성이 없다는 점에서 한 계층 위의 도메인 순수성 규칙과 같다").
 *
 * 이 계층이 Spring을 모르기 때문에 Use Case에 `@Component`를 달 수 없고, 그래서 각 앱의
 * `UseCaseConfiguration`이 `@Bean` 메서드로 Use Case를 조립한다 — 이 규칙이 깨지면 그
 * 설계 자체가 무너진다. `@Transactional` 대신 `TransactionManager` Port가 있는 것도
 * 같은 이유다.
 */
class ApplicationPurityTest :
	FunSpec({

		test("application must not depend on frameworks or infrastructure libraries") {
			noClasses()
				.that()
				.resideInAPackage("${Packages.APPLICATION}..")
				.should()
				.dependOnClassesThat()
				.resideInAnyPackage(*FORBIDDEN_IN_PURE_LAYERS)
				.because("Use Case는 어떤 프레임워크가 자신을 실행하는지 알지 못한다")
				.check(productionClasses)
		}

		test("application must not use persistence-specific date types") {
			noClasses()
				.that()
				.resideInAPackage("${Packages.APPLICATION}..")
				.should()
				.dependOnClassesThat()
				.haveFullyQualifiedName("java.time.LocalDateTime")
				.orShould()
				.dependOnClassesThat()
				.haveFullyQualifiedName("java.util.Date")
				.because("도메인과 같은 이유로 시각은 항상 UTC `Instant`다(DomainPurityTest 참고)")
				.check(productionClasses)
		}

		// backend/CLAUDE.md: "Outbound Port는 application.port.outbound에 순수 Kotlin
		// 인터페이스로 둔다". Port 패키지에는 Port가 주고받는 데이터 클래스(`OnChainTransaction`,
		// `MarketRateQuote`)와 예외도 함께 있어서, 규칙은 Repository Port로 좁혀 건다.
		test("outbound repository ports must be interfaces") {
			classes()
				.that()
				.resideInAPackage("${Packages.APPLICATION_PORT}..")
				.and()
				.haveSimpleNameEndingWith("Repository")
				.should()
				.beInterfaces()
				.because("Repository Port는 Adapter가 구현할 계약이지 구현이 아니다")
				.check(productionClasses)
		}

		// Use Case가 Port 인터페이스가 아니라 다른 Use Case를 직접 호출하기 시작하면
		// 트랜잭션 경계와 재사용 단위가 흐려진다 — AcceptAccountInvitationUseCase처럼
		// 공용 Use Case가 필요하면 여러 앱이 각자 호출하지, Use Case끼리 부르지 않는다.
		test("use cases must not call other use cases") {
			noClasses()
				.that()
				.haveSimpleNameEndingWith("UseCase")
				.should()
				.dependOnClassesThat()
				.haveSimpleNameEndingWith("UseCase")
				.because("Use Case 사이의 재사용은 Port나 도메인을 통해 이뤄진다")
				.check(productionClasses)
		}
	})
