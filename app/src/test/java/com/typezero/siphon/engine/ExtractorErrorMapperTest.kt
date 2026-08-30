package com.typezero.siphon.engine

import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorErrorMapperTest {
    @Test fun `403 points user to extractor refresh`() {
        val message = ExtractorErrorMapper.friendly("ERROR: HTTP Error 403: Forbidden")
        assertTrue(message.contains("Update the extractor", ignoreCase = true))
    }
}
