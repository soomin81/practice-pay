package paytech.practice.pay.api.merchant

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class MerchantApiApplicationTests :
	FunSpec({

		extensions(SpringExtension)

		test("contextLoads") {
		}
	})
