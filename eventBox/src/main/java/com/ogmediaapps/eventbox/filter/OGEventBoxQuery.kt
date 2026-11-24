package com.ogmediaapps.eventbox.filter

import com.ogmediaapps.eventbox.model.OGEventBoxDebugEvent

/**
 * Matches log entries against the search bar's text query.
 *
 * Query syntax (case-insensitive, terms separated by spaces and combined with AND):
 * - `login`        the name, or any parameter key or value, contains "login"
 * - `screen:home`  parameter `screen` has a value containing "home"
 * - `!user_prop`   excludes entries that match the term
 * - `is:warning`   entries that broke a Firebase limit; also `is:user_property`, `is:event`
 *                  (anything that is not a user property) and `is:repeated` (fired back to back)
 *
 * Pure Kotlin on purpose so it can be unit tested on the JVM; it never throws.
 */
internal object OGEventBoxQuery {

    /** Name prefix that the repository gives entries recorded via `setUserProperty`. */
    const val USER_PROPERTY_PREFIX = "user_property: "

    internal data class Term(val key: String?, val value: String, val negated: Boolean)

    fun parse(query: String): List<Term> = query.trim()
        .split(WHITESPACE)
        .filter { it.isNotEmpty() }
        .mapNotNull { raw ->
            val negated = raw.startsWith("!")
            val body = if (negated) raw.substring(1) else raw
            if (body.isEmpty()) return@mapNotNull null
            val colon = body.indexOf(':')
            if (colon > 0) {
                Term(body.substring(0, colon).lowercase(), body.substring(colon + 1).lowercase(), negated)
            } else {
                Term(null, body.lowercase(), negated)
            }
        }

    /**
     * The positive words to highlight in a row: plain terms and the value part of `key:value`.
     * `is:` keywords describe the entry rather than its text, so they are never highlighted.
     */
    fun highlightWords(query: String): List<String> =
        parse(query).filter { !it.negated && it.value.isNotEmpty() && !isKeyword(it) }.map { it.value }.distinct()

    fun isUserProperty(event: OGEventBoxDebugEvent): Boolean = event.name.startsWith(USER_PROPERTY_PREFIX)

    fun matches(event: OGEventBoxDebugEvent, query: String): Boolean = matches(event, parse(query))

    fun matches(event: OGEventBoxDebugEvent, terms: List<Term>): Boolean =
        terms.all { term -> termMatches(event, term) != term.negated }

    fun apply(events: List<OGEventBoxDebugEvent>, query: String): List<OGEventBoxDebugEvent> {
        val terms = parse(query)
        if (terms.isEmpty()) return events
        return events.filter { matches(it, terms) }
    }

    private fun termMatches(event: OGEventBoxDebugEvent, term: Term): Boolean {
        keywordMatches(event, term)?.let { return it }
        val params = event.params.orEmpty()
        if (term.key != null) {
            return params.entries.any { (k, v) ->
                k.lowercase() == term.key && safeString(v).lowercase().contains(term.value)
            }
        }
        if (event.name.lowercase().contains(term.value)) return true
        if (event.source?.lowercase()?.contains(term.value) == true) return true
        return params.entries.any { (k, v) ->
            k.lowercase().contains(term.value) || safeString(v).lowercase().contains(term.value)
        }
    }

    private fun isKeyword(term: Term): Boolean = term.key == KEYWORD_KEY && term.value in KEYWORDS

    /**
     * The result of an `is:` keyword term, or `null` when [term] is not one. An unknown value
     * such as `is:foo` is `null` too, so it still searches a parameter named "is".
     */
    private fun keywordMatches(event: OGEventBoxDebugEvent, term: Term): Boolean? {
        if (term.key != KEYWORD_KEY) return null
        return when (term.value) {
            "warning", "warnings", "warn" -> event.hasWarning
            "user_property", "user_properties", "userprop" -> isUserProperty(event)
            "event", "events" -> !isUserProperty(event)
            "repeated", "repeat", "duplicate" -> event.isRepeated
            else -> null
        }
    }

    private fun safeString(value: Any?): String = runCatching { value.toString() }.getOrDefault("")

    private const val KEYWORD_KEY = "is"
    private val KEYWORDS = setOf(
        "warning", "warnings", "warn",
        "user_property", "user_properties", "userprop",
        "event", "events",
        "repeated", "repeat", "duplicate",
    )

    private val WHITESPACE = Regex("\\s+")
}
