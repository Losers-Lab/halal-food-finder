package com.tahirslist.application.account

import java.time.Duration

/**
 * Shared session lifetime policy (ratified: short access JWT; refresh ~30 days).
 */
object SessionLifetimes {
    /** Refresh tokens live ~30 days and are rotated (single-use) on each refresh. */
    val REFRESH_LIFETIME: Duration = Duration.ofDays(30)
}