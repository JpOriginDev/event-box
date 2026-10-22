package com.ogmediaapps.eventbox

import android.os.Bundle

/**
 * Drop-in wrapper that sends each event to your real analytics SDK and to Event Box in one call,
 * so call sites no longer log twice. It has no analytics dependency of its own: you pass the
 * calls to forward to (Firebase, Amplitude, Mixpanel, ...).
 *
 * ```
 * val analytics = OGEventBoxAnalytics.wrap(
 *     eventSink = { name, params -> firebaseAnalytics.logEvent(name, params) },
 *     userPropertySink = { name, value -> firebaseAnalytics.setUserProperty(name, value) }
 * )
 * analytics.logEvent("purchase", bundle)
 * ```
 */
class OGEventBoxAnalytics private constructor(
    private val eventSink: EventSink,
    private val userPropertySink: UserPropertySink?
) {

    /** Forwards an event to your analytics SDK. */
    fun interface EventSink {
        fun logEvent(name: String, params: Bundle?)
    }

    /** Forwards a user property to your analytics SDK. */
    fun interface UserPropertySink {
        fun setUserProperty(name: String, value: String?)
    }

    /** Logs an event to Event Box, then to your analytics SDK. */
    fun logEvent(name: String) = logEvent(name, null as Bundle?)

    /** Logs an event with [params] to Event Box, then to your analytics SDK. */
    fun logEvent(name: String, params: Bundle?) {
        // Event Box never throws, so it goes first: a failing SDK call cannot hide the event.
        OGEventBoxLog.log(name, params)
        eventSink.logEvent(name, params)
    }

    /** Records a user property in Event Box, then in your analytics SDK. */
    fun setUserProperty(name: String, value: String?) {
        OGEventBoxLog.setUserProperty(name, value)
        userPropertySink?.setUserProperty(name, value)
    }

    companion object {
        /**
         * Creates a wrapper. [userPropertySink] is optional; without it user properties are
         * recorded in Event Box only.
         */
        @JvmStatic
        @JvmOverloads
        fun wrap(
            eventSink: EventSink,
            userPropertySink: UserPropertySink? = null
        ): OGEventBoxAnalytics = OGEventBoxAnalytics(eventSink, userPropertySink)
    }
}
