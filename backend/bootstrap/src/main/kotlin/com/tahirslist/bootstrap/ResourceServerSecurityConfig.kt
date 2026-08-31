package com.tahirslist.bootstrap

import com.tahirslist.domain.account.Role
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import java.security.KeyPair
import java.security.interfaces.RSAPublicKey

/**
 * sc-131 deny-by-default resource server.
 *
 * Every request must present a valid RS256 access JWT (verified server-side
 * against the same RSA pair that issued it) EXCEPT the explicitly-permitted
 * public routes (signup / login / refresh / logout, health, OpenAPI docs,
 * actuator). Anything else is denied with a generic 401 until a valid token
 * is supplied.
 *
 * Verification (per docs/security/auth-security-review-2026-08-29.md finding #1
 * recommendation): signature (RS256), `iss`, `exp`, and the `role` claim (must
 * be present and one of the six MVP roles). An expired / tampered /
 * wrong-issuer / missing-role JWT fails verification and is rejected as 401.
 * Replies are generic and never leak the reason. No secrets in code.
 */
@Configuration
@EnableWebSecurity
class ResourceServerSecurityConfig {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
        objectMapper: ObjectMapper,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .headers { headers ->
                headers.contentTypeOptions { }
                headers.frameOptions { it.deny() }
                // CSP: this API serves no HTML frames/pages; deny embedding outright.
                headers.contentSecurityPolicy("default-src 'none'; frame-ancestors 'none'")
            }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(HttpMethod.POST, "/v1/auth/signup", "/v1/auth/login", "/v1/auth/refresh", "/v1/auth/logout").permitAll()
                    // `/error` is Spring MVC's forwarded error dispatch (e.g. for
                    // bean-validation 400s); it must stay public so those replies surface.
                    .requestMatchers("/error").permitAll()
                    .requestMatchers("/v1/health", "/v1/api-docs/**", "/v1/swagger-ui/**", "/v3/api-docs/**",
                        "/swagger-ui/**", "/actuator/health", "/actuator/info").permitAll()
                    // Public read surface (sc-157): search/browse, detail, and the
                    // image same-origin proxy are core unauthenticated UX (an <img>
                    // cannot carry a Bearer header). Deny-by-default still guards all
                    // writes/claims. Posture change flagged for Omar's review.
                    .requestMatchers(HttpMethod.GET, "/v1/listings", "/v1/listings/**").permitAll()
                    // Deny-by-default: any other request requires a valid token.
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2
                    .jwt { it.decoder(jwtDecoder) }
                    .authenticationEntryPoint(GenericUnauthorizedEntryPoint(objectMapper))
            }
        return http.build()
    }

    /**
     * Validates RS256 signature + issuer + exp (createDefaultWithIssuer) and that
     * the token carries a known `role` claim.
     */
    @Bean
    fun jwtDecoder(
        jwtRsaKeyPair: KeyPair,
        @Value("\${app.jwt.issuer:halal-food-finder}") issuer: String,
    ): JwtDecoder {
        val decoder = NimbusJwtDecoder.withPublicKey(jwtRsaKeyPair.public as RSAPublicKey).build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                RoleClaimValidator(),
                org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(issuer),
            ),
        )
        return decoder
    }

    /** Ensures the `role` claim is present and one of the six MVP roles. */
    class RoleClaimValidator : OAuth2TokenValidator<Jwt> {
        override fun validate(jwt: Jwt): OAuth2TokenValidatorResult {
            val role = jwt.getClaim<String>("role")
            return if (role != null && Role.entries.any { it.name == role }) {
                OAuth2TokenValidatorResult.success()
            } else {
                OAuth2TokenValidatorResult.failure(
                    OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "missing or invalid role claim", null),
                )
            }
        }
    }

    /** Returns a generic 401 envelope; never reveals why authentication failed. */
    class GenericUnauthorizedEntryPoint(private val objectMapper: ObjectMapper) : AuthenticationEntryPoint {
        override fun commence(
            request: HttpServletRequest,
            response: HttpServletResponse,
            authException: AuthenticationException,
        ) {
            // The tricky part: expired/tampered/wrong-issuer tokens surface as a
            // JwtValidationException harmlessly here; we always reply generically.
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            objectMapper.writeValue(response.writer, mapOf("code" to "unauthorized", "message" to "Authentication required."))
        }
    }
}