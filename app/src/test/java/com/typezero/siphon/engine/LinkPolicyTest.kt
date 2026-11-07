package com.typezero.siphon.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LinkPolicyTest {
    @Test fun `channel URLs are blocked explicitly`() {
        assertNotNull(LinkPolicy.collectionReason("https://www.youtube.com/@somechannel"))
    }

    @Test fun `ordinary watch URL remains allowed`() {
        assertNull(LinkPolicy.collectionReason("https://www.youtube.com/watch?v=abc123"))
    }

    @Test fun `shared text yields its HTTP URL`() {
        assertEquals(
            "https://example.com/watch?v=abc123",
            LinkPolicy.extractUrl("Watch this: https://example.com/watch?v=abc123")
        )
    }

    @Test fun `surrounding message punctuation is not part of the URL`() {
        assertEquals(
            "https://example.com/watch?v=abc123",
            LinkPolicy.extractUrl("Watch <https://example.com/watch?v=abc123>.")
        )
    }

    @Test fun `URL without a host is rejected`() {
        assertNull(LinkPolicy.extractUrl("https://?source=share"))
    }

    @Test fun `valid URL ending in a balanced parenthesis is preserved`() {
        val url = "https://en.wikipedia.org/wiki/Function_(mathematics)"
        assertEquals(url, LinkPolicy.extractUrl(url))
    }

    @Test fun `terminal punctuation on a raw URL is preserved`() {
        val urls = listOf(
            "https://example.com/search?",
            "https://example.com/period.",
            "https://example.com/comma,",
            "https://example.com/semicolon;",
            "https://example.com/bang!"
        )
        urls.forEach { url -> assertEquals(url, LinkPolicy.extractUrl(url)) }
    }

    @Test fun `terminal punctuation inside explicit URL wrappers is preserved`() {
        val url = "https://example.com/search?"
        assertEquals(url, LinkPolicy.extractUrl("Watch <$url>."))
    }

    @Test fun `meaningful terminal punctuation on embedded URLs is preserved`() {
        listOf("?", ";", "!").forEach { punctuation ->
            val url = "https://example.com/search$punctuation"
            assertEquals(url, LinkPolicy.extractUrl("Watch $url"))
        }
    }

    @Test fun `sentence punctuation after embedded URLs is removed`() {
        listOf(".", ",").forEach { punctuation ->
            val url = "https://example.com/watch"
            assertEquals(url, LinkPolicy.extractUrl("Watch $url$punctuation"))
        }
    }
}
