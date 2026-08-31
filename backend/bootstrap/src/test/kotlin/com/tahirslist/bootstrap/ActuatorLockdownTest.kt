package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus

/**
 * Security lockdown of the Spring Boot actuator surface:
 *
 * - Only `/actuator/health` is exposed over the web; every other actuator
 *   endpoint (env, beans, mappings, configprops, ...) must be off.
 * - Health details are never rendered (`show-details: never`), so even the
 *   anonymous `/actuator/health` response cannot leak component-level
 *   information (DB, disk, etc.) to unauthenticated callers.
 */
class ActuatorLockdownTest : PostgresBootTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    init {
        test("GET /actuator/health is exposed but renders no details") {
            val resp = restTemplate.getForEntity("/actuator/health", String::class.java)

            resp.statusCode.is2xxSuccessful shouldBe true
            // show-details: never — only the aggregate status, nothing else.
            resp.body shouldBe """{"status":"UP"}"""
        }

        test("non-health actuator endpoints are not exposed") {
            for (path in listOf("info", "env", "beans", "mappings", "configprops", "metrics", "loggers")) {
                val resp = restTemplate.getForEntity("/actuator/$path", String::class.java)
                // 404 = endpoint not exposed (permitAll'd paths); 401 = denied by
                // the security chain (deny-by-default). Both reveal nothing —
                // the only forbidden outcome is 2xx with actuator payload.
                (resp.statusCode == HttpStatus.NOT_FOUND || resp.statusCode == HttpStatus.UNAUTHORIZED) shouldBe true
            }
        }
    }
}
