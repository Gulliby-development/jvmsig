package io.github.gulliby.jvmsig.read

import io.github.gulliby.jvmsig.model.Problem
import io.github.gulliby.jvmsig.model.Ty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TypeReaderTest {
    private data class ErrorCase(
        val input: String,
        val code: String,
        val column: Int,
        val text: String
    )

    @Test
    fun readsPrimitiveArrayVariableClassAndArgumentTrees() {
        val cases = listOf(
            "B" to Ty.Prim('B'),
            "C" to Ty.Prim('C'),
            "D" to Ty.Prim('D'),
            "F" to Ty.Prim('F'),
            "I" to Ty.Prim('I'),
            "J" to Ty.Prim('J'),
            "S" to Ty.Prim('S'),
            "Z" to Ty.Prim('Z'),
            "[[J" to Ty.Arr(Ty.Arr(Ty.Prim('J'))),
            "TT;" to Ty.Var("T"),
            "Ljava.lang.String;" to Ty.Cls(
                listOf(
                    Ty.Part("java", null),
                    Ty.Part("lang", null),
                    Ty.Part("String", null)
                )
            ),
            "Ljava/util/Map<TK;[TV;>.Entry<*>;" to Ty.Cls(
                listOf(
                    Ty.Part(
                        "java/util/Map",
                        listOf(Ty.Var("K"), Ty.Arr(Ty.Var("V")))
                    ),
                    Ty.Part("Entry", listOf(Ty.Star))
                )
            ),
            "La<+TT;-Ljava/lang/Object;[I>;" to Ty.Cls(
                listOf(
                    Ty.Part(
                        "a",
                        listOf(
                            Ty.Bound(true, Ty.Var("T")),
                            Ty.Bound(
                                false,
                                Ty.Cls(listOf(Ty.Part("java/lang/Object", null)))
                            ),
                            Ty.Arr(Ty.Prim('I'))
                        )
                    )
                )
            ),
            "Lx$ _()^+-*;" to Ty.Cls(listOf(Ty.Part("x$ _()^+-*", null)))
        )

        for ((input, expected) in cases) {
            assertEquals(expected, TypeReader(Cursor(input)).readType(), input)
        }
    }

    @Test
    fun preservesUnicodeNamesAndVariableUseOrderAndColumns() {
        val supplementary = TypeReader(Cursor("L\uD800\uDC3B;")).readType()
        assertEquals(
            Ty.Cls(listOf(Ty.Part("\uD800\uDC3B", null))),
            supplementary
        )

        val nonAscii = TypeReader(Cursor("L\u00E9/\u4E2D;")).readType()
        assertEquals(Ty.Cls(listOf(Ty.Part("\u00E9/\u4E2D", null))), nonAscii)

        val reader = TypeReader(Cursor("L\uD83D\uDE00<TT;TX;>;"))
        assertEquals(
            Ty.Cls(
                listOf(
                    Ty.Part(
                        "\uD83D\uDE00",
                        listOf(Ty.Var("T"), Ty.Var("X"))
                    )
                )
            ),
            reader.readType()
        )
        assertEquals(listOf("T" to 4, "X" to 7), reader.uses)
    }

    @Test
    fun reportsTypeContextEndOfInputExpectations() {
        val cases = listOf(
            ErrorCase("", "JS001", 1, "unexpected end, expected a type"),
            ErrorCase("L", "JS001", 2, "unexpected end, expected a name"),
            ErrorCase("T", "JS001", 2, "unexpected end, expected a name"),
            ErrorCase("La/", "JS001", 4, "unexpected end, expected a name"),
            ErrorCase("La.", "JS001", 4, "unexpected end, expected a name"),
            ErrorCase("TT", "JS001", 3, "unexpected end, expected ';'"),
            ErrorCase("La", "JS001", 3, "unexpected end, expected ';'"),
            ErrorCase("La<TT;>", "JS001", 8, "unexpected end, expected ';'"),
            ErrorCase("[", "JS001", 2, "unexpected end, expected a type"),
            ErrorCase("La<+", "JS001", 5, "unexpected end, expected a type"),
            ErrorCase("La<", "JS001", 4, "unexpected end, expected '>'"),
            ErrorCase("La<TT;", "JS001", 7, "unexpected end, expected '>'")
        )

        for (case in cases) {
            val problem = assertFailsWith<Problem> {
                TypeReader(Cursor(case.input)).readType()
            }
            assertEquals(case.code, problem.code, case.input)
            assertEquals(case.column, problem.col, case.input)
            assertEquals(case.text, problem.text, case.input)
        }
    }

    @Test
    fun reportsVariableAndClassDelimiterMismatches() {
        val cases = listOf(
            ErrorCase("Tn/", "JS002", 3, "unexpected '/', expected ';'"),
            ErrorCase(
                "Lpkg/A<TT;>/B;",
                "JS002",
                12,
                "unexpected '/', expected ';'"
            ),
            ErrorCase("La.B/C;", "JS002", 5, "unexpected '/', expected ';'"),
            ErrorCase(
                "La<TT;><TT;>;",
                "JS002",
                8,
                "unexpected '<', expected ';'"
            )
        )

        for (case in cases) {
            val problem = assertFailsWith<Problem> {
                TypeReader(Cursor(case.input)).readType()
            }
            assertEquals(case.code, problem.code, case.input)
            assertEquals(case.column, problem.col, case.input)
            assertEquals(case.text, problem.text, case.input)
        }
    }

    @Test
    fun rejectsEmptyNamesAtEveryNameDelimiter() {
        val nameDelimiters = listOf(".", ";", "[", "/", "<", ">", ":")
        for (delimiter in nameDelimiters) {
            val problem = assertFailsWith<Problem> {
                TypeReader(Cursor(delimiter)).readName()
            }
            assertEquals("JS005", problem.code, delimiter)
            assertEquals(1, problem.col, delimiter)
            assertEquals("empty name", problem.text, delimiter)
        }

        val contextualCases = listOf(
            ErrorCase("Lpkg//A;", "JS005", 6, "empty name"),
            ErrorCase("T;", "JS005", 2, "empty name"),
            ErrorCase("La.;", "JS005", 4, "empty name")
        )
        for (case in contextualCases) {
            val problem = assertFailsWith<Problem> {
                TypeReader(Cursor(case.input)).readType()
            }
            assertEquals(case.code, problem.code, case.input)
            assertEquals(case.column, problem.col, case.input)
            assertEquals(case.text, problem.text, case.input)
        }
    }

    @Test
    fun rejectsEmptyArgumentsAndInvalidTypeStartsWithShownCodePoints() {
        val cases = listOf(
            ErrorCase("La<>;", "JS006", 4, "empty type argument list"),
            ErrorCase("Q", "JS003", 1, "not a type: 'Q'"),
            ErrorCase(";", "JS003", 1, "not a type: ';'"),
            ErrorCase(")", "JS003", 1, "not a type: ')'"),
            ErrorCase(" ", "JS003", 1, "not a type: U+0020"),
            ErrorCase("\u0085", "JS003", 1, "not a type: U+0085"),
            ErrorCase("\uD83D\uDE00", "JS003", 1, "not a type: '\uD83D\uDE00'")
        )

        for (case in cases) {
            val problem = assertFailsWith<Problem> {
                TypeReader(Cursor(case.input)).readType()
            }
            assertEquals(case.code, problem.code, case.input)
            assertEquals(case.column, problem.col, case.input)
            assertEquals(case.text, problem.text, case.input)
        }
    }

    @Test
    fun rejectsPrimitiveArgumentsAndWildcardBoundsAndVoidOutsideReturn() {
        val cases = listOf(
            ErrorCase("La<I>;", "JS011", 4, "a type argument must be a reference type"),
            ErrorCase(
                "La<+I>;",
                "JS010",
                5,
                "a wildcard bound must be a reference type"
            ),
            ErrorCase(
                "La<-I>;",
                "JS010",
                5,
                "a wildcard bound must be a reference type"
            ),
            ErrorCase("V", "JS004", 1, "void is only allowed as a return type"),
            ErrorCase("[V", "JS004", 2, "void is only allowed as a return type"),
            ErrorCase("La<V>;", "JS004", 4, "void is only allowed as a return type"),
            ErrorCase("La<+V>;", "JS004", 5, "void is only allowed as a return type"),
            ErrorCase("La<-V>;", "JS004", 5, "void is only allowed as a return type")
        )

        for (case in cases) {
            val problem = assertFailsWith<Problem> {
                TypeReader(Cursor(case.input)).readType()
            }
            assertEquals(case.code, problem.code, case.input)
            assertEquals(case.column, problem.col, case.input)
            assertEquals(case.text, problem.text, case.input)
        }
    }

    @Test
    fun acceptsExactly255ArrayDimensionsAndRejectsThe256thBeforeItsElement() {
        var parsed = TypeReader(Cursor("[".repeat(255) + "I")).readType()
        var dimensions = 0
        while (parsed is Ty.Arr) {
            dimensions++
            parsed = parsed.elem
        }
        assertEquals(255, dimensions)
        assertEquals(Ty.Prim('I'), parsed)

        val overflow = assertFailsWith<Problem> {
            TypeReader(Cursor("[".repeat(256) + "I")).readType()
        }
        assertEquals("JS007", overflow.code)
        assertEquals(256, overflow.col)
        assertEquals("more than 255 array dimensions", overflow.text)

        val overflowBeforeVoid = assertFailsWith<Problem> {
            TypeReader(Cursor("[".repeat(256) + "V")).readType()
        }
        assertEquals("JS007", overflowBeforeVoid.code)
        assertEquals(256, overflowBeforeVoid.col)
        assertEquals("more than 255 array dimensions", overflowBeforeVoid.text)
    }
}
