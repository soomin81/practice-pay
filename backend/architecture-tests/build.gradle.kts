// Test-only module: no src/main, just ArchUnit rules run against the compiled
// classes of other modules (pulled in below as testImplementation deps).
plugins {
	id("practicepay.kotlin-common")
	id("practicepay.kotest")
}

dependencies {
	testImplementation(project(":modules:domain"))

	testImplementation(libs.archunit)
}
