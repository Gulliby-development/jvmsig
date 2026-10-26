package io.github.gulliby.jvmsig.read

import io.github.gulliby.jvmsig.model.Parsed
import io.github.gulliby.jvmsig.model.Problem
import io.github.gulliby.jvmsig.model.Sig
import io.github.gulliby.jvmsig.model.Ty
import io.github.gulliby.jvmsig.model.TypeParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SigReaderTest {
    @Test
    fun readsFieldsAndEveryPrimitiveMethodType() {
        assertEquals(
            Parsed(Sig.Field(Ty.Prim('I')), emptyList(), emptyList()),
            SigReader.read("I")
        )
        assertEquals(
            Parsed(Sig.Field(Ty.Var("T")), emptyList(), listOf("T" to 1)),
            SigReader.read("TT;")
        )
        assertEquals(
            Parsed(
                Sig.Method(
                    emptyList(),
                    listOf(
                        Ty.Prim('B'),
                        Ty.Prim('C'),
                        Ty.Prim('D'),
                        Ty.Prim('F'),
                        Ty.Prim('I'),
                        Ty.Prim('J'),
                        Ty.Prim('S'),
                        Ty.Prim('Z')
                    ),
                    Ty.Prim('V'),
                    emptyList()
                ),
                emptyList(),
                emptyList()
            ),
            SigReader.read("(BCDFIJSZ)V")
        )
        assertEquals(
            Parsed(
                Sig.Field(Ty.Arr(Ty.Arr(Ty.Prim('J')))),
                emptyList(),
                emptyList()
            ),
            SigReader.read("[[J")
        )
    }

    // A generic method has <parameters>(arguments)return^throws; each parameter has an optional
    // class bound followed by zero or more interface bounds, and throws remain in source order.
    @Test
    fun readsGenericMethodsWithOptionalAndMultipleBoundsAndOrderedThrows() {
        val objectType = Ty.Cls(listOf(Ty.Part("java/lang/Object", null)))
        val comparable = Ty.Cls(
            listOf(Ty.Part("java/lang/Comparable", listOf(Ty.Var("T"))))
        )
        val runnable = Ty.Cls(listOf(Ty.Part("java/lang/Runnable", null)))
        val exception = Ty.Cls(listOf(Ty.Part("java/lang/Exception", null)))
        val classAndMultipleInterfaceBounds = listOf(
            TypeParam("T", 2, objectType, listOf(comparable, runnable)),
            TypeParam("U", 71, Ty.Arr(Ty.Prim('I')), emptyList())
        )
        val interfaceOnlyAndArrayBounds = listOf(
            TypeParam("T", 2, null, listOf(runnable)),
            TypeParam("U", 25, Ty.Arr(Ty.Prim('I')), emptyList())
        )
        val noBounds = listOf(TypeParam("T", 2, null, emptyList()))
        val cases = listOf(
            "<T:Ljava/lang/Object;:Ljava/lang/Comparable<TT;>;:Ljava/lang/Runnable;U:[I>()TT;^TT;^Ljava/lang/Exception;" to Parsed(
                Sig.Method(
                    classAndMultipleInterfaceBounds,
                    emptyList(),
                    Ty.Var("T"),
                    listOf(Ty.Var("T"), exception)
                ),
                classAndMultipleInterfaceBounds,
                listOf("T" to 45, "T" to 78, "T" to 82)
            ),
            "<T::Ljava/lang/Runnable;U:[I>()TT;^TT;^Ljava/lang/Exception;" to Parsed(
                Sig.Method(
                    interfaceOnlyAndArrayBounds,
                    emptyList(),
                    Ty.Var("T"),
                    listOf(Ty.Var("T"), exception)
                ),
                interfaceOnlyAndArrayBounds,
                listOf("T" to 32, "T" to 36)
            ),
            "<T:>(TT;)V" to Parsed(
                Sig.Method(
                    noBounds,
                    listOf(Ty.Var("T")),
                    Ty.Prim('V'),
                    emptyList()
                ),
                noBounds,
                listOf("T" to 6)
            )
        )

        for ((signature, expected) in cases) {
            assertEquals(expected, SigReader.read(signature), signature)
        }
    }

    @Test
    fun readsClassSignaturesAndTheirTypeVariableUses() {
        val typeParameter = TypeParam("T", 2, null, emptyList())
        val objectType = Ty.Cls(listOf(Ty.Part("java/lang/Object", null)))
        val listOfT = Ty.Cls(
            listOf(Ty.Part("java/util/List", listOf(Ty.Var("T"))))
        )
        assertEquals(
            Parsed(
                Sig.ClassSig(listOf(typeParameter), objectType, listOf(listOfT)),
                listOf(typeParameter),
                listOf("T" to 39)
            ),
            SigReader.read("<T:>Ljava/lang/Object;Ljava/util/List<TT;>;")
        )
    }

    @Test
    fun preservesNamesAndBuildsAllArgumentFormsInTheModel() {
        assertEquals(
            Parsed(
                Sig.Field(
                    Ty.Cls(
                        listOf(
                            Ty.Part("java", null),
                            Ty.Part("lang", null),
                            Ty.Part("String", null)
                        )
                    )
                ),
                emptyList(),
                emptyList()
            ),
            SigReader.read("Ljava.lang.String;")
        )
        assertEquals(
            Parsed(
                Sig.Field(
                    Ty.Cls(
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
                    )
                ),
                emptyList(),
                listOf("T" to 5)
            ),
            SigReader.read("La<+TT;-Ljava/lang/Object;[I>;")
        )
        assertEquals(
            Parsed(
                Sig.Field(Ty.Cls(listOf(Ty.Part("x\$ _()^+-*", null)))),
                emptyList(),
                emptyList()
            ),
            SigReader.read("Lx\$ _()^+-*;")
        )
    }

    @Test
    fun recordsSupplementaryDeclarationAndUseColumnsByCodePoint() {
        val smile = "\uD83D\uDE00"
        val parameter = TypeParam(smile, 2, Ty.Var(smile), emptyList())
        assertEquals(
            Parsed(
                Sig.Method(
                    listOf(parameter),
                    listOf(Ty.Var(smile)),
                    Ty.Var(smile),
                    listOf(Ty.Var(smile))
                ),
                listOf(parameter),
                listOf(smile to 4, smile to 9, smile to 13, smile to 17)
            ),
            SigReader.read("<\uD83D\uDE00:T\uD83D\uDE00;>(T\uD83D\uDE00;)T\uD83D\uDE00;^T\uD83D\uDE00;")
        )
    }

    @Test
    fun keepsDuplicateDeclarationsAndUsesWithoutSemanticValidationOrStateLeakage() {
        assertEquals(
            Parsed(
                Sig.Method(
                    listOf(
                        TypeParam("T", 2, Ty.Var("T"), emptyList()),
                        TypeParam("T", 7, null, emptyList())
                    ),
                    listOf(Ty.Var("X")),
                    Ty.Prim('V'),
                    emptyList()
                ),
                listOf(
                    TypeParam("T", 2, Ty.Var("T"), emptyList()),
                    TypeParam("T", 7, null, emptyList())
                ),
                listOf("T" to 4, "X" to 11)
            ),
            SigReader.read("<T:TT;T:>(TX;)V")
        )
        assertEquals(
            Parsed(Sig.Field(Ty.Prim('I')), emptyList(), emptyList()),
            SigReader.read("I")
        )
        assertEquals(
            Parsed(Sig.Field(Ty.Var("X")), emptyList(), listOf("X" to 1)),
            SigReader.read("TX;")
        )
    }

    @Test
    fun reportsEndOfInputInEveryGrammarContext() {
        val cases = listOf(
            "" to Triple("JS001", 1, "unexpected end, expected a type"),
            "L" to Triple("JS001", 2, "unexpected end, expected a name"),
            "T" to Triple("JS001", 2, "unexpected end, expected a name"),
            "La/" to Triple("JS001", 4, "unexpected end, expected a name"),
            "La." to Triple("JS001", 4, "unexpected end, expected a name"),
            "TT" to Triple("JS001", 3, "unexpected end, expected ';'"),
            "La" to Triple("JS001", 3, "unexpected end, expected ';'"),
            "La<TT;>" to Triple("JS001", 8, "unexpected end, expected ';'"),
            "[" to Triple("JS001", 2, "unexpected end, expected a type"),
            "La<+" to Triple("JS001", 5, "unexpected end, expected a type"),
            "La<" to Triple("JS001", 4, "unexpected end, expected '>'"),
            "La<TT;" to Triple("JS001", 7, "unexpected end, expected '>'"),
            "<" to Triple("JS001", 2, "unexpected end, expected '>'"),
            "<T" to Triple("JS001", 3, "unexpected end, expected ':'"),
            "<T:" to Triple("JS001", 4, "unexpected end, expected a type"),
            "<T::" to Triple("JS001", 5, "unexpected end, expected a type"),
            "<T:TT;" to Triple("JS001", 7, "unexpected end, expected '>'"),
            "(" to Triple("JS001", 2, "unexpected end, expected ')'"),
            "(I" to Triple("JS001", 3, "unexpected end, expected ')'"),
            "()" to Triple("JS001", 3, "unexpected end, expected a return type"),
            "()V^" to Triple("JS001", 5, "unexpected end, expected a class or type variable"),
            "<T:>" to Triple("JS001", 5, "unexpected end, expected '(' or the superclass")
        )
        cases.forEach { (signature, expected) ->
            val problem = assertFailsWith<Problem> { SigReader.read(signature) }
            assertEquals(expected, Triple(problem.code, problem.col, problem.text), signature)
        }
    }

    @Test
    fun reportsDelimiterEmptyListAndFullConsumptionProblemsLiterally() {
        val cases = listOf(
            "<T>V" to Triple("JS002", 3, "unexpected '>', expected ':'"),
            "Tn/" to Triple("JS002", 3, "unexpected '/', expected ';'"),
            "Lpkg/A<TT;>/B;" to Triple("JS002", 12, "unexpected '/', expected ';'"),
            "La.B/C;" to Triple("JS002", 5, "unexpected '/', expected ';'"),
            "La<TT;><TT;>;" to Triple("JS002", 8, "unexpected '<', expected ';'"),
            "<T:>V" to Triple("JS002", 5, "unexpected 'V', expected '(' or the superclass"),
            "L.;" to Triple("JS005", 2, "empty name"),
            "L;" to Triple("JS005", 2, "empty name"),
            "L[" to Triple("JS005", 2, "empty name"),
            "L/" to Triple("JS005", 2, "empty name"),
            "L<" to Triple("JS005", 2, "empty name"),
            "L>" to Triple("JS005", 2, "empty name"),
            "L:" to Triple("JS005", 2, "empty name"),
            "Lpkg//A;" to Triple("JS005", 6, "empty name"),
            "La.;" to Triple("JS005", 4, "empty name"),
            "<:>()V" to Triple("JS005", 2, "empty name"),
            "La<>;" to Triple("JS006", 4, "empty type argument list"),
            "<>V" to Triple("JS009", 2, "empty type parameter list"),
            "I " to Triple("JS008", 2, "unexpected text after the signature"),
            "Ljava/lang/Object;;" to Triple("JS008", 19, "unexpected text after the signature")
        )
        cases.forEach { (signature, expected) ->
            val problem = assertFailsWith<Problem> { SigReader.read(signature) }
            assertEquals(expected, Triple(problem.code, problem.col, problem.text), signature)
        }
    }

    @Test
    fun distinguishesPrimitiveReferencesAndInvalidThrownTypes() {
        val cases = listOf(
            "<T:I>()V" to Triple("JS011", 4, "a type argument must be a reference type"),
            "<T::I>()V" to Triple("JS011", 5, "a type argument must be a reference type"),
            "(I)V^I" to Triple("JS014", 6, "a thrown type must be a class or a type variable"),
            "()V^[I" to Triple("JS014", 5, "a thrown type must be a class or a type variable"),
            "()V^Q" to Triple("JS014", 5, "a thrown type must be a class or a type variable")
        )
        cases.forEach { (signature, expected) ->
            val problem = assertFailsWith<Problem> { SigReader.read(signature) }
            assertEquals(expected, Triple(problem.code, problem.col, problem.text), signature)
        }
    }

    @Test
    fun voidContextsAndEarlierSyntaxProblemsTakePrecedence() {
        val cases = listOf(
            "V" to Triple("JS004", 1, "void is only allowed as a return type"),
            "[V" to Triple("JS004", 2, "void is only allowed as a return type"),
            "(IV)V" to Triple("JS004", 3, "void is only allowed as a return type"),
            "La<V>;" to Triple("JS004", 4, "void is only allowed as a return type"),
            "La<+V>;" to Triple("JS004", 5, "void is only allowed as a return type"),
            "La<-V>;" to Triple("JS004", 5, "void is only allowed as a return type"),
            "<T:V>()V" to Triple("JS004", 4, "void is only allowed as a return type"),
            "<T::V>()V" to Triple("JS004", 5, "void is only allowed as a return type"),
            "()V^V" to Triple("JS004", 5, "void is only allowed as a return type"),
            "(V)Q" to Triple("JS004", 2, "void is only allowed as a return type"),
            "La<I>;Q" to Triple("JS011", 4, "a type argument must be a reference type"),
            "I[V" to Triple("JS008", 2, "unexpected text after the signature"),
            "<T:TT;>(V)V" to Triple("JS004", 9, "void is only allowed as a return type")
        )
        cases.forEach { (signature, expected) ->
            val problem = assertFailsWith<Problem> { SigReader.read(signature) }
            assertEquals(expected, Triple(problem.code, problem.col, problem.text), signature)
        }
    }

    @Test
    fun accepts255ArrayDimensionsAndRejectsThe256thAtItsExactColumn() {
        val accepted = SigReader.read("[".repeat(255) + "I")
        var type = (accepted.sig as Sig.Field).type
        var dimensions = 0
        while (type is Ty.Arr) {
            dimensions++
            type = type.elem
        }
        assertEquals(255, dimensions)
        assertEquals(Ty.Prim('I'), type)

        val cases = listOf(
            ("[".repeat(256) + "I") to 256,
            ("(" + "[".repeat(256) + "I)V") to 257,
            ("[".repeat(256) + "V") to 256
        )
        cases.forEach { (signature, column) ->
            val problem = assertFailsWith<Problem> { SigReader.read(signature) }
            assertEquals(
                Triple("JS007", column, "more than 255 array dimensions"),
                Triple(problem.code, problem.col, problem.text)
            )
        }
    }

    @Test
    fun countsTrailingTextAfterSupplementaryNamesByCodePoint() {
        val problem = assertFailsWith<Problem> {
            SigReader.read("L\uD83D\uDE00;Q")
        }
        assertEquals(
            Triple("JS008", 4, "unexpected text after the signature"),
            Triple(problem.code, problem.col, problem.text)
        )
    }
}
