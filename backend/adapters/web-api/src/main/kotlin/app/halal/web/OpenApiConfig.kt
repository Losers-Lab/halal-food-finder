package app.halal.web

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Code-first OpenAPI metadata. springdoc discovers controllers, and this bean
 * fixes the API's stable identity so the committed `/v1` spec carries the real
 * title/version instead of springdoc's defaults.
 */
@Configuration
class OpenApiConfig {

    @Bean
    fun hffOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Halal Food Finder API")
                .description("HTTP API for Halal Food Finder. Contract lives at openapi/v1.json (OpenAPI 3.1).")
                .version("v1"),
        )
}