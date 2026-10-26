package io.github.gulliby.jvmsig.cmd

import io.github.gulliby.jvmsig.base.Output
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

class CheckCommandTest {
    private data class CliResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    )

    @Test
    fun checksTheRequestFixturesAndAggregatesAcrossFiles() {
        withTempDirectory { directory ->
            val ok = directory.resolve("ok.txt")
            val bad = directory.resolve("bad.txt")
            val generic = directory.resolve("generic.txt")
            Files.write(
                ok,
                "Ljava/lang/String;\n# a comment\n\n(IJ)V\n<T:Ljava/lang/Object;>(TT;)TT;\n".toByteArray(Charsets.UTF_8)
            )
            Files.write(
                bad,
                "Ljava/lang/String\nLpkg//A;\n(I\n(IV)V\n[V\nQ\n<T:>V\n".toByteArray(Charsets.UTF_8)
            )
            Files.write(
                generic,
                ("Ljava/util/List<I>;\nLjava/util/List<TT;>;\n<T:TT;>(TT;)V\n<A:TB;B:TA;>(TA;)V\n" +
                    "<T:>(TT;TT;)V\n<T:Ljava/lang/Object;>(TT;TX;)V\n<T:>(TT;)V^TT;\n"
                    ).toByteArray(Charsets.UTF_8)
            )

            val okResult = runCli(listOf("check", ok.toString()))
            assertEquals(0, okResult.exitCode)
            assertEquals("checked 3 signatures, 0 problems\n", okResult.stdout)
            assertEquals("", okResult.stderr)

            val badResult = runCli(listOf("check", bad.toString()))
            assertEquals(1, badResult.exitCode)
            assertEquals(
                "$bad:1:18: JS001 unexpected end, expected ';'\n" +
                    "$bad:2:6: JS005 empty name\n" +
                    "$bad:3:3: JS001 unexpected end, expected ')'\n" +
                    "$bad:4:3: JS004 void is only allowed as a return type\n" +
                    "$bad:5:2: JS004 void is only allowed as a return type\n" +
                    "$bad:6:1: JS003 not a type: 'Q'\n" +
                    "$bad:7:5: JS002 unexpected 'V', expected '(' or the superclass\n" +
                    "checked 7 signatures, 7 problems\n",
                badResult.stdout
            )
            assertEquals("", badResult.stderr)

            val genericResult = runCli(listOf("check", generic.toString()))
            assertEquals(1, genericResult.exitCode)
            assertEquals(
                "$generic:1:17: JS011 a type argument must be a reference type\n" +
                    "$generic:2:17: JS012 type variable T is not declared\n" +
                    "$generic:3:2: JS015 type parameter T has a cyclic bound\n" +
                    "$generic:4:2: JS015 type parameter A has a cyclic bound\n" +
                    "$generic:6:27: JS012 type variable X is not declared\n" +
                    "checked 7 signatures, 5 problems\n",
                genericResult.stdout
            )
            assertEquals("", genericResult.stderr)

            val externalResult = runCli(listOf("check", "--var", "T", "--var", "X", generic.toString()))
            assertEquals(1, externalResult.exitCode)
            assertEquals(
                "$generic:1:17: JS011 a type argument must be a reference type\n" +
                    "$generic:3:2: JS015 type parameter T has a cyclic bound\n" +
                    "$generic:4:2: JS015 type parameter A has a cyclic bound\n" +
                    "checked 7 signatures, 3 problems\n",
                externalResult.stdout
            )
            assertEquals("", externalResult.stderr)

            val combinedResult = runCli(listOf("check", ok.toString(), bad.toString()))
            assertEquals(1, combinedResult.exitCode)
            assertEquals(
                "$bad:1:18: JS001 unexpected end, expected ';'\n" +
                    "$bad:2:6: JS005 empty name\n" +
                    "$bad:3:3: JS001 unexpected end, expected ')'\n" +
                    "$bad:4:3: JS004 void is only allowed as a return type\n" +
                    "$bad:5:2: JS004 void is only allowed as a return type\n" +
                    "$bad:6:1: JS003 not a type: 'Q'\n" +
                    "$bad:7:5: JS002 unexpected 'V', expected '(' or the superclass\n" +
                    "checked 10 signatures, 7 problems\n",
                combinedResult.stdout
            )
            assertEquals("", combinedResult.stderr)
        }
    }

    @Test
    fun reportsTheSmallestSemanticColumnAndDuplicateColumnTwentyTwo() {
        val competing = runCli(
            listOf("check", "-"),
            lazy { "<T:TU;T:>()V\n".toByteArray(Charsets.UTF_8) }
        )
        assertEquals(1, competing.exitCode)
        assertEquals(
            "<stdin>:1:4: JS012 type variable U is not declared\nchecked 1 signature, 1 problem\n",
            competing.stdout
        )
        assertEquals("", competing.stderr)

        val duplicate = runCli(
            listOf("check", "-"),
            lazy { "<T:Ljava/lang/Object;T:>()V\n".toByteArray(Charsets.UTF_8) }
        )
        assertEquals(1, duplicate.exitCode)
        assertEquals(
            "<stdin>:1:22: JS013 type parameter T is declared twice\nchecked 1 signature, 1 problem\n",
            duplicate.stdout
        )
        assertEquals("", duplicate.stderr)

        val externalDuplicate = runCli(
            listOf("check", "--var", "T", "-"),
            lazy { "<T:Ljava/lang/Object;T:>()V\n".toByteArray(Charsets.UTF_8) }
        )
        assertEquals(1, externalDuplicate.exitCode)
        assertEquals(
            "<stdin>:1:22: JS013 type parameter T is declared twice\nchecked 1 signature, 1 problem\n",
            externalDuplicate.stdout
        )
        assertEquals("", externalDuplicate.stderr)

        val externalCycle = runCli(
            listOf("check", "--var", "T", "-"),
            lazy { "<T:TT;>(TT;)V\n".toByteArray(Charsets.UTF_8) }
        )
        assertEquals(1, externalCycle.exitCode)
        assertEquals(
            "<stdin>:1:2: JS015 type parameter T has a cyclic bound\nchecked 1 signature, 1 problem\n",
            externalCycle.stdout
        )
        assertEquals("", externalCycle.stderr)
    }

    @Test
    fun preservesBomLineNumberCrAndCommentBoundaries() {
        val cases = listOf(
            Triple(
                "\uFEFFI\r\n# comment\n\nQ\n",
                "<stdin>:4:1: JS003 not a type: 'Q'\nchecked 2 signatures, 1 problem\n",
                "BOM, CRLF and skipped physical lines"
            ),
            Triple(
                "I\r",
                "<stdin>:1:2: JS008 unexpected text after the signature\nchecked 1 signature, 1 problem\n",
                "a final CR is retained"
            ),
            Triple(
                "I\r\r\n",
                "<stdin>:1:2: JS008 unexpected text after the signature\nchecked 1 signature, 1 problem\n",
                "only the CR directly before LF is removed"
            ),
            Triple(
                "",
                "checked 0 signatures, 0 problems\n",
                "empty input has no physical signature lines"
            ),
            Triple(
                "I",
                "checked 1 signature, 0 problems\n",
                "unterminated final line is processed"
            ),
            Triple(
                " # comment\nI\uFEFF\n",
                "<stdin>:1:1: JS003 not a type: U+0020\n" +
                    "<stdin>:2:2: JS008 unexpected text after the signature\n" +
                    "checked 2 signatures, 2 problems\n",
                "a space-prefixed comment is a signature and an interior BOM remains"
            )
        )

        val expectedExitCodes = listOf(1, 1, 1, 0, 0, 1)
        cases.forEachIndexed { index, (input, expectedStdout, scenario) ->
            val result = runCli(
                listOf("check", "-"),
                lazy { input.toByteArray(Charsets.UTF_8) }
            )
            assertEquals(expectedExitCodes[index], result.exitCode, scenario)
            assertEquals(expectedStdout, result.stdout, scenario)
            assertEquals("", result.stderr, scenario)
        }
    }

    @Test
    fun replaysStandardInputOnceAndDoesNotReadItForFilesOrOtherCommands() {
        var stdinInitializations = 0
        val repeated = runCli(
            listOf("check", "-", "-"),
            lazy {
                stdinInitializations++
                "I\nQ\n".toByteArray(Charsets.UTF_8)
            }
        )
        assertEquals(1, stdinInitializations)
        assertEquals(1, repeated.exitCode)
        assertEquals(
            "<stdin>:2:1: JS003 not a type: 'Q'\n" +
                "<stdin>:2:1: JS003 not a type: 'Q'\n" +
                "checked 4 signatures, 2 problems\n",
            repeated.stdout
        )
        assertEquals("", repeated.stderr)

        withTempDirectory { directory ->
            val file = directory.resolve("one.txt")
            Files.write(file, "I\n".toByteArray(Charsets.UTF_8))
            val fileResult = runCli(
                listOf("check", file.toString()),
                lazy {
                    stdinInitializations++
                    byteArrayOf()
                }
            )
            assertEquals(0, fileResult.exitCode)
            assertEquals("checked 1 signature, 0 problems\n", fileResult.stdout)
            assertEquals("", fileResult.stderr)

            val showResult = runCli(
                listOf("show", "I"),
                lazy {
                    stdinInitializations++
                    byteArrayOf()
                }
            )
            assertEquals(0, showResult.exitCode)
            assertEquals("int\n", showResult.stdout)
            assertEquals("", showResult.stderr)
            assertEquals(1, stdinInitializations)
        }
    }

    @Test
    fun fatalFilesPreservePriorDiagnosticsAndSuppressLaterWorkAndSummary() {
        withTempDirectory { directory ->
            val prior = directory.resolve("prior.txt")
            val later = directory.resolve("later.txt")
            val malformed = directory.resolve("malformed.txt")
            val nul = directory.resolve("nul.txt")
            Files.write(prior, "Q\n".toByteArray(Charsets.UTF_8))
            Files.write(later, "Q\n".toByteArray(Charsets.UTF_8))
            // AC-010 bytes: [73,10,255,10].
            Files.write(malformed, intArrayOf(73, 10, 255, 10).map { it.toByte() }.toByteArray())
            Files.write(nul, byteArrayOf(0xFF.toByte(), 0))

            val malformedResult = runCli(listOf("check", prior.toString(), malformed.toString(), later.toString()))
            assertEquals(2, malformedResult.exitCode)
            assertEquals(
                "FILE:1:1: JS003 not a type: 'Q'\n".replace("FILE", prior.toString()),
                malformedResult.stdout
            )
            assertEquals("jvmsig: $malformed: not valid UTF-8 text\n", malformedResult.stderr)

            val nulResult = runCli(listOf("check", prior.toString(), nul.toString(), later.toString()))
            assertEquals(2, nulResult.exitCode)
            assertEquals("$prior:1:1: JS003 not a type: 'Q'\n", nulResult.stdout)
            assertEquals("jvmsig: $nul: contains NUL\n", nulResult.stderr)

            val missing = directory.resolve("missing.txt").toString()
            val missingResult = runCli(listOf("check", missing))
            assertEquals(2, missingResult.exitCode)
            assertEquals("", missingResult.stdout)
            assertEquals("jvmsig: cannot read FILE\n".replace("FILE", missing), missingResult.stderr)

            val missingAfterPrior = runCli(listOf("check", prior.toString(), missing))
            assertEquals(2, missingAfterPrior.exitCode)
            assertEquals("$prior:1:1: JS003 not a type: 'Q'\n", missingAfterPrior.stdout)
            assertEquals("jvmsig: cannot read $missing\n", missingAfterPrior.stderr)

            val stdinMalformed = runCli(
                listOf("check", "-"),
                lazy { intArrayOf(73, 10, 255, 10).map { it.toByte() }.toByteArray() }
            )
            assertEquals(2, stdinMalformed.exitCode)
            assertEquals("", stdinMalformed.stdout)
            assertEquals("jvmsig: <stdin>: not valid UTF-8 text\n", stdinMalformed.stderr)

            val stdinNul = runCli(
                listOf("check", "-"),
                lazy { byteArrayOf(0xFF.toByte(), 0) }
            )
            assertEquals(2, stdinNul.exitCode)
            assertEquals("", stdinNul.stdout)
            assertEquals("jvmsig: <stdin>: contains NUL\n", stdinNul.stderr)
        }
    }

    private fun runCli(args: List<String>, stdin: Lazy<ByteArray> = lazy { byteArrayOf() }): CliResult {
        val output = Output()
        val exitCode = Cli.run(args, stdin, output)
        return CliResult(
            exitCode = exitCode,
            stdout = String(output.stdout, Charsets.UTF_8),
            stderr = String(output.stderr, Charsets.UTF_8)
        )
    }

    private fun withTempDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory(
            Paths.get(System.getProperty("java.io.tmpdir")),
            "jvmsig-check-"
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
