package app.halal.bootstrap

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringTestExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate

/**
 * Skeleton boot smoke test: proves the assembled Spring Boot application boots
 * (blocking + Virtual Threads) and serves the `/v1/health` endpoint used to
 * check liveness and to seed the OpenAPI spec.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest : FunSpec() {

    override fun extensions() = listOf(SpringTestExtension())

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