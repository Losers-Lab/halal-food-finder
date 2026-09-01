// users (V1), refresh_tokens (V2), the sc-136 token-family
            // columns on refresh_tokens (V3), restaurant_listings (V4),
            // brands (V5), the seed-listing schema (V6), the seed data
            // (V7), the sc-10 search projection list (V8), the sc-43/44
            // price + multi-cuisine columns (V9), the sc-45/72 rating
            // column (V10), the sc-118 alcohol_served column (V11), the
            // sc-50/51/52 favorites table (V12), the sc-46
            // halal_certification_reviews table (V13), the sc-120
            // ai_consent_at column on reviews (V14), and the sc-73
            // VERIFIED-status opening on restaurant_listings (V15), the
            // sc-73 read-surface certifier + expires_on columns on reviews
            // (V16), the sc-42 hand-cut boolean (V17), and the sc-119
            // partial-halal columns + restaurant_halal_items (V18) all run
            // cleanly on a real PostGIS DB.
            result.migrationsExecuted shouldBe 18