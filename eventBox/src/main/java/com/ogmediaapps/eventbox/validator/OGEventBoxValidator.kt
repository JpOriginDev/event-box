package com.ogmediaapps.eventbox.validator

import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Validates analytics events and parameters against Firebase Analytics limits and specifications.
 *
 * Firebase Limits:
 * [1] DISTINCT EVENT NAMES: Max 500 distinct custom event types per app.
 * [2] EVENT NAME LENGTH: Max 40 characters (alphanumeric and underscores).
 * [3] PARAMETERS PER EVENT: Max 25 unique parameters per event.
 * [4] PARAMETER NAME LENGTH: Max 40 characters (alphanumeric and underscores).
 * [5] PARAMETER VALUE LENGTH: Max 100 characters (Standard GA, up to 500 for GA360).
 * [6] USER PROPERTIES: Max 25 user properties per app, names <= 24 chars, values <= 36 chars.
 * [7] DAILY EVENT VOLUME: Unlimited total volume; user-rate throttle warning at ~100,000 events/user/day.
 */
object OGEventBoxValidator {

    const val MAX_DISTINCT_EVENTS = 500
    const val MAX_EVENT_NAME_LENGTH = 40
    const val MAX_PARAMETERS_PER_EVENT = 25
    const val MAX_PARAM_NAME_LENGTH = 40
    const val MAX_PARAM_VALUE_LENGTH = 100
    const val MAX_USER_PROPERTIES = 25
    const val MAX_USER_PROPERTY_NAME_LENGTH = 24
    const val MAX_USER_PROPERTY_VALUE_LENGTH = 36
    const val DAILY_EVENT_VOLUME_THROTTLE = 100_000

    private val RESERVED_PREFIXES = listOf("ga_", "google_", "firebase_")

    /**
     * Firebase automatically collected events that do not count against the 500 distinct event limit.
     */
    private val AUTOMATIC_EVENTS = setOf(
        "ad_activeview",
        "ad_click",
        "ad_exposure",
        "ad_impression",
        "ad_query",
        "ad_reward",
        "adunit_exposure",
        "app_clear_data",
        "app_exception",
        "app_remove",
        "app_store_refund",
        "app_store_subscription_cancel",
        "app_store_subscription_convert",
        "app_store_subscription_renew",
        "app_update",
        "error",
        "first_open",
        "first_visit",
        "in_app_purchase",
        "notification_dismiss",
        "notification_foreground",
        "notification_open",
        "notification_receive",
        "os_update",
        "screen_view",
        "session_start",
        "user_engagement"
    )

    private val distinctCustomEvents = ConcurrentHashMap.newKeySet<String>()
    private val distinctUserProperties = ConcurrentHashMap.newKeySet<String>()

    private val dailyEventCount = AtomicInteger(0)
    @Volatile
    private var currentDayEpoch: Long = currentDay()

    private fun currentDay(): Long {
        val cal = Calendar.getInstance()
        return (cal.get(Calendar.YEAR) * 1000L) + cal.get(Calendar.DAY_OF_YEAR)
    }

    private fun checkDayRollover() {
        val today = currentDay()
        if (today != currentDayEpoch) {
            currentDayEpoch = today
            dailyEventCount.set(0)
        }
    }

