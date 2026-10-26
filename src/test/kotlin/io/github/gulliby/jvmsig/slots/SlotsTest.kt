package io.github.gulliby.jvmsig.slots

import io.github.gulliby.jvmsig.model.Problem
import io.github.gulliby.jvmsig.model.Sig
import io.github.gulliby.jvmsig.model.Ty
import io.github.gulliby.jvmsig.model.TypeParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SlotsTest {
    @Test
    fun workedInstanceAndStaticTablesMatchTheirLiteralExamples() {
        assertEquals(
            "SLOT  ARG   TYPE\n" +
                "0     this\n" +
                "1     0     int\n" +
                "2-3   1     long\n" +
                "4     2     java.lang.String\n" +
                "5-6   3     double\n" +
                "7     4     long[]\n" +
                "total 8 slots\n" +
                "returns void\n",
            Slots.render(
                method(
                    listOf(
                        Ty.Prim('I'),
                        Ty.Prim('J'),
                        classType("java/lang/String"),
                        Ty.Prim('D'),
                        Ty.Arr(Ty.Prim('J'))
                    )
                )
            )
        )

        assertEquals(
            "SLOT  ARG  TYPE\n" +
                "0     0    java.lang.Number\n" +
                "1-2   1    long\n" +
                "3     2    boolean\n" +
                "total 4 slots\n" +
                "returns java.lang.Number\n",
            Slots.render(
                method(
                    params = listOf(Ty.Var("T"), Ty.Prim('J'), Ty.Prim('Z')),
                    ret = Ty.Var("T"),
                    tparams = listOf(TypeParam("T", 1, classType("java/lang/Number"), emptyList()))
                ),
                isStatic = true
            )
        )
    }

    @Test
    fun emptyMethodsAndSingularTotalsRenderCompleteTables() {
        assertEquals(
            "SLOT  ARG  TYPE\n" +
                "total 0 slots\n" +
                "returns void\n",
            Slots.render(method(emptyList()), isStatic = true)
        )
        assertEquals(
            "SLOT  ARG   TYPE\n" +
                "0     this\n" +
                "total 1 slot\n" +
                "returns void\n",
            Slots.render(method(emptyList()))
        )
    }

    @Test
    fun primitiveNamesAndWidePrimitiveArraysUseTheExpectedWidths() {
        assertEquals(
            "SLOT  ARG  TYPE\n" +
                "0     0    byte\n" +
                "1     1    char\n" +
                "2-3   2    double\n" +
                "4     3    float\n" +
                "5     4    int\n" +
                "6-7   5    long\n" +
                "8     6    short\n" +
                "9     7    boolean\n" +
                "10    8    long[]\n" +
                "11    9    double[]\n" +
                "total 12 slots\n" +
                "returns void\n",
            Slots.render(
                method(
                    listOf(
                        Ty.Prim('B'),
                        Ty.Prim('C'),
                        Ty.Prim('D'),
                        Ty.Prim('F'),
                        Ty.Prim('I'),
                        Ty.Prim('J'),
                        Ty.Prim('S'),
                        Ty.Prim('Z'),
                        Ty.Arr(Ty.Prim('J')),
                        Ty.Arr(Ty.Prim('D'))
                    )
                ),
                isStatic = true
            )
        )
    }

    @Test
    fun arraysAndBinaryInnerClassNamesRenderAsFullJavaNames() {
        assertEquals(
            "SLOT  ARG  TYPE\n" +
                "0     0    long[][]\n" +
                "1     1    double[]\n" +
                "2     2    int[][]\n" +
                "3     3    java.lang.String[][]\n" +
                "4     4    pkg.Outer\$Inner\n" +
                "total 5 slots\n" +
                "returns java.lang.String[]\n",
            Slots.render(
                method(
                    params = listOf(
                        Ty.Arr(Ty.Arr(Ty.Prim('J'))),
                        Ty.Arr(Ty.Prim('D')),
                        Ty.Arr(Ty.Arr(Ty.Prim('I'))),
                        Ty.Arr(Ty.Arr(classType("java/lang/String"))),
                        Ty.Cls(listOf(Ty.Part("pkg/Outer", null), Ty.Part("Inner", null)))
                    ),
                    ret = Ty.Arr(classType("java/lang/String"))
                ),
                isStatic = true
            )
        )
    }

    @Test
    fun genericParametersReturnsAndThrowsUseErasure() {
        assertEquals(
            "SLOT  ARG  TYPE\n" +
                "0     0    java.lang.Number\n" +
                "1     1    java.lang.Number[]\n" +
                "2     2    java.lang.Object\n" +
                "3     3    java.lang.Object\n" +
                "4     4    pkg.Outer\$Inner\n" +
                "total 5 slots\n" +
                "returns java.lang.Number\n",
            Slots.render(
                method(
                    params = listOf(
                        Ty.Var("T"),
                        Ty.Arr(Ty.Var("T")),
                        Ty.Var("U"),
                        Ty.Var("Missing"),
                        Ty.Cls(listOf(Ty.Part("pkg/Outer", null), Ty.Part("Inner", null)))
                    ),
                    ret = Ty.Var("T"),
                    tparams = listOf(
                        TypeParam("T", 1, classType("java/lang/Number"), emptyList()),
                        TypeParam("U", 2, null, emptyList())
                    ),
                    throws = listOf(Ty.Var("T"), classType("java/io/IOException"))
                ),
                isStatic = true
            )
        )
    }

    @Test
    fun returnTypesDoNotAddSlotsAndMissingVariablesFallBackToObject() {
        assertEquals(
            "SLOT  ARG   TYPE\n" +
                "0     this\n" +
                "total 1 slot\n" +
                "returns java.lang.String[][]\n",
            Slots.render(
                method(
                    params = emptyList(),
                    ret = Ty.Arr(Ty.Arr(classType("java/lang/String")))
                )
            )
        )
        assertEquals(
            "SLOT  ARG  TYPE\n" +
                "0     0    java.lang.Object\n" +
                "total 1 slot\n" +
                "returns void\n",
            Slots.render(method(listOf(Ty.Var("Missing"))), isStatic = true)
        )
    }

    @Test
    fun multiDigitColumnsAndWideRangesArePaddedLiterally() {
        assertEquals(
            "SLOT   ARG  TYPE\n" +
                "0      0    int\n" +
                "1      1    int\n" +
                "2      2    int\n" +
                "3      3    int\n" +
                "4      4    int\n" +
                "5      5    int\n" +
                "6      6    int\n" +
                "7      7    int\n" +
                "8      8    int\n" +
                "9      9    int\n" +
                "10-11  10   long\n" +
                "total 12 slots\n" +
                "returns void\n",
            Slots.render(
                method(List(10) { Ty.Prim('I') } + Ty.Prim('J')),
                isStatic = true
            )
        )
    }

    @Test
    fun fieldsAndClassSignaturesFailWithMethodRequiredDiagnostic() {
        for (signature in listOf(
            Sig.Field(Ty.Prim('I')),
            Sig.ClassSig(emptyList(), classType("java/lang/Object"), emptyList())
        )) {
            val problem = assertFailsWith<Problem> { Slots.render(signature) }
            assertEquals("JS016", problem.code)
            assertEquals(1, problem.col)
            assertEquals("a method signature is required", problem.text)
        }
    }

    @Test
    fun slotLimitAccepts255AndRejects256OrMore() {
        val longParameters = "J".repeat(127).map { Ty.Prim(it) }
        assertTrue(
            Slots.render(method(longParameters)).endsWith("total 255 slots\nreturns void\n")
        )

        val static255 = method(longParameters + Ty.Prim('I'))
        assertTrue(
            Slots.render(static255, isStatic = true).endsWith("total 255 slots\nreturns void\n")
        )

        val failures = listOf(
            method("J".repeat(128).map { Ty.Prim(it) }),
            method(longParameters + Ty.Prim('I')),
            method(longParameters + Ty.Prim('I') + Ty.Prim('I'))
        )
        val staticFlags = listOf(false, false, true)
        for ((signature, isStatic) in failures.zip(staticFlags)) {
            val problem = assertFailsWith<Problem> { Slots.render(signature, isStatic) }
            assertEquals("JS017", problem.code)
            assertEquals(1, problem.col)
            assertEquals("more than 255 argument slots", problem.text)
        }
    }

    private fun method(
        params: List<Ty>,
        ret: Ty = Ty.Prim('V'),
        tparams: List<TypeParam> = emptyList(),
        throws: List<Ty> = emptyList()
    ): Sig.Method = Sig.Method(tparams, params, ret, throws)

    private fun classType(name: String): Ty.Cls =
        Ty.Cls(listOf(Ty.Part(name, null)))
}
