package com.tahirslist.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.tahirslist"])
class HalalFoodFinderApplication

fun main(args: Array<String>) {
    runApplication<HalalFoodFinderApplication>(*args)
}