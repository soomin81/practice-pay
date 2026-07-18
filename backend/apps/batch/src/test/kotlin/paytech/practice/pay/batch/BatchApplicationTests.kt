package paytech.practice.pay.batch

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class BatchApplicationTests :
	FunSpec({

		extensions(SpringExtension)

		test("contextLoads") {
		}
	})
