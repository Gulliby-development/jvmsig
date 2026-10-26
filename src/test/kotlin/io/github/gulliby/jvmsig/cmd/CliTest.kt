package io.github.gulliby.jvmsig.cmd

import io.github.gulliby.jvmsig.base.Output
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CliTest {
    private data class CliResult(
        val exitCode: Int,
        val stdout: ByteArray,
        val stderr: ByteArray
    )

    private data class ExpectedRun(
        val args: List<String>,
        val stdout: String,
        val stderr: String,
        val exitCode: Int
    )

    private fun runCli(
        args: List<String>,
        stdin: Lazy<ByteArray> = lazy { byteArrayOf() }
    ): CliResult {
        val output = Output()
        val exitCode = Cli.run(args, stdin, output)
        return CliResult(exitCode, output.stdout, output.stderr)
    }

    @Test
    fun printsExactUsageForHelpAndMissingCommandAndRejectsUnknownCommands() {
        val usage = "usage: jvmsig show [--kotlin] [--short] [--name NAME] SIGNATURE...\n" +
            "       jvmsig check [--var NAME]... FILE...\n" +
            "       jvmsig erase SIGNATURE...\n" +
            "       jvmsig slots [--static] SIGNATURE...\n" +
            "       jvmsig jni CLASS METHOD [DESCRIPTOR]\n"
        val cases = listOf(
            ExpectedRun(emptyList(), "", usage, 2),
            ExpectedRun(
                listOf("--help", "bogus"),
                usage,
                "",
                0
            ),
            ExpectedRun(listOf("--help"), usage, "", 0),
            ExpectedRun(listOf("-h"), usage, "", 0),
            ExpectedRun(listOf("bogus"), "", "jvmsig: unknown command bogus\n", 2)
        )

        cases.forEach { case ->
            val actual = runCli(case.args)
            assertContentEquals(case.stdout.toByteArray(Charsets.UTF_8), actual.stdout, "stdout for ${case.args}")
            assertContentEquals(case.stderr.toByteArray(Charsets.UTF_8), actual.stderr, "stderr for ${case.args}")
            assertEquals(case.exitCode, actual.exitCode, "exit code for ${case.args}")
        }

        val noArguments = runCli(emptyList())
        assertContentEquals(usage.toByteArray(Charsets.UTF_8), noArguments.stderr)
        assertContentEquals(byteArrayOf(), noArguments.stdout)
    }

    @Test
    fun reportsEveryCommandLineErrorWithExactMessageAndChannels() {
        val cases = listOf(
            ExpectedRun(listOf("show", "--name"), "", "jvmsig: option --name needs a value\n", 2),
            ExpectedRun(listOf("show", "--name="), "", "jvmsig: option --name needs a non-empty value\n", 2),
            ExpectedRun(listOf("show", "--name", ""), "", "jvmsig: option --name needs a non-empty value\n", 2),
            ExpectedRun(listOf("show", "--var", "T", "I"), "", "jvmsig: unknown option --var\n", 2),
            ExpectedRun(listOf("erase", "--x=1", "I"), "", "jvmsig: unknown option --x\n", 2),
            ExpectedRun(listOf("jni", "--x", "a", "f"), "", "jvmsig: unknown option --x\n", 2),
            ExpectedRun(listOf("slots", "--static=1", "()V"), "", "jvmsig: option --static takes no value\n", 2),
            ExpectedRun(listOf("show", "--kotlin=1", "I"), "", "jvmsig: option --kotlin takes no value\n", 2),
            ExpectedRun(listOf("show", "--short=1", "I"), "", "jvmsig: option --short takes no value\n", 2),
            ExpectedRun(listOf("check", "--var"), "", "jvmsig: option --var needs a value\n", 2),
            ExpectedRun(listOf("check", "--var="), "", "jvmsig: option --var needs a non-empty value\n", 2),
            ExpectedRun(listOf("check", "--var", "a.b", "-"), "", "jvmsig: invalid --var value: a.b\n", 2),
            ExpectedRun(listOf("check", "--var", "a;b", "-"), "", "jvmsig: invalid --var value: a;b\n", 2),
            ExpectedRun(listOf("check", "--var", "a[b", "-"), "", "jvmsig: invalid --var value: a[b\n", 2),
            ExpectedRun(listOf("check", "--var", "a/b", "-"), "", "jvmsig: invalid --var value: a/b\n", 2),
            ExpectedRun(listOf("check", "--var", "a<b", "-"), "", "jvmsig: invalid --var value: a<b\n", 2),
            ExpectedRun(listOf("check", "--var", "a>b", "-"), "", "jvmsig: invalid --var value: a>b\n", 2),
            ExpectedRun(listOf("check", "--var", "a:b", "-"), "", "jvmsig: invalid --var value: a:b\n", 2),
            ExpectedRun(listOf("show"), "", "jvmsig: show needs at least one signature\n", 2),
            ExpectedRun(listOf("erase"), "", "jvmsig: erase needs at least one signature\n", 2),
            ExpectedRun(listOf("slots"), "", "jvmsig: slots needs at least one signature\n", 2),
            ExpectedRun(listOf("check"), "", "jvmsig: check needs at least one file\n", 2),
            ExpectedRun(listOf("jni"), "", "jvmsig: jni needs CLASS METHOD [DESCRIPTOR]\n", 2),
            ExpectedRun(listOf("jni", "a"), "", "jvmsig: jni needs CLASS METHOD [DESCRIPTOR]\n", 2),
            ExpectedRun(listOf("jni", "a", "f", "()V", "extra"), "", "jvmsig: jni needs CLASS METHOD [DESCRIPTOR]\n", 2)
        )

        cases.forEach { case ->
            val actual = runCli(case.args)
            assertContentEquals(case.stdout.toByteArray(Charsets.UTF_8), actual.stdout, "stdout for ${case.args}")
            assertContentEquals(case.stderr.toByteArray(Charsets.UTF_8), actual.stderr, "stderr for ${case.args}")
            assertEquals(case.exitCode, actual.exitCode, "exit code for ${case.args}")
        }
    }

    @Test
    fun handlesInterspersedOptionsTerminatorsContinuationsAndOperandNumbering() {
        val cases = listOf(
            ExpectedRun(
                listOf("show", "I", "--short", "Ljava/lang/String;"),
                "int\nString\n",
                "",
                0
            ),
            ExpectedRun(listOf("show", "--name=a", "()V", "--name", "b"), "void b()\n", "", 0),
            ExpectedRun(listOf("show", "--short", "--short", "Ljava/lang/String;"), "String\n", "", 0),
            ExpectedRun(listOf("show", "--name", "--", "()V"), "void --()\n", "", 0),
            ExpectedRun(listOf("show", "--", "--"), "", "ARG#1:1: JS003 not a type: '-'\n", 1),
            ExpectedRun(
                listOf("show", "I", "--short", "Q", "J"),
                "int\nlong\n",
                "ARG#2:1: JS003 not a type: 'Q'\n",
                1
            ),
            ExpectedRun(listOf("show", "-"), "", "ARG#1:1: JS003 not a type: '-'\n", 1)
        )

        cases.forEach { case ->
            val actual = runCli(case.args)
            assertContentEquals(case.stdout.toByteArray(Charsets.UTF_8), actual.stdout, "stdout for ${case.args}")
            assertContentEquals(case.stderr.toByteArray(Charsets.UTF_8), actual.stderr, "stderr for ${case.args}")
            assertEquals(case.exitCode, actual.exitCode, "exit code for ${case.args}")
        }

        var stdinInitialized = false
        val nonConsumingStdin = lazy {
            stdinInitialized = true
            byteArrayOf()
        }
        val help = runCli(listOf("--help", "show"), nonConsumingStdin)
        assertEquals(0, help.exitCode)
        assertEquals(false, stdinInitialized)

        val show = runCli(listOf("show", "I"), nonConsumingStdin)
        assertEquals(0, show.exitCode)
        assertEquals(false, stdinInitialized)

        val missingCheckOperands = runCli(listOf("check"), nonConsumingStdin)
        assertEquals(2, missingCheckOperands.exitCode)
        assertEquals(false, stdinInitialized)
    }

    @Test
    fun formatsEveryRequestedShowSyntaxDiagnosticWithoutStackTraces() {
        val cases = listOf(
            ExpectedRun(
                listOf("show", "Ljava/lang/String"),
                "",
                "ARG#1:18: JS001 unexpected end, expected ';'\n",
                1
            ),
            ExpectedRun(listOf("show", "Lpkg//A;"), "", "ARG#1:6: JS005 empty name\n", 1),
            ExpectedRun(listOf("show", "L"), "", "ARG#1:2: JS001 unexpected end, expected a name\n", 1),
            ExpectedRun(
                listOf("show", "[V"),
                "",
                "ARG#1:2: JS004 void is only allowed as a return type\n",
                1
            ),
            ExpectedRun(
                listOf("show", "(IV)V"),
                "",
                "ARG#1:3: JS004 void is only allowed as a return type\n",
                1
            ),
            ExpectedRun(
                listOf("show", "Ljava/util/List<I>;"),
                "",
                "ARG#1:17: JS011 a type argument must be a reference type\n",
                1
            ),
            ExpectedRun(
                listOf("show", "Ljava/util/List<+I>;"),
                "",
                "ARG#1:18: JS010 a wildcard bound must be a reference type\n",
                1
            ),
            ExpectedRun(
                listOf("show", "Ljava/util/List<>;"),
                "",
                "ARG#1:17: JS006 empty type argument list\n",
                1
            ),
            ExpectedRun(listOf("show", "<>V"), "", "ARG#1:2: JS009 empty type parameter list\n", 1),
            ExpectedRun(
                listOf("show", "<T>V"),
                "",
                "ARG#1:3: JS002 unexpected '>', expected ':'\n",
                1
            ),
            ExpectedRun(
                listOf("show", "Lpkg/A<TT;>/B;"),
                "",
                "ARG#1:12: JS002 unexpected '/', expected ';'\n",
                1
            ),
            ExpectedRun(
                listOf("show", "I "),
                "",
                "ARG#1:2: JS008 unexpected text after the signature\n",
                1
            ),
            ExpectedRun(
                listOf("show", "(I)V^I"),
                "",
                "ARG#1:6: JS014 a thrown type must be a class or a type variable\n",
                1
            ),
            ExpectedRun(
                listOf("show", "<T:>"),
                "",
                "ARG#1:5: JS001 unexpected end, expected '(' or the superclass\n",
                1
            ),
            ExpectedRun(listOf("show", "Q"), "", "ARG#1:1: JS003 not a type: 'Q'\n", 1),
            ExpectedRun(
                listOf("show", "Ljava/lang/Object;;"),
                "",
                "ARG#1:19: JS008 unexpected text after the signature\n",
                1
            )
        )

        cases.forEach { case ->
            val actual = runCli(case.args)
            assertContentEquals(case.stdout.toByteArray(Charsets.UTF_8), actual.stdout, "stdout for ${case.args}")
            assertContentEquals(case.stderr.toByteArray(Charsets.UTF_8), actual.stderr, "stderr for ${case.args}")
            assertEquals(case.exitCode, actual.exitCode, "exit code for ${case.args}")
        }
    }
}
