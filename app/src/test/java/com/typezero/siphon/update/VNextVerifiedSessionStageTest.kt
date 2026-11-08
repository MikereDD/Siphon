package com.typezero.siphon.update

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class VNextVerifiedSessionStageTest {
    @Test fun writesExactAuthorizedBytesAndReportsTheirDigest() {
        val input = byteArrayOf(1, 5, 10, 20, 30)
        val output = ByteArrayOutputStream()
        val hash = VNextVerifiedSessionStage.transferAndSha256(
            ByteArrayInputStream(input), output, input.size.toLong()
        )
        assertArrayEquals(input, output.toByteArray())
        assertEquals(sha256(input), hash)
    }

    @Test fun refusesTruncatedInput() {
        assertThrows(IllegalArgumentException::class.java) {
            VNextVerifiedSessionStage.transferAndSha256(
                ByteArrayInputStream(byteArrayOf(1, 2)), ByteArrayOutputStream(), 3
            )
        }
    }

    @Test fun refusesAppendedBytes() {
        assertThrows(IllegalArgumentException::class.java) {
            VNextVerifiedSessionStage.transferAndSha256(
                ByteArrayInputStream(byteArrayOf(1, 2, 3)), ByteArrayOutputStream(), 2
            )
        }
    }

    @Test fun rejectsZeroLength() {
        assertThrows(IllegalArgumentException::class.java) {
            VNextVerifiedSessionStage.transferAndSha256(
                ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), 0
            )
        }
    }

    @Test fun readbackHashDetectsChangedBytes() {
        val original = byteArrayOf(6, 7, 8)
        val changed = byteArrayOf(6, 7, 9)
        assertEquals(sha256(original), VNextVerifiedSessionStage.hashStream(ByteArrayInputStream(original)))
        org.junit.Assert.assertNotEquals(
            sha256(original), VNextVerifiedSessionStage.hashStream(ByteArrayInputStream(changed))
        )
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
