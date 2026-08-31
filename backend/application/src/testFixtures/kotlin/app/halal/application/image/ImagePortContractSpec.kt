package app.halal.application.image

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

/**
 * The [ImagePort] contract, shared as a test fixture so the SAME assertions run
 * against the in-memory fake AND the real MinIO adapter (docs/design/sc-157-image-variants.md
 * §"Contract (swap) test"). An "adapter swap test" passes when any ImagePort
 * implementation satisfies this spec unmodified.
 *
 * Each scenario is exercised on an isolated listing id so parallel adapters
 * cannot cross-contaminate.
 */
abstract class ImagePortContractSpec(private val port: ImagePort) : FunSpec() {

    init {
        val listingA = UUID.randomUUID()
        val listingB = UUID.randomUUID()

        test("save then load round-trips bytes and contentType exactly") {
            val bytes = "hero-image-data".toByteArray()
            port.save(listingA, ImageVariant.THUMBNAIL, "image/jpeg", bytes)

            port.load(listingA, ImageVariant.THUMBNAIL).shouldNotBeNull().let { stored ->
                stored.contentType shouldBe "image/jpeg"
                stored.bytes.contentEquals(bytes) shouldBe true
            }
        }

        test("load of an unsaved (listing, variant) returns null") {
            port.load(listingB, ImageVariant.FULL) shouldBe null
            port.load(listingB, ImageVariant.THUMBNAIL) shouldBe null
        }

        test("variants are independent for the same listing") {
            port.save(listingA, ImageVariant.FULL, "image/png", "full".toByteArray())
            port.save(listingA, ImageVariant.THUMBNAIL, "image/jpeg", "thumb".toByteArray())

            port.load(listingA, ImageVariant.FULL)!!.bytes.contentEquals("full".toByteArray()) shouldBe true
            // THUMBNAIL write must not have clobbered FULL, and vice versa.
            port.load(listingA, ImageVariant.THUMBNAIL)!!.bytes.contentEquals("thumb".toByteArray()) shouldBe true
        }

        test("listings are isolated: writing to one never affects another") {
            port.save(listingA, ImageVariant.FULL, "image/jpeg", "listA-only".toByteArray())

            port.load(listingB, ImageVariant.FULL) shouldBe null
        }

        test("overwrite is last-write-wins (same listing + variant)") {
            port.save(listingA, ImageVariant.THUMBNAIL, "image/jpeg", "v1".toByteArray())
            port.save(listingA, ImageVariant.THUMBNAIL, "image/png", "v2".toByteArray())

            val stored = port.load(listingA, ImageVariant.THUMBNAIL).shouldNotBeNull()
            stored.contentType shouldBe "image/png"
            stored.bytes.contentEquals("v2".toByteArray()) shouldBe true
        }
    }
}