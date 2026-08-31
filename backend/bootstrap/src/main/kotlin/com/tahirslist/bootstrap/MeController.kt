package com.tahirslist.bootstrap

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * sc-131 representative protected route. Returns the identity carried by the
 * VERIFIED access JWT (account id as `sub`, plus the RBAC `role` claim). This is
 * the minimal authenticated endpoint that proves the deny-by-default resource
 * server: without a valid token the request is rejected with a generic 401
 * before it reaches here.
 */
@RestController
@RequestMapping("/v1/me")
class MeController {

    @GetMapping
    fun me(authentication: JwtAuthenticationToken): Map<String, Any> {
        val jwt = authentication.token
        return mapOf(
            "subjectId" to jwt.subject,
            "role" to jwt.getClaimAsString("role"),
        )
    }
}