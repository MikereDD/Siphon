package com.typezero.siphon.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypezeroVersionTest {
    @Test fun `numeric development components compare numerically`() {
        assertTrue(TypezeroVersion.parse("1.3-dev.10") > TypezeroVersion.parse("1.3-dev.9"))
    }

    @Test fun `multi part development builds compare correctly`() {
        assertTrue(TypezeroVersion.parse("2.7-dev.10.9.1") > TypezeroVersion.parse("2.7-dev.10.9"))
    }

    @Test fun `stable orders after development with same core`() {
        assertTrue(TypezeroVersion.parse("1.3") > TypezeroVersion.parse("1.3-dev.99"))
    }

    @Test fun `missing numeric parts normalize to zero`() {
        assertEquals(0, TypezeroVersion.parse("1.3").compareTo(TypezeroVersion.parse("1.3.0")))
    }

    @Test(expected = IllegalStateException::class)
    fun `malformed version is rejected`() {
        TypezeroVersion.parse("1.3-beta")
    }
}
