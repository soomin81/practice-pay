// infra-persistence/infra-blockchain처럼 Spring Bean(`@Component`/`@Repository`)을
// 담은 라이브러리 모듈에 적용한다. `kotlin("plugin.spring")`이 필요한 이유는 Spring
// Boot가 인터페이스를 구현한 Bean이라도 기본적으로 CGLIB(서브클래싱) 프록시를 쓰는데
// (`spring.aop.proxy-target-class=true`), Kotlin 클래스는 기본이 `final`이라 그대로
// 두면 `Cannot subclass final class ...`로 죽기 때문이다 — 이 플러그인이 `@Component`
// (메타 애노테이션까지 인식)가 붙은 클래스를 자동으로 `open`으로 만들어준다.
// db-core는 `@Component` Bean이 없어서 이 플러그인이 아니라 practicepay.spring-bom만
// 적용한다.
plugins {
	id("practicepay.kotlin-common")
	id("practicepay.spring-bom")
	id("org.jetbrains.kotlin.plugin.spring")
}
