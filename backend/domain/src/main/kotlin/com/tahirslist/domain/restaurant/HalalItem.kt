package com.tahirslist.domain.restaurant

/**
 * A single named menu item/section and whether it is halal. Models the founder
 * sc-119 directive to record *which* meat/items are halal per listing, rather
 * than a single full/partial bit.
 *
 * The stored [name] is trimmed (via the [invoke] factory) and must not be
 * blank. Equality is structural on the trimmed name + halal flag, so a
 * "  chicken  " and "chicken" are the same item.
 */
data class HalalItem private constructor(
    val name: String,
    val isHalal: Boolean,
) {
    init {
        require(name.isNotBlank()) { "Item name must not be blank." }
    }

    companion object {
        /** Trim the item name before constructing; the stored [name] is trimmed. */
        operator fun invoke(name: String, isHalal: Boolean): HalalItem =
            HalalItem(name.trim(), isHalal)
    }
}