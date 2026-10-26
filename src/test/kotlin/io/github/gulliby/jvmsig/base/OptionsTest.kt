package io.github.gulliby.jvmsig.base

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OptionsTest {
    private val spec = mapOf(
        "--static" to Options.Kind.FLAG,
        "--name" to Options.Kind.VALUE,
        "--var" to Options.Kind.MULTI
    )

    private data class SuccessCase(
        val args: List<String>,
        val flags: Set<String>,
        val values: Map<String, String>,
        val multi: Map<String, List<String>>,
        val operands: List<String>
    )

    private data class FailureCase(
        val args: List<String>,
        val message: String
    )

    @Test
    fun parsesOptionsAndOperandsIntoIndependentCollections() {
        val cases = listOf(
            SuccessCase(
                args = emptyList(),
                flags = emptySet(),
                values = emptyMap(),
                multi = emptyMap(),
                operands = emptyList()
            ),
            SuccessCase(
                args = listOf("I", "--static", "J", "--static", "-"),
                flags = setOf("--static"),
                values = emptyMap(),
                multi = emptyMap(),
                operands = listOf("I", "J", "-")
            ),
            SuccessCase(
                args = listOf("--name", "first", "I", "--name=last=tail"),
                flags = emptySet(),
                values = mapOf("--name" to "last=tail"),
                multi = emptyMap(),
                operands = listOf("I")
            ),
            SuccessCase(
                args = listOf("--var=T", "--var", "T", "--var", "U"),
                flags = emptySet(),
                values = emptyMap(),
                multi = mapOf("--var" to listOf("T", "T", "U")),
                operands = emptyList()
            ),
            SuccessCase(
                args = listOf("I", "--", "--static", "--", "-"),
                flags = emptySet(),
                values = emptyMap(),
                multi = emptyMap(),
                operands = listOf("I", "--static", "--", "-")
            ),
            SuccessCase(
                args = listOf("--name", "--", "--static"),
                flags = setOf("--static"),
                values = mapOf("--name" to "--"),
                multi = emptyMap(),
                operands = emptyList()
            ),
            SuccessCase(
                args = listOf("--var", "a.b"),
                flags = emptySet(),
                values = emptyMap(),
                multi = mapOf("--var" to listOf("a.b")),
                operands = emptyList()
            )
        )

        cases.forEach { case ->
            val actual = Options.parse(case.args, spec)
            assertEquals(case.flags, actual.flags, "flags for ${case.args}")
            assertEquals(case.values, actual.values, "values for ${case.args}")
            assertEquals(case.multi, actual.multi, "multi for ${case.args}")
            assertEquals(case.operands, actual.operands, "operands for ${case.args}")
        }
    }

    @Test
    fun reportsExactFailuresAtTheFirstInvalidOption() {
        val cases = listOf(
            FailureCase(listOf("--x=1"), "unknown option --x"),
            FailureCase(listOf("-x"), "unknown option -x"),
            FailureCase(listOf("--static=1"), "option --static takes no value"),
            FailureCase(listOf("--static="), "option --static takes no value"),
            FailureCase(listOf("--name"), "option --name needs a value"),
            FailureCase(listOf("--name="), "option --name needs a non-empty value"),
            FailureCase(listOf("--name", ""), "option --name needs a non-empty value"),
            FailureCase(listOf("--var"), "option --var needs a value"),
            FailureCase(listOf("--var="), "option --var needs a non-empty value"),
            FailureCase(listOf("--x", "--name"), "unknown option --x")
        )

        cases.forEach { case ->
            val failure = assertFailsWith<Failure> {
                Options.parse(case.args, spec)
            }
            assertEquals(2, failure.exitCode, "exit code for ${case.args}")
            assertEquals(case.message, failure.message, "message for ${case.args}")
        }
    }

    @Test
    fun failureExposesTheSuppliedExitCodeAndMessage() {
        val failure = Failure(7, "sample failure")

        assertEquals(7, failure.exitCode)
        assertEquals("sample failure", failure.message)
    }
}
