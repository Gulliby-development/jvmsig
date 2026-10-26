package io.github.gulliby.jvmsig.cmd

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProcessTest {
    private data class ProcessResult(
        val exitCode: Int,
        val stdout: ByteArray,
        val stderr: ByteArray
    )

    private data class StdinCase(
        val name: String,
        val bytes: ByteArray,
        val expectedStdout: ByteArray,
        val expectedStderr: ByteArray,
        val expectedExitCode: Int
    )

    @Test
    fun realJvmChecksMalformedUtf8AndUnicodeStdinByteStreams() {
        val cases = listOf(
            StdinCase(
                name = "malformed UTF-8 after an earlier valid line",
                bytes = byteArrayOf(76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114, 105, 110, 103, 59, 10, 0xFF.toByte(), 10),
                expectedStdout = byteArrayOf(),
                expectedStderr = "jvmsig: <stdin>: not valid UTF-8 text\n".toByteArray(Charsets.UTF_8),
                expectedExitCode = 2
            ),
            StdinCase(
                name = "Unicode stdin and a later syntax error",
                bytes = "L\u00e9/\u4e2d;\n(Q)V\n".toByteArray(Charsets.UTF_8),
                expectedStdout =
                    "<stdin>:2:2: JS003 not a type: 'Q'\nchecked 2 signatures, 1 problem\n"
                        .toByteArray(Charsets.UTF_8),
                expectedStderr = byteArrayOf(),
                expectedExitCode = 1
            )
        )

        cases.forEach { case ->
            val result = collect(startStdinProcess(), case.bytes)
            assertContentEquals(case.expectedStdout, result.stdout, case.name)
            assertContentEquals(case.expectedStderr, result.stderr, case.name)
            assertEquals(case.expectedExitCode, result.exitCode, case.name)
        }
    }

    @Test
    fun realJvmUsesCheckLineBoundariesAndChecksNulBeforeMalformedUtf8() {
        val bomAndCrLf = collect(
            startStdinProcess(),
            byteArrayOf(-17, -69, -65, 73, 13, 10, 35, 32, 99, 111, 109, 109, 101, 110, 116, 10, 10, 81, 10)
        )
        assertContentEquals(
            "<stdin>:4:1: JS003 not a type: 'Q'\nchecked 2 signatures, 1 problem\n"
                .toByteArray(Charsets.UTF_8),
            bomAndCrLf.stdout
        )
        assertContentEquals(byteArrayOf(), bomAndCrLf.stderr)
        assertEquals(1, bomAndCrLf.exitCode)

        val nulBeforeMalformed = collect(
            startStdinProcess(),
            byteArrayOf(0xFF.toByte(), 0)
        )
        assertContentEquals(byteArrayOf(), nulBeforeMalformed.stdout)
        assertContentEquals(
            "jvmsig: <stdin>: contains NUL\n".toByteArray(Charsets.UTF_8),
            nulBeforeMalformed.stderr
        )
        assertEquals(2, nulBeforeMalformed.exitCode)
    }

    @Test
    fun realJvmStrictlyDecodesMalformedFileBytesFromItsFileChannel() {
        withTempDirectory { directory ->
            Files.write(directory.resolve("malformed.txt"), byteArrayOf(73, 10, 0xFF.toByte(), 10))
            val process = startProgram(listOf("check", "malformed.txt"), directory)
            val result = collect(process, byteArrayOf())

            assertContentEquals(byteArrayOf(), result.stdout)
            assertContentEquals(
                "jvmsig: malformed.txt: not valid UTF-8 text\n".toByteArray(Charsets.UTF_8),
                result.stderr
            )
            assertEquals(2, result.exitCode)
        }
    }

    private fun startStdinProcess(): Process {
        val builder = ProcessBuilder(
            listOf(
                System.getProperty("java.home") + "/bin/java",
                "-cp",
                System.getProperty("java.class.path"),
                "io.github.gulliby.jvmsig.cmd.MainKt",
                "check",
                "-"
            )
        )
        clearJavaOptionEnvironment(builder)
        return builder.start()
    }

    private fun startProgram(arguments: List<String>, directory: Path?): Process {
        val command = listOf(
            System.getProperty("java.home") + "/bin/java",
            "-cp",
            System.getProperty("java.class.path"),
            "io.github.gulliby.jvmsig.cmd.MainKt"
        ) + arguments
        val builder = ProcessBuilder(command)
        if (directory != null) {
            builder.directory(directory.toFile())
        }
        clearJavaOptionEnvironment(builder)
        return builder.start()
    }

    private fun clearJavaOptionEnvironment(builder: ProcessBuilder) {
        builder.environment().remove("JAVA_TOOL_OPTIONS")
        builder.environment().remove("_JAVA_OPTIONS")
        builder.environment().remove("JDK_JAVA_OPTIONS")
    }

    private fun collect(process: Process, stdin: ByteArray): ProcessResult {
        val stdoutBytes = AtomicReference<ByteArray?>()
        val stderrBytes = AtomicReference<ByteArray?>()
        val stdoutReader = Thread {
            stdoutBytes.set(process.inputStream.use { it.readBytes() })
        }.apply {
            isDaemon = true
            start()
        }
        val stderrReader = Thread {
            stderrBytes.set(process.errorStream.use { it.readBytes() })
        }.apply {
            isDaemon = true
            start()
        }

        try {
            process.outputStream.use { it.write(stdin) }
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), "child JVM did not exit within 60 seconds")
            stdoutReader.join(60_000)
            stderrReader.join(60_000)
            assertFalse(stdoutReader.isAlive, "stdout reader did not finish")
            assertFalse(stderrReader.isAlive, "stderr reader did not finish")
            return ProcessResult(
                exitCode = process.exitValue(),
                stdout = assertNotNull(stdoutBytes.get(), "stdout reader did not collect bytes"),
                stderr = assertNotNull(stderrBytes.get(), "stderr reader did not collect bytes")
            )
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                process.waitFor(60, TimeUnit.SECONDS)
            }
            process.outputStream.close()
            process.inputStream.close()
            process.errorStream.close()
            stdoutReader.join(60_000)
            stderrReader.join(60_000)
        }
    }

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory(
            Paths.get(System.getProperty("java.io.tmpdir")),
            "jvmsig-process-"
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
