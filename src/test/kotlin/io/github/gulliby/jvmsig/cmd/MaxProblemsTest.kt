package io.github.gulliby.jvmsig.cmd

import io.github.gulliby.jvmsig.base.Output
import kotlin.test.Test
import kotlin.test.assertEquals

class MaxProblemsTest {
    private data class CliResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun runCheck(input: String, vararg args: String): CliResult {
        val output = Output()
        val exitCode = Cli.run(listOf("check") + args.toList() + "-", lazy { input.toByteArray(Charsets.UTF_8) }, output)
        return CliResult(exitCode, String(output.stdout, Charsets.UTF_8), String(output.stderr, Charsets.UTF_8))
    }

    private val bad = "I\nQ\n(I\nX\nQ\n"

    @Test
    fun stopsAfterTheNthProblemAndSaysSo() {
        val cases = listOf(
            listOf("--max-problems", "1") to
                "<stdin>:2:1: JS003 not a type: 'Q'\nchecked 2 signatures, 1 problem (stopped after 1 problem)\n",
            listOf("--max-problems", "2") to
                "<stdin>:2:1: JS003 not a type: 'Q'\n<stdin>:3:3: JS001 unexpected end, expected ')'\n" +
                "checked 3 signatures, 2 problems (stopped after 2 problems)\n",
            listOf("--max-problems=3") to
                "<stdin>:2:1: JS003 not a type: 'Q'\n<stdin>:3:3: JS001 unexpected end, expected ')'\n" +
                "<stdin>:4:1: JS003 not a type: 'X'\nchecked 4 signatures, 3 problems (stopped after 3 problems)\n"
        )
        for ((args, expected) in cases) {
            val result = runCheck(bad, *args.toTypedArray())
            assertEquals(CliResult(1, expected, ""), result, args.joinToString(" "))
        }
    }

    @Test
    fun anUnreachedOrLargerLimitChangesNothing() {
        val plain = runCheck(bad)
        assertEquals(1, plain.exitCode)
        assertEquals(
            "<stdin>:2:1: JS003 not a type: 'Q'\n<stdin>:3:3: JS001 unexpected end, expected ')'\n" +
                "<stdin>:4:1: JS003 not a type: 'X'\n<stdin>:5:1: JS003 not a type: 'Q'\nchecked 5 signatures, 4 problems\n",
            plain.stdout
        )
        assertEquals(plain, runCheck(bad, "--max-problems", "5"))
        assertEquals(plain, runCheck(bad, "--max-problems", "999"))
        assertEquals(CliResult(0, "checked 2 signatures, 0 problems\n", ""), runCheck("I\nJ\n", "--max-problems", "1"))
    }

    @Test
    fun theLimitCountsOverFilesAndLaterFilesAreNotRead() {
        val output = Output()
        val exitCode = Cli.run(
            listOf("check", "--max-problems", "1", "-", "no-such-file.txt"),
            lazy { "I\nQ\nI\n".toByteArray(Charsets.UTF_8) },
            output
        )
        assertEquals(1, exitCode)
        assertEquals(
            "<stdin>:2:1: JS003 not a type: 'Q'\nchecked 2 signatures, 1 problem (stopped after 1 problem)\n",
            String(output.stdout, Charsets.UTF_8)
        )
        assertEquals("", String(output.stderr, Charsets.UTF_8))
    }

    @Test
    fun theLimitComposesWithVarAndCommentsAndCrlf() {
        val input = "# comment\r\nTT;\r\n\r\nQ\r\n"
        val result = runCheck(input, "--var", "T", "--max-problems", "1")
        assertEquals(1, result.exitCode)
        assertEquals(
            "<stdin>:4:1: JS003 not a type: 'Q'\nchecked 2 signatures, 1 problem (stopped after 1 problem)\n",
            result.stdout
        )
    }

    @Test
    fun invalidLimitsAreUsageErrors() {
        val cases = listOf(
            "0" to "jvmsig: invalid --max-problems value: 0\n",
            "1000" to "jvmsig: invalid --max-problems value: 1000\n",
            "007" to "jvmsig: invalid --max-problems value: 007\n",
            "-1" to "jvmsig: invalid --max-problems value: -1\n",
            "x" to "jvmsig: invalid --max-problems value: x\n",
            "1.5" to "jvmsig: invalid --max-problems value: 1.5\n"
        )
        for ((value, message) in cases) {
            assertEquals(CliResult(2, "", message), runCheck("I\n", "--max-problems", value), value)
        }
        assertEquals(CliResult(2, "", "jvmsig: option --max-problems needs a non-empty value\n"), runCheck("I\n", "--max-problems="))
        val output = Output()
        val code = Cli.run(listOf("check", "-", "--max-problems"), lazy { ByteArray(0) }, output)
        assertEquals(
            CliResult(2, "", "jvmsig: option --max-problems needs a value\n"),
            CliResult(code, String(output.stdout, Charsets.UTF_8), String(output.stderr, Charsets.UTF_8))
        )
    }

    @Test
    fun otherCommandsDoNotTakeTheOption() {
        for (command in listOf("show", "erase", "slots")) {
            val output = Output()
            val code = Cli.run(listOf(command, "--max-problems", "1", "I"), lazy { ByteArray(0) }, output)
            assertEquals(2, code, command)
            assertEquals("jvmsig: unknown option --max-problems\n", String(output.stderr, Charsets.UTF_8), command)
        }
    }
}
