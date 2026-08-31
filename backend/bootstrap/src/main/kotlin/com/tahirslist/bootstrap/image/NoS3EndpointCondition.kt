package com.tahirslist.bootstrap.image

import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotatedTypeMetadata

/**
 * True when no S3 object-store endpoint is configured (sc-157).
 *
 * The S3 adapter ([com.tahirslist.storage.s3.S3ImagePortConfig]) produces its
 * `imagePort` bean only when `app.storage.s3.endpoint` is set. The InMemory
 * fallback here must be the exact complement, *regardless of configuration
 * processing order* — a bare `@ConditionalOnMissingBean` alone is order-dependent
 * and collides with the adapter bean by name when both activate in a full boot
 * (BeanDefinitionOverrideException). Gate the fallback on absence of the same
 * property the adapter is gated on, so at most one `imagePort` is ever defined.
 */
class NoS3EndpointCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean =
        context.environment.getProperty("app.storage.s3.endpoint").isNullOrBlank()
}