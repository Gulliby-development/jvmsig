package io.github.gulliby.jvmsig.cmd

import io.github.gulliby.jvmsig.base.Output
import io.github.gulliby.jvmsig.base.Version
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class VersionTest {
    private fun run(vararg args: String): Triple<Int, String, String> {
        val output = Output()
        val exitCode = Cli.run(args.toList(), lazy { byteArrayOf() }, output)
        return Triple(exitCode, String(output.stdout, Charsets.UTF_8), String(output.stderr, Charsets.UTF_8))
    }

    @Test
    fun versionTextComesFromTheBuildVersion() {
        assertEquals("0.1.0", Version.text)
    }

    @Test
    fun longAndShortFlagsPrintTheVersionAsTheFirstArgument() {
        assertEquals(Triple(0, "jvmsig 0.1.0\n", ""), run("--version"))
        assertEquals(Triple(0, "jvmsig 0.1.0\n", ""), run("-V"))
        assertEquals(Triple(0, "jvmsig 0.1.0\n", ""), run("--version", "bogus"))
    }

    @Test
    fun versionIsNotAnOptionOfTheCommands() {
        assertEquals(Triple(2, "", "jvmsig: unknown option --version\n"), run("show", "--version", "I"))
        assertEquals(Triple(2, "", "jvmsig: unknown option -V\n"), run("erase", "-V", "I"))
    }

    @Test
    fun helpStillComesBeforeVersion() {
        val result = run("--help", "--version")
        assertEquals(0, result.first)
        assertContentEquals(
            ("usage: jvmsig show [--kotlin] [--short] [--name NAME] SIGNATURE...\n" +
                "       jvmsig check [--var NAME]... FILE...\n" +
                "       jvmsig erase SIGNATURE...\n" +
                "       jvmsig slots [--static] SIGNATURE...\n" +
                "       jvmsig jni CLASS METHOD [DESCRIPTOR]\n" +
                "       jvmsig --version\n").toByteArray(Charsets.UTF_8),
            result.second.toByteArray(Charsets.UTF_8)
        )
    }
}
