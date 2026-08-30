package com.typezero.siphon.engine

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
}
