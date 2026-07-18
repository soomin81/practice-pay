plugins {
	id("practicepay.kotlin-common")
	id("practicepay.kotest")
	id("practicepay.mockk")
}

dependencies {
	implementation(project(":modules:domain"))
}
