package io.github.gulliby.jvmsig.cmd

import io.github.gulliby.jvmsig.base.Output
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocsTest {
    @Test
    fun guideDocumentsPurposeBuildCommandsAndPlatformContract() {
        val root = projectRoot()
        val readme = String(Files.readAllBytes(root.resolve("README.md")), Charsets.UTF_8)

        assertTrue(Files.isRegularFile(root.resolve("README.md")))
        assertTrue(readme.contains("offline Kotlin/JVM command-line tool"))
        assertTrue(readme.contains("people working with bytecode and native-method code"))
        assertTrue(readme.contains("only the Kotlin and Java standard libraries"))
        assertTrue(readme.contains("never accesses the network"))
        assertTrue(readme.contains("gradle installDist"))
        assertTrue(readme.contains("gradle test"))
        assertTrue(readme.contains("`build/install/jvmsig/bin/jvmsig`"))
        assertTrue(readme.contains("`build/install/jvmsig/bin/jvmsig.bat`"))
        assertTrue(readme.contains("In the examples below, `jvmsig` means the installed launcher"))
        assertTrue(readme.contains("POSIX-compatible shell"))
        assertTrue(readme.contains("Git Bash"))
        assertTrue(readme.contains("Windows"))
        assertTrue(readme.contains("macOS"))
        assertTrue(readme.contains("Linux"))
        assertTrue(readme.contains("UTF-8"))
        assertTrue(readme.contains("LF"))
        assertTrue(readme.contains("Exit status is `0`"))
        assertTrue(readme.contains("`1` when one or more signatures have problems"))
        assertTrue(readme.contains("`2` for command-line errors"))
        assertTrue(readme.contains("jvmsig show [--kotlin] [--short] [--name NAME] SIGNATURE..."))
        assertTrue(readme.contains("jvmsig check [--var NAME]... FILE..."))
        assertTrue(readme.contains("jvmsig erase SIGNATURE..."))
        assertTrue(readme.contains("jvmsig slots [--static] SIGNATURE..."))
        assertTrue(readme.contains("jvmsig jni CLASS METHOD [DESCRIPTOR]"))
        assertTrue(readme.contains("`--kotlin`"))
        assertTrue(readme.contains("`--short`"))
        assertTrue(readme.contains("`--name NAME`"))
        assertTrue(readme.contains("`--var NAME`"))
        assertTrue(readme.contains("`--static`"))
        assertTrue(readme.contains("overloaded-name suffix"))
    }

    @Test
    fun diagnosticTableCoversAllCodesAndTriggers() {
        val readme = String(Files.readAllBytes(projectRoot().resolve("README.md")), Charsets.UTF_8)
        val expectedCodes = listOf(
            "JS001", "JS002", "JS003", "JS004", "JS005", "JS006", "JS007", "JS008",
            "JS009", "JS010", "JS011", "JS012", "JS013", "JS014", "JS015", "JS016", "JS017"
        )
        val tableCodes = Regex("""\| (JS\d{3}) \|""")
            .findAll(readme)
            .map { it.groupValues[1] }
            .toList()
        assertEquals(expectedCodes, tableCodes)

        val expectedRows = listOf(
            "| JS001 | `unexpected end, expected WHAT` | One past the last code point when the signature ends where more input is required. |",
            "| JS002 | `unexpected CHAR, expected WHAT` | At the wrong code point where one specific character is required. |",
            "| JS003 | `not a type: CHAR` | At a code point that cannot start a type where a type is required. |",
            "| JS004 | `void is only allowed as a return type` | At `V` in any position other than a method return type. |",
            "| JS005 | `empty name` | Where a name is required but a name-ending delimiter appears; end of input instead gives JS001. |",
            "| JS006 | `empty type argument list` | At `>` in an empty class type-argument list, `<>`. |",
            "| JS007 | `more than 255 array dimensions` | At the 256th `[` in an array type. |",
            "| JS008 | `unexpected text after the signature` | At the first code point after a complete signature. |",
            "| JS009 | `empty type parameter list` | At `>` in an empty leading type-parameter list, `<>`. |",
            "| JS010 | `a wildcard bound must be a reference type` | At a primitive type following `+` or `-` in a wildcard bound. |",
            "| JS011 | `a type argument must be a reference type` | At a primitive used as a type argument. |",
            "| JS012 | `type variable NAME is not declared` | At the `T` starting an undeclared type-variable use; `check` only. |",
            "| JS013 | `type parameter NAME is declared twice` | At the name of the second or later declaration; `check` only. |",
            "| JS014 | `a thrown type must be a class or a type variable` | After `^` when the next type is neither a class nor a type variable (`V` instead gives JS004). |",
            "| JS015 | `type parameter NAME has a cyclic bound` | At the name of a type parameter whose first-bound chain returns to itself; `check` only. |",
            "| JS016 | `a method signature is required` | At column 1 when `slots`, or `jni` with a descriptor, receives a non-method signature. |",
            "| JS017 | `more than 255 argument slots` | At column 1 when `slots` would use more than 255 slots in total. |"
        )
        expectedRows.forEach { assertTrue(readme.contains(it), "Missing diagnostic row: $it") }
        assertTrue(readme.contains("one-based Unicode code-point positions"))
        assertTrue(readme.contains("`CHAR`"))
        assertTrue(readme.contains("`WHAT`"))
        assertTrue(readme.contains("`NAME`"))
        assertTrue(readme.contains("uppercase `U+` hexadecimal"))
        assertTrue(readme.contains("semantic diagnostics JS012, JS013, and JS015"))
    }

    @Test
    fun mitLicenseContainsStandardTermsAndAttribution() {
        val license = String(Files.readAllBytes(projectRoot().resolve("LICENSE")), Charsets.UTF_8)

        assertTrue(license.contains("MIT License"))
        assertTrue(license.contains("Copyright (c) Gulliby-development"))
        assertTrue(license.contains("Permission is hereby granted, free of charge"))
        assertTrue(license.contains("to deal\nin the Software without restriction"))
        assertTrue(license.contains("The above copyright notice and this permission notice"))
        assertTrue(license.contains("THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND"))
        assertTrue(license.contains("IN NO EVENT SHALL THE\nAUTHORS OR COPYRIGHT HOLDERS BE LIABLE"))
    }

    @Test
    fun javaShowExampleMatchesCliAndReadme() {
        val readme = String(Files.readAllBytes(projectRoot().resolve("README.md")), Charsets.UTF_8)
        assertTrue(readme.contains("$ jvmsig show I\nint\n"))

        val result = runCli(listOf("show", "I"))
        assertEquals(0, result.first)
        assertEquals("int\n", result.second)
        assertEquals("", result.third)
    }

    @Test
    fun kotlinShowExampleMatchesCliAndReadme() {
        val readme = String(Files.readAllBytes(projectRoot().resolve("README.md")), Charsets.UTF_8)
        assertTrue(readme.contains("\$ jvmsig show --kotlin '[Ljava/lang/String;'\nArray<java.lang.String>\n"))

        val result = runCli(listOf("show", "--kotlin", "[Ljava/lang/String;"))
        assertEquals(0, result.first)
        assertEquals("Array<java.lang.String>\n", result.second)
        assertEquals("", result.third)
    }

    @Test
    fun checkFileExampleMatchesCliAndReadme() {
        val readme = String(Files.readAllBytes(projectRoot().resolve("README.md")), Charsets.UTF_8)
        assertTrue(readme.contains("$ printf 'I\\n' > ok.txt\n$ jvmsig check ok.txt\nchecked 1 signature, 0 problems\n"))

        val directory = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), "jvmsig-docs-")
        val fixture = directory.resolve("ok.txt")
        try {
            Files.write(fixture, "I\n".toByteArray(Charsets.UTF_8))
            val result = runCli(listOf("check", fixture.toRealPath().toString()))
            assertEquals(0, result.first)
            assertEquals("checked 1 signature, 0 problems\n", result.second)
            assertEquals("", result.third)
        } finally {
            Files.deleteIfExists(fixture)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun erasureExampleMatchesCliAndReadme() {
        val readme = String(Files.readAllBytes(projectRoot().resolve("README.md")), Charsets.UTF_8)
        assertTrue(readme.contains("$ jvmsig erase 'TT;'\nLjava/lang/Object;\n"))

        val result = runCli(listOf("erase", "TT;"))
        assertEquals(0, result.first)
        assertEquals("Ljava/lang/Object;\n", result.second)
        assertEquals("", result.third)
    }

    @Test
    fun slotsExampleMatchesCliAndReadme() {
        val readme = String(Files.readAllBytes(projectRoot().resolve("README.md")), Charsets.UTF_8)
        assertTrue(readme.contains("$ jvmsig slots --static '()V'\nSLOT  ARG  TYPE\ntotal 0 slots\nreturns void\n"))

        val result = runCli(listOf("slots", "--static", "()V"))
        assertEquals(0, result.first)
        assertEquals("SLOT  ARG  TYPE\ntotal 0 slots\nreturns void\n", result.second)
        assertEquals("", result.third)
    }

    @Test
    fun jniExampleMatchesCliAndReadme() {
        val readme = String(Files.readAllBytes(projectRoot().resolve("README.md")), Charsets.UTF_8)
        assertTrue(readme.contains("$ jvmsig jni com.acme.Foo run\nJava_com_acme_Foo_run\n"))

        val result = runCli(listOf("jni", "com.acme.Foo", "run"))
        assertEquals(0, result.first)
        assertEquals("Java_com_acme_Foo_run\n", result.second)
        assertEquals("", result.third)
    }

    private fun projectRoot(): Path {
        val codeSource = Paths.get(DocsTest::class.java.protectionDomain.codeSource.location.toURI())
        return generateSequence(codeSource) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("README.md")) }
            ?: error("Could not locate project README.md from test code source")
    }

    private fun runCli(args: List<String>): Triple<Int, String, String> {
        val output = Output()
        val status = Cli.run(args, lazy { byteArrayOf() }, output)
        return Triple(
            status,
            String(output.stdout, Charsets.UTF_8),
            String(output.stderr, Charsets.UTF_8)
        )
    }
}
