// Test-only module: no src/main, just ArchUnit rules run against the compiled
// classes of other modules (pulled in below as testImplementation deps).
plugins {
	kotlin("jvm") version "2.3.21"
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	testImplementation(project(":modules:domain"))

	testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
	testImplementation("io.kotest:kotest-assertions-core:5.9.1")
	testImplementation("com.tngtech.archunit:archunit:1.4.1")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
