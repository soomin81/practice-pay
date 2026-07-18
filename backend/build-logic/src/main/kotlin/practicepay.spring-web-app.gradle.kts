// api-payment/api-admin/api-merchant 3개 웹 API 앱에 공통인 것만 담는다 —
// practicepay.spring-boot-app 위에 webmvc/security/validation 스타터를 얹는다.
// batch는 웹 앱이 아니라서(spring-boot-starter-web* 없음) 이 플러그인이 아니라
// practicepay.spring-boot-app을 직접 쓴다.
plugins {
	id("practicepay.spring-boot-app")
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")

	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
}
