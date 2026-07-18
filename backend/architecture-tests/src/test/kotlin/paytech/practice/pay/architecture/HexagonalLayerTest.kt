package paytech.practice.pay.architecture

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan

/**
 * 헥사고날 아키텍처의 **의존 방향**을 강제한다
 * (`backend/CLAUDE.md`의 Architecture 절: `inbound adapter → application → domain
 * ← outbound port ← outbound adapter`).
 *
 * 핵심은 "안쪽은 바깥쪽을 모른다"이다 — `domain`은 아무도 모르고, `application`은
 * 자신이 선언한 Port의 구현체(`modules:infra-*`)를 모르며, inbound Adapter(`apps:*`)와
 * outbound Adapter(`modules:infra-*`)는 서로를 모른다.
 *
 * inbound/outbound Adapter가 서로를 모른다는 규칙이 성립하는 이유는 이 프로젝트의
 * 앱들이 Adapter를 타입으로 직접 참조하지 않고 `@SpringBootApplication(scanBasePackages = ...)`
 * 문자열로 컴포넌트 스캔해서 배선하기 때문이다(`backend/CLAUDE.md`의 Apps 절).
 * 어떤 앱이 Adapter 클래스를 직접 import해야 하는 상황이 실제로 생기면 이 규칙이 먼저
 * 깨진다 — 그때는 "Port를 우회하고 있는 건 아닌지"를 먼저 확인하고, 의도한 배선이라면
 * 아래 `mayNotBeAccessedByAnyLayer()`를 `mayOnlyBeAccessedByLayers(...)`로 완화한다.
 */
class HexagonalLayerTest :
	FunSpec({

		val domain = "Domain"
		val application = "Application"
		val outboundAdapter = "Outbound Adapter"
		val inboundAdapter = "Inbound Adapter"

		test("hexagonal layers must only depend inwards") {
			layeredArchitecture()
				// 프레임워크/JDK 의존은 이 규칙의 관심사가 아니다(계층 순수성은 *PurityTest가 본다).
				.consideringOnlyDependenciesInAnyPackage("paytech.practice.pay..")
				.layer(domain)
				.definedBy("${Packages.DOMAIN}..")
				.layer(application)
				.definedBy("${Packages.APPLICATION}..")
				.layer(outboundAdapter)
				.definedBy("${Packages.INFRA}..")
				.layer(inboundAdapter)
				.definedBy("${Packages.API_APPS}..", "${Packages.BATCH_APP}..")
				.whereLayer(inboundAdapter)
				.mayNotBeAccessedByAnyLayer()
				.whereLayer(outboundAdapter)
				.mayNotBeAccessedByAnyLayer()
				.whereLayer(application)
				.mayOnlyBeAccessedByLayers(inboundAdapter, outboundAdapter)
				.whereLayer(domain)
				.mayOnlyBeAccessedByLayers(application, inboundAdapter, outboundAdapter)
				.check(productionClasses)
		}

		test("domain must not depend on any outer layer") {
			noClasses()
				.that()
				.resideInAPackage("${Packages.DOMAIN}..")
				.should()
				.dependOnClassesThat()
				.resideInAnyPackage(
					"${Packages.APPLICATION}..",
					"${Packages.INFRA}..",
					"${Packages.API_APPS}..",
					"${Packages.BATCH_APP}..",
				).because("도메인은 자신을 쓰는 어떤 계층도 알지 못한다")
				.check(productionClasses)
		}

		test("application must not depend on its own port implementations") {
			noClasses()
				.that()
				.resideInAPackage("${Packages.APPLICATION}..")
				.should()
				.dependOnClassesThat()
				.resideInAnyPackage(
					"${Packages.INFRA}..",
					"${Packages.API_APPS}..",
					"${Packages.BATCH_APP}..",
				).because("Adapter가 Port를 구현하지, Port가 Adapter를 알지 않는다(의존성 역전)")
				.check(productionClasses)
		}

		// ArchUnit 규칙은 대상 클래스가 하나도 없어도 "통과"한다 — 모듈이 빠지거나 패키지가
		// 바뀌어서 규칙 전체가 조용히 무력화되는 걸 막는 가드다(Packages의 KDoc 참고).
		test("every layer must actually be imported") {
			listOf(
				Packages.DOMAIN,
				Packages.APPLICATION,
				Packages.INFRA,
				Packages.API_APPS,
				Packages.BATCH_APP,
			).forEach { packagePrefix ->
				withClue("$packagePrefix 아래 클래스가 하나도 임포트되지 않았다 — architecture-tests의 의존성을 확인한다") {
					productionClasses.count { it.packageName.startsWith(packagePrefix) } shouldBeGreaterThan 0
				}
			}
		}
	})
