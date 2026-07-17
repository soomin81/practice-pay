package paytech.practice.pay.api.merchant

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class MerchantApiApplicationTests :
	FunSpec({

		extensions(SpringExtension)

		test("contextLoads") {
		}
	})
