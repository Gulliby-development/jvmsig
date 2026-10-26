package io.github.gulliby.jvmsig.jni

import io.github.gulliby.jvmsig.base.Failure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JniTest {
    @Test
    fun preservesAsciiRangesAndTheirAdjacentPunctuation() {
        assertEquals("Java_C_AZaz09", Jni.shortName("C", "AZaz09"))

        val cases = listOf(
            "@" to "Java_C__00040",
            "`" to "Java_C__00060",
            "{" to "Java_C__0007b",
            "9:" to "Java_C_9_0003a"
        )
        for ((methodName, expected) in cases) {
            assertEquals(expected, Jni.shortName("C", methodName), methodName)
        }
        assertEquals("Java_C_m__Z_3", Jni.overloadedName("C", "m", "Z["))
        assertEquals("Java_C_m___0", Jni.overloadedName("C", "m", "/0"))
    }

    @Test
    fun appliesEachJniEscapeToOverloadParameterText() {
        val cases = listOf(
            "_" to "Java_C_m___1",
            ";" to "Java_C_m___2",
            "[" to "Java_C_m___3",
            "/" to "Java_C_m___",
            "." to "Java_C_m___0002e"
        )
        for ((parameterText, expected) in cases) {
            assertEquals(expected, Jni.overloadedName("C", "m", parameterText), parameterText)
        }
    }

    @Test
    fun normalizesClassSeparatorsAndPreservesOverloadExamples() {
        assertEquals("Java_com_acme_Foo_run", Jni.shortName("com.acme.Foo", "run"))
        assertEquals(
            "Java_my_1pkg_Foo_1Bar_00024In_00024ner_go_11",
            Jni.shortName("my_pkg/Foo_Bar\$In\$ner", "go_1")
        )
        assertEquals("Java_a_b_C_m", Jni.shortName("a.b/C", "m"))
        assertEquals("Java_a_b_C_m", Jni.shortName("a/b.C", "m"))

        assertEquals(
            "Java_a_B_run__ILjava_lang_String_2_3D",
            Jni.overloadedName("a.B", "run", "ILjava/lang/String;[D")
        )
        assertEquals(
            "Java_a_B_f__Ljava_util_Map_00024Entry_2_3_3JLjava_lang_String_2C",
            Jni.overloadedName("a.B", "f", "Ljava/util/Map\$Entry;[[JLjava/lang/String;C")
        )
        assertEquals(
            "Java_a_B_f__Ljava_lang_Number_2",
            Jni.overloadedName("a.B", "f", "Ljava/lang/Number;")
        )
        assertEquals("Java_a_B_f__", Jni.overloadedName("a.B", "f", ""))
    }

    @Test
    fun escapesParameterDotsInsteadOfNormalizingThemAsClassDots() {
        assertEquals("Java_C_m__Lfoo_0002ebar_2", Jni.overloadedName("C", "m", "Lfoo.bar;"))
    }

    @Test
    fun escapesOtherLegalNonIdentifierCharacters() {
        assertEquals("Java__00024_m", Jni.shortName("$", "m"))
        assertEquals(
            "Java_C_go_00028_00020_00029_0003a",
            Jni.shortName("C", "go( ):")
        )
        assertEquals("Java_C_00024_00028_m", Jni.shortName("C$(", "m"))
    }

    @Test
    fun escapesUnicodeByUtf16CodeUnit() {
        assertEquals("Java_C__000e9", Jni.shortName("C", "\u00e9"))
        assertEquals("Java_C__04e2d", Jni.shortName("C", "\u4e2d"))
        assertEquals("Java_C__0d83d_0de00", Jni.shortName("C", "\uD83D\uDE00"))
        assertEquals("Java_C__0d83d", Jni.shortName("C", "\uD83D"))
        assertEquals("Java_C__0de00", Jni.shortName("C", "\uDE00"))
        assertEquals("Java_C__00001", Jni.shortName("C", "\u0001"))
    }

    @Test
    fun acceptsNamePartsWithoutApplyingJavaIdentifierRules() {
        Jni.validateClassName("a($:)/b")
        Jni.validateClassName("a.b/C")
        Jni.validateMethodName("go $:()")
        Jni.validateMethodName("$")
    }

    @Test
    fun rejectsEveryForbiddenClassNameFormWithOriginalInput() {
        val cases = listOf(
            "" to "invalid class name: ",
            "/" to "invalid class name: /",
            "." to "invalid class name: .",
            "/a" to "invalid class name: /a",
            "a/" to "invalid class name: a/",
            ".a" to "invalid class name: .a",
            "a." to "invalid class name: a.",
            "a//b" to "invalid class name: a//b",
            "a..b" to "invalid class name: a..b",
            "a./b" to "invalid class name: a./b",
            "a/.b" to "invalid class name: a/.b",
            ";" to "invalid class name: ;",
            "[" to "invalid class name: [",
            "<" to "invalid class name: <",
            ">" to "invalid class name: >"
        )
        for ((name, message) in cases) {
            assertFailure(message) { Jni.validateClassName(name) }
        }
    }

    @Test
    fun rejectsEveryForbiddenMethodNameWithOriginalInput() {
        val cases = listOf(
            "" to "invalid method name: ",
            "." to "invalid method name: .",
            ";" to "invalid method name: ;",
            "[" to "invalid method name: [",
            "/" to "invalid method name: /",
            "<" to "invalid method name: <",
            ">" to "invalid method name: >"
        )
        for ((name, message) in cases) {
            assertFailure(message) { Jni.validateMethodName(name) }
        }
    }

    @Test
    fun validatesClassBeforeMethodWhenBothAreInvalid() {
        val failure = assertFailsWith<Failure> {
            Jni.shortName("a..b", "m.x")
        }
        assertEquals(2, failure.exitCode)
        assertEquals("invalid class name: a..b", failure.message)

        val overloadedFailure = assertFailsWith<Failure> {
            Jni.overloadedName("a..b", "m.x", "")
        }
        assertEquals(2, overloadedFailure.exitCode)
        assertEquals("invalid class name: a..b", overloadedFailure.message)
    }

    private fun assertFailure(expectedMessage: String, action: () -> Unit) {
        val failure = assertFailsWith<Failure>(block = action)
        assertEquals(2, failure.exitCode)
        assertEquals(expectedMessage, failure.message)
    }
}
