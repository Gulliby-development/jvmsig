package io.github.gulliby.jvmsig.erase

import io.github.gulliby.jvmsig.model.Sig
import io.github.gulliby.jvmsig.model.Ty
import io.github.gulliby.jvmsig.model.TypeParam
import kotlin.test.Test
import kotlin.test.assertEquals

class EraserTest {
    @Test
    fun primitiveFieldsAndMethodDescriptorsArePreserved() {
        val primitiveCases = listOf(
            'B' to "B",
            'C' to "C",
            'D' to "D",
            'F' to "F",
            'I' to "I",
            'J' to "J",
            'S' to "S",
            'Z' to "Z"
        )

        for ((code, descriptor) in primitiveCases) {
            assertEquals(descriptor, Eraser.erase(Sig.Field(Ty.Prim(code))))
        }

        assertEquals(
            "(BCDFIJSZ)V",
            Eraser.erase(
                Sig.Method(
                    tparams = emptyList(),
                    params = primitiveCases.map { Ty.Prim(it.first) },
                    ret = Ty.Prim('V'),
                    throws = emptyList()
                )
            )
        )
        assertEquals(
            "()V",
            Eraser.erase(Sig.Method(emptyList(), emptyList(), Ty.Prim('V'), emptyList()))
        )
    }

    @Test
    fun arraysAndClassesEraseTheirStructureAndBinaryNames() {
        val stringType = classType("java/lang/String")
        val mapType = Ty.Cls(
            listOf(
                Ty.Part(
                    "java/util/Map",
                    listOf(Ty.Var("K"), Ty.Star)
                ),
                Ty.Part("Entry", listOf(Ty.Bound(true, Ty.Var("V")))),
                Ty.Part("Nested", null)
            )
        )

        assertEquals("[[[J", Eraser.erase(Sig.Field(Ty.Arr(Ty.Arr(Ty.Arr(Ty.Prim('J')))))))
        assertEquals("[[Ljava/lang/String;", Eraser.erase(Sig.Field(Ty.Arr(Ty.Arr(stringType)))))
        assertEquals("Ljava/util/Map\$Entry\$Nested;", Eraser.erase(Sig.Field(mapType)))
        assertEquals("Lpkg/Outer\$Inner;", Eraser.erase(Sig.Field(Ty.Cls(listOf(Ty.Part("pkg/Outer\$Inner", null))))))
        assertEquals(
            Ty.Cls(listOf(Ty.Part("java/util/Map\$Entry\$Nested", null))),
            Eraser.eraseType(mapType)
        )
    }

    @Test
    fun classArgumentsAreDroppedWithoutResolvingThem() {
        val recursiveParameters = listOf(
            typeParam("T", Ty.Var("T"))
        )
        val generic = Ty.Cls(
            listOf(
                Ty.Part(
                    "pkg/Generic",
                    listOf(
                        Ty.Var("T"),
                        Ty.Arr(Ty.Var("Missing")),
                        Ty.Star,
                        Ty.Bound(true, classType("pkg/Bound")),
                        Ty.Bound(false, Ty.Arr(Ty.Prim('I')))
                    )
                )
            )
        )

        assertEquals(
            Ty.Cls(listOf(Ty.Part("pkg/Generic", null))),
            Eraser.eraseType(generic, recursiveParameters)
        )
        assertEquals("Lpkg/Generic;", Eraser.erase(Sig.Field(generic)))
    }

    @Test
    fun wildcardModelVariantsEraseTheirBoundsOrObject() {
        val number = classType("java/lang/Number")

        assertEquals(number, Eraser.eraseType(Ty.Bound(true, number)))
        assertEquals(number, Eraser.eraseType(Ty.Bound(false, number)))
        assertEquals(Ty.Arr(Ty.Prim('I')), Eraser.eraseType(Ty.Bound(false, Ty.Arr(Ty.Prim('I')))))
        assertEquals(classType("java/lang/Object"), Eraser.eraseType(Ty.Star))
        assertEquals(Ty.Prim('V'), Eraser.eraseType(Ty.Prim('V')))
    }

