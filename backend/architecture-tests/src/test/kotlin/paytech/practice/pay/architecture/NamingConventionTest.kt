package paytech.practice.pay.architecture

import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import io.kotest.core.spec.style.FunSpec

/**
 * "이름이 X면 자리도 정해져 있다"는 컨벤션을 강제한다.
 *
 * 의존 방향(`HexagonalLayerTest`)만 지켜도 아키텍처는 성립하지만, 같은 역할의 클래스가
 * 앱마다 다른 자리에 생기기 시작하면 `backend/CLAUDE.md`가 설명하는 패키지 구조
 * (`web`/`config`/`support`, 애그리게이트별 서브패키지)를 읽는 것만으로 코드를 찾을 수
 * 없게 된다 — 그 구조를 규칙으로 고정한다.
 */
class NamingConventionTest :
	FunSpec({

		test("use cases must live in the application module") {
			classes()
				.that()
				.haveSimpleNameEndingWith("UseCase")
				.should()
				.resideInAPackage("${Packages.APPLICATION}..")
				.because("Use Case는 `modules:application`의 것이다 — 앱이나 Adapter에 두지 않는다")
				.check(productionClasses)
		}

		test("repository adapters must live in the jooq persistence package") {
			classes()
				.that()
				.haveSimpleNameEndingWith("RepositoryAdapter")
				.should()
				.resideInAPackage("${Packages.PERSISTENCE_ADAPTER}..")
				.because("애그리게이트를 저장·복원하는 Repository 구현은 전부 한자리에 모인다")
				.check(productionClasses)
		}

		// `..web..`은 세 API 앱의 inbound Adapter 자리다(`api.<app>.web`) — 컨트롤러와
		// 요청·응답 DTO, `@RestControllerAdvice` 예외 핸들러가 여기 있다.
		test("spring web endpoints must live in a web package") {
			classes()
				.that()
				.areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
				.or()
				.areAnnotatedWith("org.springframework.web.bind.annotation.RestControllerAdvice")
				.should()
				.resideInAPackage("${Packages.API_APPS}..web..")
				.because("HTTP를 아는 코드는 inbound Adapter 패키지에만 있다")
				.check(productionClasses)
		}

		test("controllers must be annotated as rest controllers") {
			classes()
				.that()
				.haveSimpleNameEndingWith("Controller")
				.should()
				.beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
				.because("이 프로젝트의 컨트롤러는 전부 JSON API다 — 뷰를 렌더링하는 컨트롤러는 없다")
				.check(productionClasses)
		}
	})
