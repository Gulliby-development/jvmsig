package io.github.gulliby.jvmsig.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class ProblemTest {
    @Test
    fun typeVariantsRetainTheirSuppliedFields() {
        val int = Ty.Prim('I')
        val void = Ty.Prim('V')
        val variable = Ty.Var("T")
        val array = Ty.Arr(variable)
        val extendsBound = Ty.Bound(true, variable)
        val superBound = Ty.Bound(false, Ty.Var("U"))
        val parts = listOf(
            Ty.Part("java/util/Map", null),
            Ty.Part("Entry", listOf(Ty.Star))
        )
        val classType = Ty.Cls(parts)
        val absentArguments = Ty.Part("X", null)
        val emptyArguments = Ty.Part("X", emptyList())

        assertEquals('I', int.code)
        assertEquals('V', void.code)
        assertEquals(true, array.elem == Ty.Var("T"))
        assertEquals(true, extendsBound.extends)
        assertEquals(Ty.Var("T"), extendsBound.bound)
        assertEquals(false, superBound.extends)
        assertEquals(Ty.Var("U"), superBound.bound)
        assertEquals(Ty.Star, Ty.Star)
        assertEquals("java/util/Map", classType.parts[0].name)
        assertEquals("Entry", classType.parts[1].name)
        assertEquals(null, classType.parts[0].args)
        assertEquals(listOf(Ty.Star), classType.parts[1].args)
        assertEquals(null, absentArguments.args)
        assertEquals(emptyList<Ty>(), emptyArguments.args)
        assertNotEquals(absentArguments, emptyArguments)
        assertEquals(listOf(Ty.Part("java/util/Map", null), Ty.Part("Entry", listOf(Ty.Star))), classType.parts)
    }

    @Test
    fun typeParameterBoundsPreservePrecedenceAndOrder() {
        val noBounds = TypeParam("T", 2, null, emptyList())
        val classOnly = TypeParam("T", 2, Ty.Var("A"), emptyList())
        val interfacesOnly = TypeParam("T", 2, null, listOf(Ty.Var("B"), Ty.Var("C")))
        val classAndInterfaces = TypeParam("T", 2, Ty.Var("A"), listOf(Ty.Var("B"), Ty.Var("C")))

        assertEquals(null, noBounds.firstBound())
        assertEquals(emptyList<Ty>(), noBounds.bounds())
        assertEquals(null, noBounds.firstBound())
        assertEquals(emptyList<Ty>(), noBounds.interfaceBounds)

        assertEquals(Ty.Var("A"), classOnly.firstBound())
        assertEquals(listOf(Ty.Var("A")), classOnly.bounds())
        assertEquals(Ty.Var("A"), classOnly.firstBound())
        assertEquals(emptyList<Ty>(), classOnly.interfaceBounds)

        assertEquals(Ty.Var("B"), interfacesOnly.firstBound())
        assertEquals(listOf(Ty.Var("B"), Ty.Var("C")), interfacesOnly.bounds())
        assertEquals(Ty.Var("B"), interfacesOnly.firstBound())
        assertEquals(listOf(Ty.Var("B"), Ty.Var("C")), interfacesOnly.interfaceBounds)

        assertEquals(Ty.Var("A"), classAndInterfaces.firstBound())
        assertEquals(listOf(Ty.Var("A"), Ty.Var("B"), Ty.Var("C")), classAndInterfaces.bounds())
        assertEquals(Ty.Var("A"), classAndInterfaces.firstBound())
        assertEquals(Ty.Var("A"), classAndInterfaces.classBound)
        assertEquals(listOf(Ty.Var("B"), Ty.Var("C")), classAndInterfaces.interfaceBounds)
    }

    @Test
    fun signaturesAndParsedOccurrencesRetainFieldsAndOrder() {
        val field = Sig.Field(Ty.Prim('I'))
        val method = Sig.Method(
            emptyList(),
            listOf(Ty.Prim('I'), Ty.Prim('J')),
            Ty.Prim('V'),
            listOf(Ty.Var("E"))
        )
        val parameter = TypeParam("T", 2, null, emptyList())
        val superclass = Ty.Cls(listOf(Ty.Part("java/lang/Object", null)))
        val runnable = Ty.Cls(listOf(Ty.Part("java/lang/Runnable", null)))
        val classSignature = Sig.ClassSig(listOf(parameter), superclass, listOf(runnable))
        val declarations = listOf(
            TypeParam("T", 2, null, emptyList()),
            TypeParam("T", 5, null, emptyList())
        )
        val uses = listOf("T" to 12, "X" to 9, "T" to 12)
        val parsed = Parsed(Sig.Field(Ty.Var("T")), declarations, uses)

        assertEquals(true, field.type == Ty.Prim('I'))
        assertEquals(emptyList(), method.tparams)
        assertEquals(listOf(Ty.Prim('I'), Ty.Prim('J')), method.params)
        val ret = method.ret
        assertEquals(true, ret == Ty.Prim('V'))
        val throws = method.throws
        assertEquals(true, throws == listOf(Ty.Var("E")))
        assertEquals(listOf(parameter), classSignature.tparams)
        assertEquals(superclass, classSignature.sup)
        assertEquals(listOf(runnable), classSignature.ifaces)
        assertEquals(Sig.Field(Ty.Var("T")), parsed.sig)
        assertEquals(listOf(2, 5), parsed.tparams.map { it.col })
        assertEquals(listOf("T" to 12, "X" to 9, "T" to 12), parsed.uses)
        assertEquals(uses, parsed.uses)
    }

    @Test
    fun allProblemFactoriesPreserveExactCodesColumnsMessagesAndExceptionBehavior() {
        val cases = listOf(
            Problem.unexpectedEnd(18, "';'") to Triple("JS001", 18, "unexpected end, expected ';'"),
            Problem.unexpected(3, "'>'", "':'") to Triple("JS002", 3, "unexpected '>', expected ':'"),
            Problem.notType(1, "'Q'") to Triple("JS003", 1, "not a type: 'Q'"),
            Problem.voidNotReturn(2) to Triple("JS004", 2, "void is only allowed as a return type"),
            Problem.emptyName(6) to Triple("JS005", 6, "empty name"),
            Problem.emptyTypeArguments(17) to Triple("JS006", 17, "empty type argument list"),
            Problem.tooManyDimensions(256) to Triple("JS007", 256, "more than 255 array dimensions"),
            Problem.trailingText(19) to Triple("JS008", 19, "unexpected text after the signature"),
            Problem.emptyTypeParameters(2) to Triple("JS009", 2, "empty type parameter list"),
            Problem.wildcardBoundNotReference(18) to Triple("JS010", 18, "a wildcard bound must be a reference type"),
            Problem.typeArgumentNotReference(17) to Triple("JS011", 17, "a type argument must be a reference type"),
            Problem.undeclaredVariable(27, "X") to Triple("JS012", 27, "type variable X is not declared"),
            Problem.duplicateParameter(5, "T") to Triple("JS013", 5, "type parameter T is declared twice"),
            Problem.invalidThrownType(6) to Triple("JS014", 6, "a thrown type must be a class or a type variable"),
            Problem.cyclicBound(2, "A") to Triple("JS015", 2, "type parameter A has a cyclic bound"),
            Problem.methodRequired() to Triple("JS016", 1, "a method signature is required"),
            Problem.tooManySlots() to Triple("JS017", 1, "more than 255 argument slots"),
            Problem.notType(4, "U+0020") to Triple("JS003", 4, "not a type: U+0020"),
            Problem.unexpected(7, "U+0085", "a name") to Triple("JS002", 7, "unexpected U+0085, expected a name"),
            Problem("CUSTOM", 8, "literal text") to Triple("CUSTOM", 8, "literal text")
        )

        cases.forEach { (problem, expected) ->
            assertEquals(expected, Triple(problem.code, problem.col, problem.text))
            val text = problem.text
            assertEquals(true, problem.message == text)
            assertSame(problem, assertFailsWith<Problem> { throw problem })
        }
    }
}