    @Test
    fun variablesUseTheFirstDeclarationAndFirstAvailableBound() {
        val number = classType("java/lang/Number")
        val string = classType("java/lang/String")
        val parameters = listOf(
            typeParam("T", Ty.Var("U"), listOf(string)),
            typeParam("U", null, listOf(number, string)),
            typeParam("X", number, listOf(string)),
            typeParam("Y", null, listOf(string, number)),
            typeParam("D", null),
            typeParam("D", number),
            typeParam("E", number),
            typeParam("E", string)
        )

        assertEquals(number, Eraser.eraseType(Ty.Var("T"), parameters))
        assertEquals(number, Eraser.eraseType(Ty.Var("U"), parameters))
        assertEquals(number, Eraser.eraseType(Ty.Var("X"), parameters))
        assertEquals(string, Eraser.eraseType(Ty.Var("Y"), parameters))
        assertEquals(classType("java/lang/Object"), Eraser.eraseType(Ty.Var("D"), parameters))
        assertEquals(number, Eraser.eraseType(Ty.Var("E"), parameters))
    }

    @Test
    fun unboundedAndUndeclaredVariablesUseObjectWithoutLeakingCallState() {
        val objectType = classType("java/lang/Object")
        val number = classType("java/lang/Number")
        val string = classType("java/lang/String")

        assertEquals(objectType, Eraser.eraseType(Ty.Var("Missing")))
        assertEquals(objectType, Eraser.eraseType(Ty.Var("T"), listOf(typeParam("T", null))))
        assertEquals(number, Eraser.eraseType(Ty.Var("T"), listOf(typeParam("T", number))))
        assertEquals(string, Eraser.eraseType(Ty.Var("T"), listOf(typeParam("T", string))))
        assertEquals(objectType, Eraser.eraseType(Ty.Var("T")))
    }

    @Test
    fun cyclesFallBackAtTheRepeatedVariableAndRemainPathLocal() {
        val objectType = classType("java/lang/Object")
        val string = classType("java/lang/String")
        val parameters = listOf(
            typeParam("Self", Ty.Var("Self")),
            typeParam("A", Ty.Var("B")),
            typeParam("B", Ty.Var("A")),
            typeParam("Entry", Ty.Var("Middle")),
            typeParam("Middle", Ty.Var("Loop")),
            typeParam("Loop", Ty.Var("Middle")),
            typeParam("ArrayCycle", Ty.Arr(Ty.Var("ArrayCycle"))),
            typeParam("T", string)
        )

        assertEquals(objectType, Eraser.eraseType(Ty.Var("Self"), parameters))
        assertEquals(objectType, Eraser.eraseType(Ty.Var("A"), parameters))
        assertEquals(objectType, Eraser.eraseType(Ty.Var("Entry"), parameters))
        assertEquals(Ty.Arr(objectType), Eraser.eraseType(Ty.Var("ArrayCycle"), parameters))
        assertEquals(
            Ty.Arr(Ty.Arr(objectType)),
            Eraser.eraseType(Ty.Arr(Ty.Var("ArrayCycle")), parameters)
        )
        assertEquals(
            "(Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/String;",
            Eraser.erase(
                Sig.Method(
                    tparams = parameters,
                    params = listOf(Ty.Var("T"), Ty.Arr(Ty.Var("T"))),
                    ret = Ty.Var("T"),
                    throws = emptyList()
                )
            )
        )
    }

    @Test
    fun erasedMethodModelDropsDeclarationsAndThrowsButKeepsErasedArrays() {
        val number = classType("java/lang/Number")
        val method = Sig.Method(
            tparams = listOf(typeParam("T", number)),
            params = listOf(Ty.Var("T"), Ty.Arr(Ty.Var("T")), Ty.Prim('J')),
            ret = Ty.Arr(Ty.Var("T")),
            throws = listOf(Ty.Var("T"), classType("java/io/IOException"))
        )

        assertEquals(
            Sig.Method(
                tparams = emptyList(),
                params = listOf(number, Ty.Arr(number), Ty.Prim('J')),
                ret = Ty.Arr(number),
                throws = emptyList()
            ),
            Eraser.eraseMethod(method)
        )
        assertEquals(
            "(Ljava/lang/Number;[Ljava/lang/Number;J)[Ljava/lang/Number;",
            Eraser.erase(method)
        )
    }

