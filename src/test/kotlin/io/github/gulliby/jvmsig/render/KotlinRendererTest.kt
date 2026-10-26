package io.github.gulliby.jvmsig.render

import io.github.gulliby.jvmsig.model.Sig
import io.github.gulliby.jvmsig.model.Ty
import io.github.gulliby.jvmsig.model.TypeParam
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinRendererTest {
    @Test
    fun primitiveTypesAndArraysUseBothRendererMappings() {
        val types = listOf(
            Ty.Prim('B') to "Byte",
            Ty.Prim('C') to "Char",
            Ty.Prim('D') to "Double",
            Ty.Prim('F') to "Float",
            Ty.Prim('I') to "Int",
            Ty.Prim('J') to "Long",
            Ty.Prim('S') to "Short",
            Ty.Prim('Z') to "Boolean",
            Ty.Prim('V') to "Unit",
            Ty.Arr(Ty.Prim('B')) to "ByteArray",
            Ty.Arr(Ty.Prim('C')) to "CharArray",
            Ty.Arr(Ty.Prim('D')) to "DoubleArray",
            Ty.Arr(Ty.Prim('F')) to "FloatArray",
            Ty.Arr(Ty.Prim('I')) to "IntArray",
            Ty.Arr(Ty.Prim('J')) to "LongArray",
            Ty.Arr(Ty.Prim('S')) to "ShortArray",
            Ty.Arr(Ty.Prim('Z')) to "BooleanArray",
            Ty.Arr(Ty.Arr(Ty.Prim('J'))) to "Array<LongArray>",
            Ty.Arr(classType("java/lang/String")) to "Array<java.lang.String>",
            Ty.Arr(Ty.Var("T")) to "Array<T>"
        )

        types.forEach { (type, expected) ->
            assertEquals(expected, KotlinRenderer.render(type), "Kotlin rendering of $type")
        }

        val javaTypes = listOf(
            Ty.Prim('B') to "byte",
            Ty.Prim('C') to "char",
            Ty.Prim('D') to "double",
            Ty.Prim('F') to "float",
            Ty.Prim('I') to "int",
            Ty.Prim('J') to "long",
            Ty.Prim('S') to "short",
            Ty.Prim('Z') to "boolean",
            Ty.Prim('V') to "void",
            Ty.Arr(Ty.Prim('B')) to "byte[]",
            Ty.Arr(Ty.Prim('C')) to "char[]",
            Ty.Arr(Ty.Prim('D')) to "double[]",
            Ty.Arr(Ty.Prim('F')) to "float[]",
            Ty.Arr(Ty.Prim('I')) to "int[]",
            Ty.Arr(Ty.Prim('J')) to "long[]",
            Ty.Arr(Ty.Prim('S')) to "short[]",
            Ty.Arr(Ty.Prim('Z')) to "boolean[]",
            Ty.Arr(Ty.Arr(Ty.Prim('J'))) to "long[][]",
            Ty.Arr(classType("java/lang/String")) to "java.lang.String[]",
            Ty.Arr(Ty.Var("T")) to "T[]"
        )

        javaTypes.forEach { (type, expected) ->
            assertEquals(expected, JavaRenderer.render(type), "Java rendering of $type")
        }
    }

    @Test
    fun classPartsVarianceAndShortNamesAreRenderedRecursively() {
        val types = listOf(
            Ty.Star to "*",
            Ty.Bound(true, Ty.Var("T")) to "out T",
            Ty.Bound(false, Ty.Var("T")) to "in T",
            Ty.Cls(
                listOf(
                    Ty.Part("java/util/Map", listOf(Ty.Var("K"), Ty.Arr(Ty.Var("V")))),
                    Ty.Part("Entry", listOf(Ty.Star))
                )
            ) to "java.util.Map<K, Array<V>>.Entry<*>",
            Ty.Cls(
                listOf(
                    Ty.Part("java/util/Map", listOf(classType("java/lang/String"))),
                    Ty.Part("Entry", listOf(classType("java/lang/Number")))
                )
            ) to "Map<String>.Entry<Number>",
            Ty.Cls(
                listOf(
                    Ty.Part("java", null),
                    Ty.Part("lang", null),
                    Ty.Part("String", null)
                )
            ) to "java.lang.String",
            Ty.Cls(listOf(Ty.Part("Box", emptyList()))) to "Box<>",
            Ty.Cls(
                listOf(
                    Ty.Part("java/util/List", listOf(Ty.Bound(true, classType("java/lang/Number"))))
                )
            ) to "java.util.List<out java.lang.Number>",
            Ty.Cls(listOf(Ty.Part("java/util/Map\$Entry", null))) to "java.util.Map\$Entry",
            Ty.Var("T\$ inner") to "T$ inner"
        )

        types.forEachIndexed { index, (type, expected) ->
            val short = index == 4 || index == 5
            assertEquals(expected, KotlinRenderer.render(type, short), "Kotlin class/type row $index")
        }
        assertEquals("Map\$Entry", KotlinRenderer.render(classType("java/util/Map\$Entry"), short = true))
    }

    @Test
    fun fieldAndUnnamedMethodRenderingHasExactSpacing() {
        assertEquals("T", KotlinRenderer.render(Ty.Var("T")))
        assertEquals("T", KotlinRenderer.render(Sig.Field(Ty.Var("T"))))
        assertEquals("T", KotlinRenderer.render(Sig.Field(Ty.Var("T")), name = "ignored"))

        val methods = listOf(
            Sig.Method(emptyList(), emptyList(), Ty.Prim('V'), emptyList()) to "() -> Unit",
            Sig.Method(
                emptyList(),
                listOf(Ty.Prim('I'), Ty.Prim('J')),
                Ty.Prim('Z'),
                emptyList()
            ) to "(Int, Long) -> Boolean",
            Sig.Method(
                listOf(TypeParam("T", 1, null, emptyList())),
                listOf(Ty.Var("T")),
                Ty.Prim('V'),
                emptyList()
            ) to "<T> (T) -> Unit",
            Sig.Method(
                listOf(
                    TypeParam("T", 1, classType("java/lang/Object"), listOf(classType("java/lang/Runnable"))),
                    TypeParam("U", 2, Ty.Var("T"), emptyList())
                ),
                emptyList(),
                Ty.Prim('V'),
                listOf(Ty.Var("T"), classType("java/lang/Exception"))
            ) to "<T : java.lang.Object & java.lang.Runnable, U : T> () -> Unit",
            Sig.Method(
                listOf(
                    TypeParam(
                        "T",
                        1,
                        null,
                        listOf(classType("java/lang/Runnable"), classType("java/io/Serializable"))
                    )
                ),
                emptyList(),
                Ty.Prim('V'),
                emptyList()
            ) to "<T : Runnable & Serializable> () -> Unit"
        )

        methods.forEachIndexed { index, (signature, expected) ->
            val short = index == 4
            assertEquals(expected, KotlinRenderer.render(signature, short), "Kotlin method row $index")
        }
    }

    @Test
    fun namedMethodsRetainNamesAndRenderGenericBounds() {
        val plainMethod = Sig.Method(emptyList(), listOf(Ty.Prim('I')), Ty.Prim('I'), emptyList())
        assertEquals("fun max(Int): Int", KotlinRenderer.render(plainMethod, name = "max"))
        assertEquals("fun odd name(Int): Int", KotlinRenderer.render(plainMethod, name = "odd name"))

        val method = Sig.Method(
            listOf(
                TypeParam(
                    "T",
                    1,
                    null,
                    listOf(
                        Ty.Cls(
                            listOf(
                                Ty.Part(
                                    "java/lang/Comparable",
                                    listOf(Ty.Bound(false, Ty.Var("T")))
                                )
                            )
                        )
                    )
                )
            ),
            listOf(
                Ty.Cls(
                    listOf(
                        Ty.Part("java/util/List", listOf(Ty.Bound(true, Ty.Var("T"))))
                    )
                )
            ),
            Ty.Var("T"),
            listOf(classType("java/io/IOException"))
        )

        assertEquals(
            "fun <T : java.lang.Comparable<in T>> max(java.util.List<out T>): T",
            KotlinRenderer.render(method, name = "max")
        )
        assertEquals(
            "fun <T : Comparable<in T>> max(List<out T>): T",
            KotlinRenderer.render(method, short = true, name = "max")
        )
    }

    @Test
    fun classSignaturesRetainSuperclassAndRenderInterfaces() {
        val signatures = listOf(
            Sig.ClassSig(emptyList(), classType("java/lang/Object"), emptyList()) to
                ": java.lang.Object",
            Sig.ClassSig(
                listOf(TypeParam("T", 1, null, emptyList())),
                classType("java/lang/Object"),
                listOf(
                    Ty.Cls(listOf(Ty.Part("java/util/List", listOf(Ty.Var("T"))))),
                    classType("java/lang/Runnable")
                )
            ) to "<T> : java.lang.Object, java.util.List<T>, java.lang.Runnable",
            Sig.ClassSig(
                listOf(TypeParam("T", 1, null, emptyList())),
                classType("java/lang/Object"),
                listOf(classType("java/lang/Runnable"))
            ) to "<T> : Object, Runnable"
        )

        signatures.forEachIndexed { index, (signature, expected) ->
            val short = index == 2
            val name = if (index == 2) "ignored" else null
            assertEquals(expected, KotlinRenderer.render(signature, short, name), "Kotlin class row $index")
        }
    }

    private fun classType(name: String, args: List<Ty>? = null): Ty.Cls =
        Ty.Cls(listOf(Ty.Part(name, args)))
}
