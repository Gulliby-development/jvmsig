package io.github.gulliby.jvmsig.base

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class OutputTest {
    @Test
    fun startsEmptyAndCollectsIndependentUtf8Lines() {
        val output = Output()

        assertContentEquals(byteArrayOf(), output.stdout)
        assertContentEquals(byteArrayOf(), output.stderr)

        output.outLine("\u00E9")
        output.errLine("Q")
        output.outLine("")
        output.outLine("\uD83D\uDE00")

        assertContentEquals(
            byteArrayOf(
                0xC3.toByte(), 0xA9.toByte(), 0x0A,
                0x0A,
                0xF0.toByte(), 0x9F.toByte(), 0x98.toByte(), 0x80.toByte(), 0x0A
            ),
            output.stdout
        )
        assertContentEquals(byteArrayOf(0x51, 0x0A), output.stderr)
        assertEquals(
            "[195,169,10,10,240,159,152,128,10]",
            output.stdout.joinToString(separator = ",", prefix = "[", postfix = "]") {
                (it.toInt() and 0xFF).toString()
          }
        )
        assertEquals(
            "[81,10]",
            output.stderr.joinToString(separator = ",", prefix = "[", postfix = "]") {
                (it.toInt() and 0xFF).toString()
          }
        )
    }

    @Test
    fun snapshotsAreDetachedAndUnaffectedByLaterWrites() {
        val output = Output()
        output.outLine("\u00E9")
        output.errLine("Q")

        val mutatedStdout = output.stdout
        val mutatedStderr = output.stderr
        mutatedStdout[0] = 0
        mutatedStderr[0] = 0

        assertContentEquals(byteArrayOf(0xC3.toByte(), 0xA9.toByte(), 0x0A), output.stdout)
        assertContentEquals(byteArrayOf(0x51, 0x0A), output.stderr)

        val earlierStdout = output.stdout
        val earlierStderr = output.stderr
        output.outLine("later")
        output.errLine("later")

        assertContentEquals(byteArrayOf(0xC3.toByte(), 0xA9.toByte(), 0x0A), earlierStdout)
        assertContentEquals(byteArrayOf(0x51, 0x0A), earlierStderr)
        assertContentEquals(
            byteArrayOf(0xC3.toByte(), 0xA9.toByte(), 0x0A, 0x6C, 0x61, 0x74, 0x65, 0x72, 0x0A),
            output.stdout
        )
        assertContentEquals(
            byteArrayOf(0x51, 0x0A, 0x6C, 0x61, 0x74, 0x65, 0x72, 0x0A),
            output.stderr
        )
    }
}