    @Test
    fun classSignaturesJoinErasedSuperclassAndInterfaces() {
        val objectType = Ty.Cls(listOf(Ty.Part("java/lang/Object", null)))
        val listType = Ty.Cls(listOf(Ty.Part("java/util/List", listOf(Ty.Var("T")))))
        val runnableType = Ty.Cls(listOf(Ty.Part("java/lang/Runnable", null)))
        val parameters = listOf(typeParam("T", null))

        assertEquals(
            "Ljava/lang/Object;",
            Eraser.erase(Sig.ClassSig(emptyList(), objectType, emptyList()))
        )
        assertEquals(
            "Ljava/lang/Object; Ljava/util/List;",
            Eraser.erase(Sig.ClassSig(parameters, objectType, listOf(listType)))
        )
        assertEquals(
            "Ljava/lang/Object; Ljava/util/List; Ljava/lang/Runnable;",
            Eraser.erase(Sig.ClassSig(parameters, objectType, listOf(listType, runnableType)))
        )
    }

    @Test
    fun workedErasureExamplesFromTheRequestMatchTheirLiteralDescriptors() {
        val objectType = classType("java/lang/Object")
        val numberType = classType("java/lang/Number")
        val comparableOfT = Ty.Cls(
            listOf(
                Ty.Part("java/lang/Comparable", listOf(Ty.Var("T")))
            )
        )
        val examples = listOf(
            Sig.Field(Ty.Arr(Ty.Arr(classType("java/lang/String")))) to "[[Ljava/lang/String;",
            Sig.Field(
                Ty.Cls(
                    listOf(
                        Ty.Part("java/util/Map", listOf(Ty.Var("K"), Ty.Var("V"))),
                        Ty.Part("Entry", listOf(Ty.Var("K")))
                    )
                )
            ) to "Ljava/util/Map\$Entry;",
            Sig.Method(
                listOf(
                    typeParam("T", Ty.Var("U")),
                    typeParam("U", null, listOf(numberType))
                ),
                listOf(Ty.Var("T"), Ty.Arr(Ty.Var("U"))),
                Ty.Var("T"),
                emptyList()
            ) to "(Ljava/lang/Number;[Ljava/lang/Number;)Ljava/lang/Number;",
            Sig.Method(
                listOf(typeParam("T", null, listOf(comparableOfT))),
                listOf(Ty.Arr(Ty.Var("T"))),
                Ty.Var("T"),
                emptyList()
            ) to "([Ljava/lang/Comparable;)Ljava/lang/Comparable;",
            Sig.Method(
                listOf(typeParam("A", Ty.Var("B")), typeParam("B", Ty.Var("A"))),
                listOf(Ty.Var("A"), Ty.Var("B")),
                Ty.Var("A"),
                emptyList()
            ) to "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            Sig.Method(
                listOf(typeParam("T", numberType)),
                listOf(Ty.Var("X")),
                Ty.Prim('V'),
                listOf(Ty.Var("T"))
            ) to "(Ljava/lang/Object;)V",
            Sig.ClassSig(
                listOf(typeParam("T", null)),
                objectType,
                listOf(
                    Ty.Cls(listOf(Ty.Part("java/util/List", listOf(Ty.Var("T"))))),
                    Ty.Cls(listOf(Ty.Part("java/lang/Runnable", null)))
                )
            ) to "Ljava/lang/Object; Ljava/util/List; Ljava/lang/Runnable;",
            Sig.Field(Ty.Var("T")) to "Ljava/lang/Object;"
        )

        assertEquals(8, examples.size)
        for ((signature, descriptor) in examples) {
            assertEquals(descriptor, Eraser.erase(signature))
        }
    }

    private fun classType(name: String): Ty.Cls = Ty.Cls(listOf(Ty.Part(name, null)))

    private fun typeParam(name: String, classBound: Ty?, interfaceBounds: List<Ty> = emptyList()) =
        TypeParam(name, 1, classBound, interfaceBounds)
}
