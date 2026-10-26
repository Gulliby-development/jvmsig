package io.github.gulliby.jvmsig.base

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InputsTest {
    @Test
    fun readReturnsExactBinaryBytesAndEmptyFiles() {
        withTempDirectory { directory ->
            val binaryPath = directory.resolve("binary.dat")
            val emptyPath = directory.resolve("empty.dat")
            val bytes = byteArrayOf(0, 73, 255.toByte(), 10)

            Files.write(binaryPath, bytes)
            Files.write(emptyPath, byteArrayOf())

            assertContentEquals(byteArrayOf(0, 73, 255.toByte(), 10), Inputs.read(binaryPath.toString()))
            assertContentEquals(byteArrayOf(), Inputs.read(emptyPath.toString()))
        }
    }

    @Test
    fun readReportsTheSuppliedPathForMissingAndInvalidPaths() {
        withTempDirectory { directory ->
            val missingPath = directory.resolve("absent.txt").toString()
            val missingFailure = assertFailsWith<Failure> { Inputs.read(missingPath) }
            assertEquals(2, missingFailure.exitCode)
            assertEquals("cannot read $missingPath", missingFailure.message)
        }

        val invalidPath = "bad\u0000path"
        val invalidFailure = assertFailsWith<Failure> { Inputs.read(invalidPath) }
        assertEquals(2, invalidFailure.exitCode)
        assertEquals("cannot read bad\u0000path", invalidFailure.message)
    }

    @Test
    fun decodePreservesEmptyTextLineCharactersBomAndMultibyteScalars() {
        assertEquals("", Inputs.decode(byteArrayOf(), "sample.txt"))
        assertEquals("I\r\nQ\r", Inputs.decode(byteArrayOf(73, 13, 10, 81, 13), "sample.txt"))
        assertEquals("\uFEFFI", Inputs.decode(bytesFromList("[239,187,191,73]"), "sample.txt"))
        assertEquals(
            "\u00E9\u4E2D\uD83D\uDE00",
            Inputs.decode(bytesFromList("[195,169,228,184,173,240,159,152,128]"), "sample.txt")
        )
    }

    @Test
    fun decodeRejectsNulBeforeMalformedUtf8() {
        val failureWithMalformedByte = assertFailsWith<Failure> {
            Inputs.decode(bytesFromList("[255,0]"), "sample.txt")
        }
        assertEquals(2, failureWithMalformedByte.exitCode)
        assertEquals("sample.txt: contains NUL", failureWithMalformedByte.message)

        val failureWithOnlyNul = assertFailsWith<Failure> {
            Inputs.decode(bytesFromList("[0]"), "sample.txt")
        }
        assertEquals(2, failureWithOnlyNul.exitCode)
        assertEquals("sample.txt: contains NUL", failureWithOnlyNul.message)
    }

    @Test
    fun decodeRejectsEverySpecifiedMalformedUtf8Sequence() {
        val malformedSequences = listOf(
            "[255]",
            "[128]",
            "[226,130]",
            "[192,175]",
            "[237,160,128]"
        )

        malformedSequences.forEach { literalBytes ->
            val failure = assertFailsWith<Failure> {
                Inputs.decode(bytesFromList(literalBytes), "sample.txt")
            }
            assertEquals(2, failure.exitCode)
            assertEquals("sample.txt: not valid UTF-8 text", failure.message)
        }
    }

    @Test
    fun realFilesRetainBomAndRejectNulOrMalformedBytesDuringDecode() {
        withTempDirectory { directory ->
            val bomPath = directory.resolve("bom.txt")
            val bomBytes = byteArrayOf(239.toByte(), 187.toByte(), 191.toByte(), 73)
            Files.write(bomPath, bomBytes)
            assertContentEquals(bomBytes, Inputs.read(bomPath.toString()))
            assertEquals("\uFEFFI", Inputs.decode(Inputs.read(bomPath.toString()), "bom.txt"))

            val invalidFiles = listOf(
                Triple("nul.txt", byteArrayOf(255.toByte(), 0), "nul.txt: contains NUL"),
                Triple("malformed.txt", byteArrayOf(255.toByte()), "malformed.txt: not valid UTF-8 text")
            )
            invalidFiles.forEach { (fileName, bytes, expectedMessage) ->
                val file = directory.resolve(fileName)
                Files.write(file, bytes)
                val failure = assertFailsWith<Failure> {
                    Inputs.decode(Inputs.read(file.toString()), fileName)
                }
                assertEquals(2, failure.exitCode)
                assertEquals(expectedMessage, failure.message)
            }
        }
    }

    private fun bytesFromList(literalBytes: String): ByteArray =
        literalBytes.removePrefix("[").removeSuffix("]")
            .split(",")
            .map { it.toInt().toByte() }
            .toByteArray()

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory(
            Paths.get(System.getProperty("java.io.tmpdir")),
            "jvmsig-inputs-"
        )
        try {
            block(directory)
        } finally {
            Files.list(directory).use { entries ->
                entries.forEach { entry -> Files.deleteIfExists(entry) }
            }
            Files.deleteIfExists(directory)
        }
    }
}
