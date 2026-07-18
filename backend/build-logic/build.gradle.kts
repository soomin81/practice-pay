// `backend/settings.gradle.kts`가 `pluginManagement { includeBuild("build-logic") }`로
// 끌어오는 포함된 빌드(Included Build)다 — `buildSrc`가 아니라 이 방식을 쓴 이유는
// `buildSrc`는 그 자체가 루트 빌드의 일부라 바뀔 때마다 루트 빌드 전체를 무효화시키지만,
// 포함된 빌드는 독립된 빌드라 그 캐시 이점이 그대로 유지되기 때문이다.
//
// 여기서 선언한 `implementation` 의존성은 `src/main/kotlin/*.gradle.kts`의 Precompiled
// Script Plugin들이 각자 `plugins { id("org.jetbrains.kotlin.jvm") }`처럼 버전 없이
// 플러그인을 걸 수 있게 classpath에 실제 플러그인 아티팩트를 올려주는 역할이다 — 버전은
// 여기 한 곳(`libs.versions.toml` 경유)에서만 정한다.
plugins {
	`kotlin-dsl`
}

repositories {
	mavenCentral()
	gradlePluginPortal()
}

dependencies {
	implementation(libs.kotlin.gradlePlugin)
	implementation(libs.kotlin.allopen)
	implementation(libs.springBoot.gradlePlugin)
	implementation(libs.springDependencyManagement.gradlePlugin)
}
