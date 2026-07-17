package paytech.practice.pay.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.core.spec.style.FunSpec

/**
 * `modules/domain`이 Spring과 jOOQ에 의존하지 않는다는 규칙
 * (`backend/CLAUDE.md`의 Architecture 섹션, `docs/domain/domain-model.md`의
 * "도메인은 Spring과 jOOQ에 의존하지 않는다")을 강제한다.
 *
 * 같은 규칙이 HTTP Client·블록체인 SDK 의존도 금지하지만, 이 프로젝트 어디에도
 * 아직 그런 라이브러리가 없어서(검증할 실제 패키지 prefix가 없어서) 지금은
 * 확인하지 않는다 — 실제로 도입되면 이 테스트에 패키지를 추가한다.
 */
class DomainPurityTest : FunSpec({

	val domainClasses = ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("paytech.practice.pay.domain")

	test("domain must not depend on Spring or jOOQ") {
		noClasses()
			.that().resideInAPackage("paytech.practice.pay.domain..")
			.should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "org.jooq..")
			.check(domainClasses)
	}
})
