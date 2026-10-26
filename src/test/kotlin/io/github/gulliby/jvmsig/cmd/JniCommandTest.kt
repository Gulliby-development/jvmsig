package io.github.gulliby.jvmsig.cmd

import io.github.gulliby.jvmsig.base.Output
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class JniCommandTest {
    private data class Case(
        val args: List<String>,
        val stdout: String,
        val stderr: String,
        val exitCode: Int
    )

    @Test
    fun matchesShortNameAndUtf16EscapeExamples() {
        val cases = listOf(
            Case(listOf("jni", "com.acme.Foo", "run"), "Java_com_acme_Foo_run\n", "", 0),
            Case(
                listOf("jni", "my_pkg/Foo_Bar\$In\$ner", "go_1"),
                "Java_my_1pkg_Foo_1Bar_00024In_00024ner_go_11\n",
                "",
                0
            ),
            Case(
                listOf("jni", "a.B", "\u00e9\u4e2d\ud83d\ude00"),
                "Java_a_B__000e9_04e2d_0d83d_0de00\n",
                "",
                0
            ),
            Case(
                listOf("jni", "a.B", "\u00e9"),
                "Java_a_B__000e9\n",
                "",
                0
            )
        )

        cases.forEach { case ->
            assertCase(case)
        }
    }

    @Test
    fun matchesOverloadedDescriptorExamplesAndGenericErasure() {
        val cases = listOf(
            Case(
                listOf("jni", "a.B", "run", "(ILjava/lang/String;[D)V"),
                "Java_a_B_run__ILjava_lang_String_2_3D\n",
                "",
                0
            ),
            Case(
                listOf("jni", "a.B", "f", "(Ljava/util/Map\$Entry;[[JLjava/lang/String;C)V"),
                "Java_a_B_f__Ljava_util_Map_00024Entry_2_3_3JLjava_lang_String_2C\n",
                "",
                0
            ),
            Case(
                listOf("jni", "a.B", "f", "<T:Ljava/lang/Number;>(TT;)V"),
                "Java_a_B_f__Ljava_lang_Number_2\n",
                "",
                0
            ),
            Case(listOf("jni", "a.B", "f", "()V"), "Java_a_B_f__\n", "", 0),
            Case(listOf("jni", "a.B", "f", "(Lx)y;)V"), "Java_a_B_f__Lx_00029y_2\n", "", 0)
        )

        cases.forEach { case ->
            assertCase(case)
        }
    }

    @Test
    fun reportsDescriptorSyntaxAndNonMethodProblemsAtOperandThree() {
        val cases = listOf(
            Case(
                listOf("jni", "a.B", "f", "Q"),
                "",
                "ARG#3:1: JS003 not a type: 'Q'\n",
                1
            ),
            Case(
                listOf("jni", "a.B", "f", "I"),
                "",
                "ARG#3:1: JS016 a method signature is required\n",
                1
            )
        )

        cases.forEach { case ->
            assertCase(case)
        }
    }

    @Test
    fun rejectsEmptyAndMalformedClassNamesWithExactMessages() {
        val invalidNames = listOf(
            "" to "jvmsig: invalid class name: \n",
            "/" to "jvmsig: invalid class name: /\n",
            "." to "jvmsig: invalid class name: .\n",
            "/a" to "jvmsig: invalid class name: /a\n",
            "a/" to "jvmsig: invalid class name: a/\n",
            ".a" to "jvmsig: invalid class name: .a\n",
            "a." to "jvmsig: invalid class name: a.\n",
            "a//b" to "jvmsig: invalid class name: a//b\n",
            "a..b" to "jvmsig: invalid class name: a..b\n",
            "a./b" to "jvmsig: invalid class name: a./b\n",
            "a/.b" to "jvmsig: invalid class name: a/.b\n",
            ";" to "jvmsig: invalid class name: ;\n",
            "[" to "jvmsig: invalid class name: [\n",
            "<" to "jvmsig: invalid class name: <\n",
            ">" to "jvmsig: invalid class name: >\n"
        )

        invalidNames.forEach { (name, expectedStderr) ->
            assertCase(Case(listOf("jni", name, "run"), "", expectedStderr, 2))
        }
    }

    @Test
    fun rejectsEmptyAndForbiddenMethodNamesWithExactMessages() {
        val invalidNames = listOf(
            "" to "jvmsig: invalid method name: \n",
            "." to "jvmsig: invalid method name: .\n",
            ";" to "jvmsig: invalid method name: ;\n",
            "[" to "jvmsig: invalid method name: [\n",
            "/" to "jvmsig: invalid method name: /\n",
            "<" to "jvmsig: invalid method name: <\n",
            ">" to "jvmsig: invalid method name: >\n"
        )

        invalidNames.forEach { (name, expectedStderr) ->
            assertCase(Case(listOf("jni", "a.B", name), "", expectedStderr, 2))
        }
    }

    @Test
    fun validatesNamesBeforeParsingDescriptorsAndAcceptsPermissiveNames() {
        val cases = listOf(
            Case(
                listOf("jni", "a($:)/b", "go $:()"),
                "Java_a_00028_00024_0003a_00029_b_go_00020_00024_0003a_00028_00029\n",
                "",
                0
            ),
            Case(listOf("jni", "a.b/C", "\$"), "Java_a_b_C__00024\n", "", 0),
            Case(
                listOf("jni", "a..b", "m.x", "Q"),
                "",
                "jvmsig: invalid class name: a..b\n",
                2
            ),
            Case(
                listOf("jni", "a.B", "m.x", "Q"),
                "",
                "jvmsig: invalid method name: m.x\n",
                2
            )
        )

        cases.forEach { case ->
            assertCase(case)
        }
    }

    private fun assertCase(case: Case) {
        val output = Output()
        val exitCode = Cli.run(case.args, lazy { ByteArray(0) }, output)

        assertContentEquals(case.stdout.toByteArray(Charsets.UTF_8), output.stdout, "stdout for ${case.args}")
        assertContentEquals(case.stderr.toByteArray(Charsets.UTF_8), output.stderr, "stderr for ${case.args}")
        assertEquals(case.exitCode, exitCode, "exit code for ${case.args}")
    }
}
