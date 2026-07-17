package paytech.practice.pay

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class PracticePayApplicationTests : FunSpec({

	extensions(SpringExtension)

	test("contextLoads") {
	}

})