    /**
     * Validates an event name and optional parameters against Firebase Analytics rules.
     * Returns a list of warning messages, or an empty list if compliant.
     */
    @JvmStatic
    @JvmOverloads
    fun validateEvent(name: String, params: Map<String, Any?>? = null): List<String> {
        val warnings = mutableListOf<String>()
        val trimmedName = name.trim()

        // [7] Daily event volume throttle
        checkDayRollover()
        val currentDailyTotal = dailyEventCount.incrementAndGet()
        if (currentDailyTotal > DAILY_EVENT_VOLUME_THROTTLE) {
            warnings.add("Daily volume limit: $currentDailyTotal events logged today (Firebase rate limit throttles at ~100,000 events/user/day).")
        }

        // [2] Event name length
        if (trimmedName.length > MAX_EVENT_NAME_LENGTH) {
            warnings.add("Event name length (${trimmedName.length} chars) exceeds max limit of $MAX_EVENT_NAME_LENGTH characters. Key event reporting will fail.")
        }

        // Event name format (alphanumeric and underscores, must start with letter, no reserved prefixes)
        if (trimmedName.any { !it.isLetterOrDigit() && it != '_' }) {
            warnings.add("Event name contains invalid characters. Must only contain alphanumeric characters and underscores.")
        } else if (trimmedName.isNotEmpty() && !trimmedName.first().isLetter()) {
            warnings.add("Event name must start with an alphabetic character.")
        }

        if (RESERVED_PREFIXES.any { trimmedName.lowercase(Locale.US).startsWith(it) }) {
            warnings.add("Event name starts with a reserved Firebase prefix ('ga_', 'google_', or 'firebase_').")
        }

        // [1] Distinct event names limit (max 500 custom events)
        val isAutomatic = AUTOMATIC_EVENTS.contains(trimmedName.lowercase(Locale.US))
        if (!isAutomatic) {
            if (!distinctCustomEvents.contains(trimmedName)) {
                if (distinctCustomEvents.size >= MAX_DISTINCT_EVENTS) {
                    warnings.add("Distinct event types limit exceeded: Firebase allows max $MAX_DISTINCT_EVENTS custom event types per app (this is #${distinctCustomEvents.size + 1}).")
                }
                distinctCustomEvents.add(trimmedName)
            }
        }

        // Validate parameters if present
        if (params != null) {
            // [3] Parameters per event
            if (params.size > MAX_PARAMETERS_PER_EVENT) {
                warnings.add("Parameters limit exceeded: Event has ${params.size} parameters (max $MAX_PARAMETERS_PER_EVENT unique parameters per event allowed).")
            }

            for ((key, value) in params) {
                // [4] Parameter name length and character requirements
                if (key.length > MAX_PARAM_NAME_LENGTH) {
                    warnings.add("Parameter '$key' length (${key.length} chars) exceeds max limit of $MAX_PARAM_NAME_LENGTH characters.")
                }
                if (key.any { !it.isLetterOrDigit() && it != '_' }) {
                    warnings.add("Parameter '$key' contains invalid characters. Must only use alphanumeric characters and underscores.")
                } else if (key.isNotEmpty() && !key.first().isLetter()) {
                    warnings.add("Parameter '$key' must start with an alphabetic character.")
                }
                if (RESERVED_PREFIXES.any { key.lowercase(Locale.US).startsWith(it) }) {
                    warnings.add("Parameter '$key' starts with a reserved Firebase prefix ('ga_', 'google_', or 'firebase_').")
                }

                // [5] Parameter value length
                if (value != null) {
                    val strValue = value.toString()
                    if (strValue.length > MAX_PARAM_VALUE_LENGTH) {
                        warnings.add("Parameter '$key' value length (${strValue.length} chars) exceeds max limit of $MAX_PARAM_VALUE_LENGTH characters (Standard GA). Up to 500 for GA360.")
                    }
                }
            }
        }

        return warnings
    }

    /**
     * Validates user property against Firebase rules:
     * [6] USER PROPERTIES: Max 25 user properties per app.
     *     Names must be <= 24 characters.
     *     Values must be <= 36 characters.
     */
    @JvmStatic
    fun validateUserProperty(name: String, value: String?): List<String> {
        val warnings = mutableListOf<String>()
        val trimmedName = name.trim()

        if (!distinctUserProperties.contains(trimmedName)) {
            if (distinctUserProperties.size >= MAX_USER_PROPERTIES) {
                warnings.add("User properties limit exceeded: Firebase allows max $MAX_USER_PROPERTIES user properties per app (this is #${distinctUserProperties.size + 1}).")
            }
            distinctUserProperties.add(trimmedName)
        }

        if (trimmedName.length > MAX_USER_PROPERTY_NAME_LENGTH) {
            warnings.add("User property name '$trimmedName' length (${trimmedName.length} chars) exceeds max limit of $MAX_USER_PROPERTY_NAME_LENGTH characters.")
        }
        if (trimmedName.any { !it.isLetterOrDigit() && it != '_' }) {
            warnings.add("User property name '$trimmedName' contains invalid characters. Must only use alphanumeric characters and underscores.")
        } else if (trimmedName.isNotEmpty() && !trimmedName.first().isLetter()) {
            warnings.add("User property name '$trimmedName' must start with an alphabetic character.")
        }
        if (RESERVED_PREFIXES.any { trimmedName.lowercase(Locale.US).startsWith(it) }) {
            warnings.add("User property name '$trimmedName' starts with a reserved Firebase prefix ('ga_', 'google_', or 'firebase_').")
        }

        if (value != null && value.length > MAX_USER_PROPERTY_VALUE_LENGTH) {
            warnings.add("User property '$trimmedName' value length (${value.length} chars) exceeds max limit of $MAX_USER_PROPERTY_VALUE_LENGTH characters.")
        }

        return warnings
    }

    /**
     * Resets the distinct event names and user properties trackers.
     * Useful when clearing the debug log.
     */
    @JvmStatic
    fun reset() {
        distinctCustomEvents.clear()
        distinctUserProperties.clear()
        dailyEventCount.set(0)
    }

    /**
     * Returns current distinct custom events count.
     */
    @JvmStatic
    fun getDistinctCustomEventsCount(): Int = distinctCustomEvents.size

    /**
     * Returns current distinct user properties count.
     */
    @JvmStatic
    fun getDistinctUserPropertiesCount(): Int = distinctUserProperties.size
}
