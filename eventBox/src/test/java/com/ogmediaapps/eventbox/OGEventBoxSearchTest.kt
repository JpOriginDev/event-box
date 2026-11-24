package com.ogmediaapps.eventbox

import com.ogmediaapps.eventbox.filter.OGEventBoxExport
import com.ogmediaapps.eventbox.filter.OGEventBoxQuery
import com.ogmediaapps.eventbox.model.OGEventBoxDebugEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class OGEventBoxSearchTest {

    private val login = OGEventBoxDebugEvent(1, 1_000L, "login_success", mapOf("screen" to "home", "method" to "google"))
    private val purchase = OGEventBoxDebugEvent(
        2, 2_000L, "purchase", mapOf("value" to 9.99, "currency" to "USD"),
        warnings = listOf("Too many parameters"),
    )
    private val userProp = OGEventBoxDebugEvent(
        3, 3_000L, "user_property: plan", mapOf("property_name" to "plan", "property_value" to "pro"),
    )
    private val noParams = OGEventBoxDebugEvent(4, 4_000L, "app_open")
    private val all = listOf(login, purchase, userProp, noParams)

    private fun names(query: String) =
        OGEventBoxQuery.apply(all, query).map { it.name }

    @Test
    fun plainTermMatchesNameCaseInsensitive() {
        assertEquals(listOf("login_success"), names("LOGIN"))
    }

    @Test
    fun plainTermMatchesParamKeysAndValues() {
        assertEquals(listOf("login_success"), names("google"))
        assertEquals(listOf("purchase"), names("currency"))
        assertEquals(listOf("purchase"), names("9.99"))
    }

    @Test
    fun keyValueTermMatchesOnlyThatKey() {
        assertEquals(listOf("login_success"), names("screen:ho"))
        assertEquals(emptyList<String>(), names("method:home"))
        assertEquals(emptyList<String>(), names("missing:home"))
    }

    @Test
    fun negationExcludes() {
        assertEquals(listOf("login_success", "purchase", "app_open"), names("!user_property"))
    }

    @Test
    fun termsCombineWithAnd() {
        assertEquals(listOf("login_success"), names("login screen:home"))
        assertEquals(emptyList<String>(), names("login purchase"))
    }

    @Test
    fun blankQueryReturnsSameList() {
        assertSame(all, OGEventBoxQuery.apply(all, "   "))
        assertTrue(OGEventBoxQuery.parse("! ").isEmpty())
    }

    @Test
    fun valueWhoseToStringThrowsDoesNotCrash() {
        val bad = OGEventBoxDebugEvent(9, 0L, "weird", mapOf("x" to object {
            override fun toString(): String = throw IllegalStateException("boom")
        }))
        assertFalse(OGEventBoxQuery.matches(bad, "boom"))
        assertTrue(OGEventBoxQuery.matches(bad, "weird"))
        assertTrue(OGEventBoxExport.eventJson(bad).contains("\"x\": \"<unprintable>\""))
    }

    @Test
    fun highlightWordsSkipNegatedTermsAndUseValues() {
        assertEquals(listOf("login", "home"), OGEventBoxQuery.highlightWords("login screen:home !purchase"))
    }

    @Test
    fun isKeywordsSelectByKind() {
        assertEquals(listOf("purchase"), names("is:warning"))
        assertEquals(listOf("purchase"), names("IS:WARNINGS"))
        assertEquals(listOf("login_success", "user_property: plan", "app_open"), names("!is:warning"))
        assertEquals(listOf("user_property: plan"), names("is:user_property"))
        assertEquals(listOf("login_success", "purchase", "app_open"), names("is:event"))
        assertEquals(listOf("purchase"), names("is:event usd"))
        assertEquals(emptyList<String>(), names("is:repeated"))

        val repeated = OGEventBoxDebugEvent(5, 5_000L, "tap", repeatCount = 3)
        assertTrue(OGEventBoxQuery.matches(repeated, "is:repeated"))
    }

    @Test
    fun isKeywordsAreNotHighlightedAndUnknownOnesSearchParams() {
        assertEquals(listOf("usd"), OGEventBoxQuery.highlightWords("is:warning usd"))
        val withIsParam = OGEventBoxDebugEvent(6, 0L, "x", mapOf("is" to "foo"))
        assertTrue(OGEventBoxQuery.matches(withIsParam, "is:foo"))
        assertFalse(OGEventBoxQuery.matches(withIsParam, "is:warning"))
    }

    @Test
    fun exportEscapesAndTypesValues() {
        val event = OGEventBoxDebugEvent(
            5, 0L, "quote\"d", linkedMapOf("n" to 3L, "b" to true, "s" to "a\nb", "nil" to null),
            warnings = listOf("w1"),
        )
        val json = OGEventBoxExport.eventJson(event)
        assertTrue(json.contains("\"name\": \"quote\\\"d\""))
        assertTrue(json.contains("\"n\": 3"))
        assertTrue(json.contains("\"b\": true"))
        assertTrue(json.contains("\"s\": \"a\\nb\""))
        assertTrue(json.contains("\"nil\": null"))
        assertTrue(json.contains("\"warnings\": [\"w1\"]"))
        assertEquals("[]", OGEventBoxExport.logJson(emptyList()))
        val log = OGEventBoxExport.logJson(listOf(login, noParams))
        assertTrue(log.startsWith("[\n") && log.endsWith("\n]"))
        assertTrue(log.contains("\"params\": {}"))
    }
}
