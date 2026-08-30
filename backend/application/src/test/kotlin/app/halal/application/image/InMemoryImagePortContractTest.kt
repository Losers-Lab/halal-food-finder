package app.halal.application.image

import java.util.UUID

/**
 * In-memory swap proof for the [ImagePort] contract: the SAME
 * [ImagePortContractSpec] that runs against the MinIO-backed adapter also passes
 * against this fake. If an adapter changes and the contract breaks, BOTH tests
 * fail together — that is the "adapter swap test".
 */
class InMemoryImagePortContractTest : ImagePortContractSpec(InMemoryImagePort())