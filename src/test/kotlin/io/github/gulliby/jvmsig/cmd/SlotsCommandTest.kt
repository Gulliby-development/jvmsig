package io.github.gulliby.jvmsig.cmd

import io.github.gulliby.jvmsig.base.Output
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlotsCommandTest {
    @Test
    fun documentedInstanceTableMatchesItsLiteralExample() {
        val result = runCli("slots", "(IJLjava/lang/String;D[J)V")

        assertEquals(0, result.exitCode)
        assertContentEquals(
            (
                "SLOT  ARG   TYPE\n" +
                    "0     this\n" +
                    "1     0     int\n" +
                    "2-3   1     long\n" +
                    "4     2     java.lang.String\n" +
                    "5-6   3     double\n" +
                    "7     4     long[]\n" +
                    "total 8 slots\n" +
                    "returns void\n"
                ).toByteArray(Charsets.UTF_8),
            result.stdout
        )
        assertContentEquals(ByteArray(0), result.stderr)
    }

    @Test
    fun documentedStaticGenericAndEmptyTablesMatchTheirLiteralExamples() {
        val generic = runCli("slots", "--static", "<T:Ljava/lang/Number;>(TT;JZ)TT;")

        assertEquals(0, generic.exitCode)
        assertContentEquals(
            (
                "SLOT  ARG  TYPE\n" +
                    "0     0    java.lang.Number\n" +
                    "1-2   1    long\n" +
                    "3     2    boolean\n" +
                    "total 4 slots\n" +
                    "returns java.lang.Number\n"
                ).toByteArray(Charsets.UTF_8),
            generic.stdout
        )
        assertContentEquals(ByteArray(0), generic.stderr)

        val emptyStatic = runCli("slots", "--static", "()V")

        assertEquals(0, emptyStatic.exitCode)
        assertContentEquals(
            (
                "SLOT  ARG  TYPE\n" +
                    "total 0 slots\n" +
                    "returns void\n"
                ).toByteArray(Charsets.UTF_8),
            emptyStatic.stdout
        )
        assertContentEquals(ByteArray(0), emptyStatic.stderr)
    }

    @Test
    fun staticAndInstanceEmptyMethodsHaveTheirLiteralReceiverTotals() {
        val emptyInstance = runCli("slots", "()V")

        assertEquals(0, emptyInstance.exitCode)
        assertContentEquals(
            (
                "SLOT  ARG   TYPE\n" +
                    "0     this\n" +
                    "total 1 slot\n" +
                    "returns void\n"
                ).toByteArray(Charsets.UTF_8),
            emptyInstance.stdout
        )
        assertContentEquals(ByteArray(0), emptyInstance.stderr)
    }

    @Test
    fun documentedMultipleTablesHaveOneBlankLineAndKeepTheOperandDiagnostic() {
        val result = runCli("slots", "(I)V", "I", "(J)J")

        assertEquals(1, result.exitCode)
        assertContentEquals(
            (
                "SLOT  ARG   TYPE\n" +
                    "0     this\n" +
                    "1     0     int\n" +
                    "total 2 slots\n" +
                    "returns void\n" +
                    "\n" +
                    "SLOT  ARG   TYPE\n" +
                    "0     this\n" +
                    "1-2   0     long\n" +
                    "total 3 slots\n" +
                    "returns long\n"
                ).toByteArray(Charsets.UTF_8),
            result.stdout
        )
        assertContentEquals(
            "ARG#2:1: JS016 a method signature is required\n".toByteArray(Charsets.UTF_8),
            result.stderr
        )
    }

    @Test
    fun failuresBeforeBetweenAndAfterTablesDoNotAddSeparatorsOrStopProcessing() {
        val result = runCli("slots", "I", "()V", "Q", "()V", "I")

        assertEquals(1, result.exitCode)
        assertContentEquals(
            (
                "SLOT  ARG   TYPE\n" +
                    "0     this\n" +
                    "total 1 slot\n" +
                    "returns void\n" +
                    "\n" +
                    "SLOT  ARG   TYPE\n" +
                    "0     this\n" +
                    "total 1 slot\n" +
                    "returns void\n"
                ).toByteArray(Charsets.UTF_8),
            result.stdout
        )
        assertContentEquals(
            (
                "ARG#1:1: JS016 a method signature is required\n" +
                    "ARG#3:1: JS003 not a type: 'Q'\n" +
                    "ARG#5:1: JS016 a method signature is required\n"
                ).toByteArray(Charsets.UTF_8),
            result.stderr
        )
    }

    @Test
    fun instanceAndStaticBoundariesAccept255AndReject256Slots() {
        val instance255 = runCli("slots", "(" + "J".repeat(127) + ")V")

        assertEquals(0, instance255.exitCode)
        assertEquals(
            "SLOT     ARG   TYPE\n",
            String(instance255.stdout, Charsets.UTF_8).substringBefore('\n') + "\n"
        )
        assertTrue(
            String(instance255.stdout, Charsets.UTF_8).endsWith(
                "253-254  126   long\n" +
                    "total 255 slots\n" +
                    "returns void\n"
            )
        )
        assertContentEquals(ByteArray(0), instance255.stderr)

        val instance256 = runCli("slots", "(" + "J".repeat(128) + ")V")

        assertEquals(1, instance256.exitCode)
        assertContentEquals(ByteArray(0), instance256.stdout)
        assertContentEquals(
            "ARG#1:1: JS017 more than 255 argument slots\n".toByteArray(Charsets.UTF_8),
            instance256.stderr
        )

        val static255 = runCli("slots", "--static", "(" + "J".repeat(127) + "I)V")

        assertEquals(0, static255.exitCode)
        assertEquals(
            "SLOT     ARG  TYPE\n",
            String(static255.stdout, Charsets.UTF_8).substringBefore('\n') + "\n"
        )
        assertTrue(
            String(static255.stdout, Charsets.UTF_8).endsWith(
                "252-253  126  long\n" +
                    "254      127  int\n" +
                    "total 255 slots\n" +
                    "returns void\n"
            )
        )
        assertContentEquals(ByteArray(0), static255.stderr)

        val static256 = runCli("slots", "--static", "(" + "J".repeat(127) + "II)V")

        assertEquals(1, static256.exitCode)
        assertContentEquals(ByteArray(0), static256.stdout)
        assertContentEquals(
            "ARG#1:1: JS017 more than 255 argument slots\n".toByteArray(Charsets.UTF_8),
            static256.stderr
        )
    }

    private data class CliResult(
        val exitCode: Int,
        val stdout: ByteArray,
        val stderr: ByteArray
    )

    private fun runCli(vararg args: String): CliResult {
        val output = Output()
        val exitCode = Cli.run(args.toList(), lazyOf(ByteArray(0)), output)
        return CliResult(exitCode, output.stdout, output.stderr)
    }
}
