package com.tahirslist.application.image

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
 * sc-183 adds distinct thumbnail widths; the spec pins that each [ImageVariant]
 * (including each thumbnail width) is an independent storage key.
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
            port.save(listingA, ImageVariant.THUMBNAIL_400, "image/jpeg", bytes)

            port.load(listingA, ImageVariant.THUMBNAIL_400).shouldNotBeNull().let { stored ->
                stored.contentType shouldBe "image/jpeg"
                stored.bytes.contentEquals(bytes) shouldBe true
            }
        }

        test("load of an unsaved (listing, variant) returns null") {
            port.load(listingB, ImageVariant.FULL) shouldBe null
            port.load(listingB, ImageVariant.THUMBNAIL_400) shouldBe null
            port.load(listingB, ImageVariant.THUMBNAIL_768) shouldBe null
        }

        test("variants are independent for the same listing") {
            port.save(listingA, ImageVariant.FULL, "image/png", "full".toByteArray())
            port.save(listingA, ImageVariant.THUMBNAIL_400, "image/jpeg", "thumb".toByteArray())

            port.load(listingA, ImageVariant.FULL)!!.bytes.contentEquals("full".toByteArray()) shouldBe true
            // THUMBNAIL_400 write must not have clobbered FULL, and vice versa.
            port.load(listingA, ImageVariant.THUMBNAIL_400)!!.bytes.contentEquals("thumb".toByteArray()) shouldBe true
        }

        test("distinct thumbnail widths are independent storage keys for the same listing") {
            port.save(listingA, ImageVariant.THUMBNAIL_400, "image/jpeg", "w400".toByteArray())

            // Writing the small width must not have created the wider one.
            port.load(listingA, ImageVariant.THUMBNAIL_768) shouldBe null

            port.save(listingA, ImageVariant.THUMBNAIL_768, "image/jpeg", "w768".toByteArray())
            port.load(listingA, ImageVariant.THUMBNAIL_400)!!.bytes.contentEquals("w400".toByteArray()) shouldBe true
            port.load(listingA, ImageVariant.THUMBNAIL_768)!!.bytes.contentEquals("w768".toByteArray()) shouldBe true
        }

        test("listings are isolated: writing to one never affects another") {
            port.save(listingA, ImageVariant.FULL, "image/jpeg", "listA-only".toByteArray())

            port.load(listingB, ImageVariant.FULL) shouldBe null
        }

        test("overwrite is last-write-wins (same listing + variant)") {
            port.save(listingA, ImageVariant.THUMBNAIL_400, "image/jpeg", "v1".toByteArray())
            port.save(listingA, ImageVariant.THUMBNAIL_400, "image/png", "v2".toByteArray())

            val stored = port.load(listingA, ImageVariant.THUMBNAIL_400).shouldNotBeNull()
            stored.contentType shouldBe "image/png"
            stored.bytes.contentEquals("v2".toByteArray()) shouldBe true
        }

        test("delete removes the stored variant (owner remove-image path, sc-53/54)") {
            port.save(listingA, ImageVariant.FULL, "image/jpeg", "hero".toByteArray())

            port.load(listingA, ImageVariant.FULL).shouldNotBeNull()
            port.delete(listingA, ImageVariant.FULL)

            port.load(listingA, ImageVariant.FULL) shouldBe null
        }

        test("delete of an unsaved variant is a silent no-op") {
            // Idempotent remove: deleting nothing must not throw, and load stays null.
            port.delete(listingB, ImageVariant.FULL)
            port.load(listingB, ImageVariant.FULL) shouldBe null
        }

        test("delete of one variant leaves the others intact") {
            port.save(listingA, ImageVariant.FULL, "image/jpeg", "full".toByteArray())
            port.save(listingA, ImageVariant.THUMBNAIL_400, "image/jpeg", "thumb".toByteArray())

            port.delete(listingA, ImageVariant.FULL)

            port.load(listingA, ImageVariant.FULL) shouldBe null
            port.load(listingA, ImageVariant.THUMBNAIL_400)!!.bytes.contentEquals("thumb".toByteArray()) shouldBe true
        }
    }
}