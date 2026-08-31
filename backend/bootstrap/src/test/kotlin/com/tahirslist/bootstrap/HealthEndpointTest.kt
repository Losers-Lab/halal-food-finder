package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate

/**
 * Boot smoke test: proves the assembled Spring Boot application boots
 * (blocking + Virtual Threads + a live PostGIS datasource + Flyway migration)
 * and serves the `/v1/health` endpoint.
 */
class HealthEndpointTest : PostgresBootTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    init {
        test("GET /v1/health returns 200 with status ok") {
            val resp = restTemplate.getForEntity("/v1/health", String::class.java)

            resp.statusCode.is2xxSuccessful shouldBe true
            resp.body shouldBe """{"status":"ok"}"""
        }
    }
}