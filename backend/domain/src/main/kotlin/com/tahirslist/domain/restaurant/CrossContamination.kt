package com.tahirslist.domain.restaurant

/**
 * Cross-contamination is a HARD index gate (founder sc-119 re-scope #3): if it
 * occurs, or may/uncertain occur, the restaurant is NOT included in our index.
 *
 * Only [NO_CROSS_CONTAMINATION] qualifies a listing for the search index
 * ([isIndexQualified]); [PRESENT] (occurs) and [UNCERTAIN] (may/uncertain —
 * the conservative unknown default) are excluded from `listing_search`.
 */
enum class CrossContamination {
    /** Declared/presented no cross-contamination — the only index-qualified state. */
    NO_CROSS_CONTAMINATION,

    /** Cross-contamination occurs — excluded from the index. */
    PRESENT,

    /** May occur / uncertain — excluded from the index (conservative default). */
    UNCERTAIN,
    ;

    /** Only a no-cross-contamination listing belongs in the search index. */
    fun isIndexQualified(): Boolean = this == NO_CROSS_CONTAMINATION

    companion object {
        /** Conservative default: exempt until a no-cross-contamination qualification. */
        val DEFAULT: CrossContamination = UNCERTAIN
    }
}