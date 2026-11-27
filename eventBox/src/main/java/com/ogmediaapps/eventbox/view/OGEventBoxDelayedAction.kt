package com.ogmediaapps.eventbox.view

import android.view.View

/**
 * A [Runnable] tied to the [view] it is posted on, so scheduling and cancelling it are one-liners
 * and a cancel can never target a different runnable than the one that was scheduled.
 */
internal class OGEventBoxDelayedAction(private val view: View, private val action: () -> Unit) {
    private val runnable = Runnable { action() }

    /** Posts the action after [delayMs]. Does not cancel an earlier post; call [cancel] first if needed. */
    fun schedule(delayMs: Long) {
        view.postDelayed(runnable, delayMs)
    }

    fun cancel() {
        view.removeCallbacks(runnable)
    }

    /** Runs the action now, on the caller's thread. */
    fun run() = action()
}
