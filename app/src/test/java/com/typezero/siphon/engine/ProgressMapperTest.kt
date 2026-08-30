package com.typezero.siphon.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressMapperTest {
    @Test fun `engine one hundred never means whole job one hundred`() {
        val mapped = ProgressMapper.map(1f, "[download] 100%")
        assertTrue(mapped.percent < 100)
        assertTrue(mapped.percent <= 94)
    }

    @Test fun `unknown progress stays indeterminate`() {
        assertEquals(-1, ProgressMapper.map(-1f, "Downloading webpage").percent)
    }
}
