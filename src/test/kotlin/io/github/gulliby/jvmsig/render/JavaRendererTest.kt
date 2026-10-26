package io.github.gulliby.jvmsig.render

import io.github.gulliby.jvmsig.model.Sig
import io.github.gulliby.jvmsig.model.Ty
import io.github.gulliby.jvmsig.model.TypeParam
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaRendererTest {
    @Test
    fun primitiveNamesAndArraysMatchJavaDisplayText() {
        val primitiveCases = listOf(
            Triple("byte", Ty.Prim('B'), "byte"),
            Triple("char", Ty.Prim('C'), "char"),
            Triple("double", Ty.Prim('D'), "double"),
            Triple("float", Ty.Prim('F'), "float"),
            Triple("int", Ty.Prim('I'), "int"),
            Triple("long", Ty.Prim('J'), "long"),
            Triple("short", Ty.Prim('S'), "short"),
            Triple("boolean", Ty.Prim('Z'), "boolean"),
            Triple("void", Ty.Prim('V'), "void")
        )
        primitiveCases.forEach { (scenario, type, expected) ->
            assertEquals(expected, JavaRenderer.render(type), scenario)
        }

        val arrayCases = listOf(
            Triple("byte array", Ty.Arr(Ty.Prim('B')), "byte[]"),
            Triple("char array", Ty.Arr(Ty.Prim('C')), "char[]"),
            Triple("double array", Ty.Arr(Ty.Prim('D')), "double[]"),
            Triple("float array", Ty.Arr(Ty.Prim('F')), "float[]"),
            Triple("int array", Ty.Arr(Ty.Prim('I')), "int[]"),
            Triple("long array", Ty.Arr(Ty.Prim('J')), "long[]"),
            Triple("short array", Ty.Arr(Ty.Prim('S')), "short[]"),
            Triple("boolean array", Ty.Arr(Ty.Prim('Z')), "boolean[]"),
            Triple("nested primitive array", Ty.Arr(Ty.Arr(Ty.Prim('J'))), "long[][]"),
            Triple(
                "reference array",
                Ty.Arr(Ty.Cls(listOf(Ty.Part("java/lang/String", null)))),
                "java.lang.String[]"
            ),
            Triple("variable array", Ty.Arr(Ty.Var("T")), "T[]")
        )
        arrayCases.forEach { (scenario, type, expected) ->
            assertEquals(expected, JavaRenderer.render(type), scenario)
        }
    }

    @Test
    fun rendererDefaultsRenderVariablesAndFieldsWithoutNewlines() {
        val variable = Ty.Var("T")
        val field = Sig.Field(Ty.Var("T"))

        assertEquals("T", JavaRenderer.render(variable))
        assertEquals("T", JavaRenderer.render(field))
        assertEquals("T", JavaRenderer.render(field, name = "ignored"))
    }

    @Test
    fun classPartsVarianceAndArgumentPresenceRenderExactly() {
        val cases = listOf(
            Triple("wildcard", Ty.Star, "?"),
            Triple("upper variance", Ty.Bound(true, Ty.Var("T")), "? extends T"),
            Triple("lower variance", Ty.Bound(false, Ty.Var("T")), "? super T"),
            Triple(
                "per-part arguments",
                Ty.Cls(
                    listOf(
                        Ty.Part("java/util/Map", listOf(Ty.Var("K"), Ty.Arr(Ty.Var("V")))),
                        Ty.Part("Entry", listOf(Ty.Star))
                    )
                ),
                "java.util.Map<K, V[]>.Entry<?>"
            ),
            Triple(
                "nested variance",
                Ty.Cls(
                    listOf(
                        Ty.Part(
                            "java/util/List",
                            listOf(Ty.Bound(true, Ty.Cls(listOf(Ty.Part("java/lang/Number", null)))))
                        )
                    )
                ),
                "java.util.List<? extends java.lang.Number>"
            ),
            Triple(
                "dollar sign preserved",
                Ty.Cls(listOf(Ty.Part("java/util/Map\$Entry", null))),
                "java.util.Map\$Entry"
            ),
            Triple(
                "present empty arguments",
                Ty.Cls(listOf(Ty.Part("Box", emptyList()))),
                "Box<>"
            ),
            Triple(
                "absent arguments",
                Ty.Cls(listOf(Ty.Part("Box", null))),
                "Box"
            )
        )
        cases.forEach { (scenario, type, expected) ->
            assertEquals(expected, JavaRenderer.render(type), scenario)
        }
    }

    @Test
    fun shortNamesOnlyShortenTheFirstClassPartRecursively() {
        val cases = listOf(
            Triple(
                "short class arguments recursively",
                Ty.Cls(
                    listOf(
                        Ty.Part("java/util/Map", listOf(Ty.Cls(listOf(Ty.Part("java/lang/String", null))))),
                        Ty.Part("Entry", listOf(Ty.Cls(listOf(Ty.Part("java/lang/Number", null)))))
                    )
                ),
                "Map<String>.Entry<Number>"
            ),
            Triple(
                "dots in inner parts remain",
                Ty.Cls(
                    listOf(
                        Ty.Part("java", null),
                        Ty.Part("lang", null),
                        Ty.Part("String", null)
                    )
                ),
                "java.lang.String"
            ),
            Triple(
                "dollar sign stays in shortened binary name",
                Ty.Cls(listOf(Ty.Part("java/util/Map\$Entry", null))),
                "Map\$Entry"
            )
        )
        cases.forEach { (scenario, type, expected) ->
            assertEquals(expected, JavaRenderer.render(type, short = true), scenario)
        }
    }

    @Test
    fun methodsRenderParametersReturnsNamesAndEmptyLists() {
        val cases = listOf(
            Triple(
                "empty method lists",
                Sig.Method(emptyList(), emptyList(), Ty.Prim('V'), emptyList()),
                "void()"
            ),
            Triple(
                "parameters",
                Sig.Method(emptyList(), listOf(Ty.Prim('I'), Ty.Prim('J')), Ty.Prim('Z'), emptyList()),
                "boolean(int, long)"
            ),
            Triple(
                "named nongeneric method",
                Sig.Method(emptyList(), listOf(Ty.Prim('I')), Ty.Prim('I'), emptyList()),
                "int max(int)"
            )
        )
        cases.forEachIndexed { index, (scenario, method, expected) ->
            val name = if (index == 2) "max" else null
            assertEquals(expected, JavaRenderer.render(method, name = name), scenario)
        }

        val verbatimName = Sig.Method(emptyList(), listOf(Ty.Prim('I')), Ty.Prim('I'), emptyList())
        assertEquals("int method name();(int)", JavaRenderer.render(verbatimName, name = "method name();"))
    }

    @Test
    fun genericMethodsRenderBoundsThrowsAndShortNames() {
        val unbounded = Sig.Method(
            listOf(TypeParam("T", 1, null, emptyList())),
            listOf(Ty.Var("T")),
            Ty.Prim('V'),
            emptyList()
        )
        assertEquals("<T> void(T)", JavaRenderer.render(unbounded))

        val orderedBounds = Sig.Method(
            listOf(
                TypeParam(
                    "T",
                    1,
                    Ty.Cls(listOf(Ty.Part("java/lang/Object", null))),
                    listOf(Ty.Cls(listOf(Ty.Part("java/lang/Runnable", null))))
                ),
                TypeParam("U", 1, Ty.Var("T"), emptyList())
            ),
            emptyList(),
            Ty.Prim('V'),
            listOf(Ty.Var("T"), Ty.Cls(listOf(Ty.Part("java/lang/Exception", null))))
        )
        assertEquals(
            "<T extends java.lang.Object & java.lang.Runnable, U extends T> void() throws T, java.lang.Exception",
            JavaRenderer.render(orderedBounds)
        )

        val interfaceOnlyBounds = Sig.Method(
            listOf(
                TypeParam(
                    "T",
                    1,
                    null,
                    listOf(
                        Ty.Cls(listOf(Ty.Part("java/lang/Runnable", null))),
                        Ty.Cls(listOf(Ty.Part("java/io/Serializable", null)))
                    )
                )
            ),
            emptyList(),
            Ty.Prim('V'),
            emptyList()
        )
        assertEquals(
            "<T extends Runnable & Serializable> void()",
            JavaRenderer.render(interfaceOnlyBounds, short = true)
        )

        val comparableOfT = Ty.Cls(
            listOf(
                Ty.Part(
                    "java/lang/Comparable",
                    listOf(Ty.Bound(false, Ty.Var("T")))
                )
            )
        )
        val listOfExtendsT = Ty.Cls(
            listOf(
                Ty.Part(
                    "java/util/List",
                    listOf(Ty.Bound(true, Ty.Var("T")))
                )
            )
        )
        val genericMax = Sig.Method(
            listOf(TypeParam("T", 1, null, listOf(comparableOfT))),
            listOf(listOfExtendsT),
            Ty.Var("T"),
            listOf(Ty.Cls(listOf(Ty.Part("java/io/IOException", null))))
        )

        assertEquals(
            "<T extends java.lang.Comparable<? super T>> T max(java.util.List<? extends T>) throws java.io.IOException",
            JavaRenderer.render(genericMax, name = "max")
        )
        assertEquals(
            "<T extends Comparable<? super T>> T max(List<? extends T>) throws IOException",
            JavaRenderer.render(genericMax, short = true, name = "max")
        )
    }

    @Test
    fun classSignaturesRenderSuperclassInterfacesAndShortNames() {
        val noOptionalLists = Sig.ClassSig(
            emptyList(),
            Ty.Cls(listOf(Ty.Part("java/lang/Object", null))),
            emptyList()
        )
        assertEquals("extends java.lang.Object", JavaRenderer.render(noOptionalLists))

        val genericInterfaces = Sig.ClassSig(
            listOf(TypeParam("T", 1, null, emptyList())),
            Ty.Cls(listOf(Ty.Part("java/lang/Object", null))),
            listOf(
                Ty.Cls(listOf(Ty.Part("java/util/List", listOf(Ty.Var("T"))))),
                Ty.Cls(listOf(Ty.Part("java/lang/Runnable", null)))
            )
        )
        assertEquals(
            "<T> extends java.lang.Object implements java.util.List<T>, java.lang.Runnable",
            JavaRenderer.render(genericInterfaces)
        )

        val shortClass = Sig.ClassSig(
            listOf(TypeParam("T", 1, null, emptyList())),
            Ty.Cls(listOf(Ty.Part("java/lang/Object", null))),
            listOf(Ty.Cls(listOf(Ty.Part("java/lang/Runnable", null))))
        )
        assertEquals(
            "<T> extends Object implements Runnable",
            JavaRenderer.render(shortClass, short = true, name = "ignored")
        )
    }
}
