package paytech.practice.pay.api.admin

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class AdminApiApplicationTests :
	FunSpec({

		extensions(SpringExtension)

		test("contextLoads") {
		}
	})
