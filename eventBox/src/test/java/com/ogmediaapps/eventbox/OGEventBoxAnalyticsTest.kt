package com.ogmediaapps.eventbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OGEventBoxAnalyticsTest {

    @Test
    fun logEventForwardsToSink() {
        val received = mutableListOf<String>()
        val analytics = OGEventBoxAnalytics.wrap(eventSink = { name, params ->
            assertNull(params)
            received.add(name)
        })

        analytics.logEvent("user_signed_in")
        analytics.logEvent("purchase", null)

        assertEquals(listOf("user_signed_in", "purchase"), received)
    }

    @Test
    fun userPropertyForwardsToSinkWhenProvided() {
        val received = mutableListOf<Pair<String, String?>>()
        val analytics = OGEventBoxAnalytics.wrap(
            eventSink = { _, _ -> },
            userPropertySink = { name, value -> received.add(name to value) }
        )

        analytics.setUserProperty("user_tier", "premium")

        assertEquals(listOf("user_tier" to "premium"), received)
    }

    @Test
    fun userPropertyWithoutSinkDoesNotThrow() {
        OGEventBoxAnalytics.wrap(eventSink = { _, _ -> }).setUserProperty("user_tier", "free")
    }
}
