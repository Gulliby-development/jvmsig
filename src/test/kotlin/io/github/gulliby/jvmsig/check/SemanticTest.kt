package io.github.gulliby.jvmsig.check

import io.github.gulliby.jvmsig.model.Parsed
import io.github.gulliby.jvmsig.model.Problem
import io.github.gulliby.jvmsig.model.Sig
import io.github.gulliby.jvmsig.model.Ty
import io.github.gulliby.jvmsig.model.TypeParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SemanticTest {
    @Test
    fun emptyMetadataAndDeclaredForwardReferencesAreAccepted() {
        assertNull(Semantic.validate(parsed()))

        val parameters = listOf(
            parameter("T", 2, Ty.Var("U")),
            parameter("U", 9, classType("java/lang/Number"))
        )
        assertNull(Semantic.validate(parsed(parameters, listOf("T" to 20, "U" to 25))))
    }

    @Test
    fun undeclaredUsesChooseTheSmallestSuppliedColumnRegardlessOfListOrder() {
        val result = Semantic.validate(
            parsed(uses = listOf("Later" to 40, "Earlier" to 6, "Middle" to 22))
        )

        assertEquals(
            Triple("JS012", 6, "type variable Earlier is not declared"),
            actual(result)
        )
    }

    @Test
    fun externalNamesAndUnicodeNamesRetainTheSuppliedColumns() {
        val uses = listOf("Outer" to 3, "\u03C0" to 41)
        val parsed = parsed(uses = uses)

        assertNull(Semantic.validate(parsed, setOf("Outer", "\u03C0")))
        assertEquals(
            Triple("JS012", 41, "type variable \u03C0 is not declared"),
            actual(Semantic.validate(parsed, setOf("Outer")))
        )

        val unicodeDeclaration = parameter("\u96EA", 5)
        assertNull(Semantic.validate(parsed(listOf(unicodeDeclaration), listOf("\u96EA" to 73))))
    }

    @Test
    fun secondAndThirdDuplicateDeclarationsAreBothSemanticCandidates() {
        val secondDuplicate = parsed(
            listOf(parameter("T", 2), parameter("T", 19))
        )
        assertEquals(
            Triple("JS013", 19, "type parameter T is declared twice"),
            actual(Semantic.validate(secondDuplicate))
        )

        val thirdDuplicateHasTheSmallestColumn = parsed(
            listOf(parameter("T", 2), parameter("T", 19), parameter("T", 5))
        )
        assertEquals(
            Triple("JS013", 5, "type parameter T is declared twice"),
            actual(Semantic.validate(thirdDuplicateHasTheSmallestColumn))
        )
    }

    @Test
    fun firstDeclarationIsUsedForEveryBoundLookup() {
        val parameters = listOf(
            parameter("T", 2, classType("java/lang/Object")),
            parameter("T", 5, Ty.Var("W")),
            parameter("W", 9, Ty.Var("U")),
            parameter("U", 12, Ty.Var("T"))
        )

        assertEquals(
            Triple("JS013", 5, "type parameter T is declared twice"),
            actual(Semantic.validate(parsed(parameters)))
        )
    }

    @Test
    fun selfMutualAndLongerCyclesReportTheSmallestMemberColumn() {
        val cases = listOf(
            parsed(listOf(parameter("T", 8, Ty.Var("T")))) to
                Triple("JS015", 8, "type parameter T has a cyclic bound"),
            parsed(listOf(parameter("A", 20, Ty.Var("B")), parameter("B", 3, Ty.Var("A")))) to
                Triple("JS015", 3, "type parameter B has a cyclic bound"),
            parsed(
                listOf(
                    parameter("A", 8, Ty.Var("B")),
                    parameter("B", 20, Ty.Var("C")),
                    parameter("C", 3, Ty.Var("A"))
                )
            ) to Triple("JS015", 3, "type parameter C has a cyclic bound")
        )

        cases.forEach { (model, expected) ->
            assertEquals(expected, actual(Semantic.validate(model)))
        }
    }

    @Test
    fun aTailEnteringALaterCycleIsNotReportedAsACycleMember() {
        val parameters = listOf(
            parameter("Tail", 2, Ty.Var("A")),
            parameter("A", 11, Ty.Var("B")),
            parameter("B", 7, Ty.Var("A"))
        )

        assertEquals(
            Triple("JS015", 7, "type parameter B has a cyclic bound"),
            actual(Semantic.validate(parsed(parameters)))
        )
    }

    @Test
    fun onlyTheFirstBoundIsFollowedWithClassPrecedenceAndInterfaceFallback() {
        val parametersWithoutCycles = listOf(
            parameter("T", 2, classType("java/lang/Object"), listOf(Ty.Var("T"))),
            parameter("U", 8, Ty.Var("V"), listOf(Ty.Var("U"))),
            parameter("V", 12, classType("java/lang/Number")),
            parameter("W", 16, null, listOf(classType("java/lang/Object"), Ty.Var("W")))
        )
        assertNull(Semantic.validate(parsed(parametersWithoutCycles)))

        val firstInterfaceIsTheEdge = parameter("X", 23, null, listOf(Ty.Var("X"), classType("java/lang/Object")))
        assertEquals(
            Triple("JS015", 23, "type parameter X has a cyclic bound"),
            actual(Semantic.validate(parsed(listOf(firstInterfaceIsTheEdge))))
        )
    }

    @Test
    fun noBoundClassesArraysAndUndeclaredVariableBoundsTerminateTheWalk() {
        val parameters = listOf(
            parameter("None", 2),
            parameter(
                "Class",
                8,
                classType("java/lang/Node", listOf(Ty.Var("Class")))
            ),
            parameter("Array", 14, Ty.Arr(Ty.Var("Array"))),
            parameter("Missing", 20, Ty.Var("Undeclared"))
        )

        assertNull(Semantic.validate(parsed(parameters)))
    }

    @Test
    fun eachSemanticProblemKindCanWinByItsSmallestColumn() {
        val cases = listOf(
            parsed(
                listOf(parameter("T", 10), parameter("T", 8), parameter("A", 15, Ty.Var("A"))),
                listOf("X" to 2)
            ) to Triple("JS012", 2, "type variable X is not declared"),
            parsed(
                listOf(parameter("T", 10), parameter("T", 3), parameter("A", 6, Ty.Var("A"))),
                listOf("X" to 12)
            ) to Triple("JS013", 3, "type parameter T is declared twice"),
            parsed(
                listOf(parameter("T", 10), parameter("T", 8), parameter("A", 4, Ty.Var("A"))),
                listOf("X" to 15)
            ) to Triple("JS015", 4, "type parameter A has a cyclic bound")
        )

        cases.forEach { (model, expected) ->
            assertEquals(expected, actual(Semantic.validate(model)))
        }
    }

    @Test
    fun parsedMetadataIsAuthoritativeAndValidationDoesNotMutateOrLeakState() {
        val declared = parameter("T", 6)
        val signatureOnlyDeclaration = parameter("FromSig", 1)
        val signature = Sig.Method(
            listOf(signatureOnlyDeclaration),
            listOf(Ty.Var("T")),
            Ty.Prim('V'),
            emptyList()
        )
        val model = Parsed(signature, listOf(declared), listOf("T" to 30, "FromSig" to 40))

        val expected = Triple("JS012", 40, "type variable FromSig is not declared")
        assertEquals(expected, actual(Semantic.validate(model)))
        assertNull(Semantic.validate(model, setOf("FromSig")))
        assertEquals(expected, actual(Semantic.validate(model)))

        assertEquals(signature, model.sig)
        assertEquals(listOf(declared), model.tparams)
        assertEquals(listOf("T" to 30, "FromSig" to 40), model.uses)
        assertNull(Semantic.validate(parsed(sig = Sig.Field(Ty.Var("NotInUseMetadata")))))
    }

    @Test
    fun externalNamesSilenceOnlyUndeclaredUseProblems() {
        val duplicateModel = parsed(
            listOf(parameter("T", 2), parameter("T", 9)),
            listOf("External" to 1)
        )
        assertEquals(
            Triple("JS013", 9, "type parameter T is declared twice"),
            actual(Semantic.validate(duplicateModel, setOf("External")))
        )

        val cycleModel = parsed(
            listOf(parameter("A", 8, Ty.Var("A"))),
            listOf("External" to 1)
        )
        assertEquals(
            Triple("JS015", 8, "type parameter A has a cyclic bound"),
            actual(Semantic.validate(cycleModel, setOf("External")))
        )
    }

    private fun parsed(
        tparams: List<TypeParam> = emptyList(),
        uses: List<Pair<String, Int>> = emptyList(),
        sig: Sig = Sig.Field(Ty.Prim('I'))
    ): Parsed = Parsed(sig, tparams, uses)

    private fun parameter(
        name: String,
        col: Int,
        classBound: Ty? = null,
        interfaceBounds: List<Ty> = emptyList()
    ): TypeParam = TypeParam(name, col, classBound, interfaceBounds)

    private fun classType(name: String, arguments: List<Ty>? = null): Ty.Cls =
        Ty.Cls(listOf(Ty.Part(name, arguments)))

    private fun actual(problem: Problem?): Triple<String, Int, String>? =
        problem?.let { Triple(it.code, it.col, it.text) }
}
