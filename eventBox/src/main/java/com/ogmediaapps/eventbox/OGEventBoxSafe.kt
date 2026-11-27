package com.ogmediaapps.eventbox

/**
 * The SDK must never crash the host app, so risky framework calls (window manager, preferences,
 * host `toString()`, ...) are wrapped. These helpers keep that policy in one place. Both catch
 * [Throwable], exactly like `runCatching`, and are inline so `return` inside the block still works.
 */

/** Runs [block] and ignores any failure. */
internal inline fun safely(block: () -> Unit) {
    try {
        block()
    } catch (_: Throwable) {
    }
}

/** Runs [block] and returns its result, or [fallback] if it throws. */
internal inline fun <T> safeOr(fallback: T, block: () -> T): T =
    try {
        block()
    } catch (_: Throwable) {
        fallback
    }
