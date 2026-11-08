package com.typezero.siphon.update

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class VNextPrivateApkStageTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun privateCopyUsesIndependentBytes() {
        val source = temp.newFile("download.apk")
        val target = File(temp.root, "private.apk")
        source.writeBytes(byteArrayOf(1, 2, 3, 4))
        VNextPrivateApkStage.copyBounded(source, target)
        source.writeBytes(byteArrayOf(9, 9))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), target.readBytes())
    }

    @Test fun emptyDownloadFailsClosedAndLeavesNoTarget() {
        val source = temp.newFile("empty.apk")
        val target = File(temp.root, "private.apk")
        assertThrows(IllegalArgumentException::class.java) {
            VNextPrivateApkStage.copyBounded(source, target)
        }
        assertFalse(target.exists())
    }

    @Test fun destinationCannotBeOverwritten() {
        val source = temp.newFile("download.apk").apply { writeBytes(byteArrayOf(1)) }
        val target = temp.newFile("private.apk").apply { writeBytes(byteArrayOf(7)) }
        assertThrows(IllegalArgumentException::class.java) {
            VNextPrivateApkStage.copyBounded(source, target)
        }
        assertArrayEquals(byteArrayOf(7), target.readBytes())
    }

    @Test fun stagedDigestDetectsMutation() {
        val source = temp.newFile("download.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val target = File(temp.root, "private.apk")
        VNextPrivateApkStage.copyBounded(source, target)
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(byteArrayOf(1, 2, 3)).joinToString("") { "%02x".format(it) }
        assertEquals(expected, VNextPrivateApkStage.digest(target))
        target.appendBytes(byteArrayOf(4))
        assertFalse(expected == VNextPrivateApkStage.digest(target))
    }
}
