package paytech.practice.pay

import org.springframework.boot.fromApplication
import org.springframework.boot.with

fun main(args: Array<String>) {
	fromApplication<PracticePayApplication>().with(TestcontainersConfiguration::class).run(*args)
}
