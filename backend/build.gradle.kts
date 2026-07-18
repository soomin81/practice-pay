plugins {
	// `apply false` here — the root project has no Kotlin source of its own, this
	// just puts the Kotlin Gradle plugin's classes on the classpath. Without it,
	// applying org.jlleitschuh.gradle.ktlint to the root project below throws
	// NoClassDefFoundError on org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
	// — the ktlint plugin unconditionally touches that class on apply, even for a
	// project that never applies kotlin("jvm") itself.
	//
	// 버전은 `gradle/libs.versions.toml`에서 온다 — 메인 빌드의 build.gradle.kts는
	// 카탈로그 접근자를 정상적으로 쓸 수 있다(쓰지 못하는 건 build-logic의
	// Precompiled Script Plugin뿐이다, backend/CLAUDE.md의 "build-logic" 절 참고).
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.ktlint) apply false
}

group = "paytech"
version = "0.0.1-SNAPSHOT"

// Applied to every project (root + all subprojects, including the phantom
// `:modules` parent Gradle creates for the `modules:domain`/`modules:application`
// hierarchical includes) uniformly — ktlint config never varies per module, so
// this is the one exception to this project's otherwise-deliberate "duplicate
// small plugin blocks per module" style. `:modules` has no build.gradle.kts of
// its own, so it needs `repositories` here too or ktlint's own dependency
// resolution fails on it.
//
// The root project itself has no Kotlin source of its own — it's a pure
// multi-module aggregator now that the four apps under `apps/` are the real
// deployables (see backend/CLAUDE.md). It doesn't apply `kotlin("jvm")`; the
// ktlint plugin still applies fine without it (it just skips the
// main/test-sourceSet ktlint tasks here and only lints this file itself,
// exactly like it already does for the source-less `:modules` project).
allprojects {
	apply(plugin = "org.jlleitschuh.gradle.ktlint")

	repositories {
		mavenCentral()
	}
}
