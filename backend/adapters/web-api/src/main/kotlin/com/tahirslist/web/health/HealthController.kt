package com.tahirslist.web.health

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Liveness probe for the assembled application. Used both to seed the committed
 * OpenAPI spec and by orchestrators to confirm the web adapter is up.
 */
@RestController
@RequestMapping("/v1/health")
class HealthController {

    @GetMapping
    fun health(): Map<String, String> = mapOf("status" to "ok")
}