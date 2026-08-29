package app.halal.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["app.halal"])
class HalalFoodFinderApplication

fun main(args: Array<String>) {
    runApplication<HalalFoodFinderApplication>(*args)
}