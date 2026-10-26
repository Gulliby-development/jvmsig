package io.github.gulliby.jvmsig.cmd

import io.github.gulliby.jvmsig.base.Output
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ShowCommandTest {
    private data class ShowCase(
        val name: String,
        val args: List<String>,
        val stdout: String
    )

    @Test
    fun requestJavaAndKotlinExamplesMatchTheirLiteralOutput() {
        val cases = listOf(
            ShowCase("Java primitive", listOf("show", "I"), "int\n"),
            ShowCase("Java nested primitive array", listOf("show", "[[J"), "long[][]\n"),
            ShowCase(
                "Java generic inner class and arguments",
                listOf("show", "Ljava/util/Map<TK;[TV;>.Entry<*>;"),
                "java.util.Map<K, V[]>.Entry<?>\n"
            ),
            ShowCase(
                "Java shortened binary name",
                listOf("show", "--short", "Ljava/util/Map\$Entry;"),
                "Map\$Entry\n"
            ),
            ShowCase(
                "Java lower-bounded wildcard",
                listOf("show", "Ljava/util/List<-TT;>;"),
                "java.util.List<? super T>\n"
            ),
            ShowCase("Java method", listOf("show", "(IJ)Z"), "boolean(int, long)\n"),
            ShowCase(
                "Java named generic method and throws",
                listOf(
                    "show",
                    "--name",
                    "max",
                    "<T::Ljava/lang/Comparable<-TT;>;>(Ljava/util/List<+TT;>;)TT;^Ljava/io/IOException;"
                ),
                "<T extends java.lang.Comparable<? super T>> T max(java.util.List<? extends T>) " +
                    "throws java.io.IOException\n"
            ),
            ShowCase(
                "Java type parameter bounds and throws",
                listOf("show", "<T:Ljava/lang/Object;:Ljava/lang/Runnable;U:TT;>()V^TT;^Ljava/lang/Exception;"),
                "<T extends java.lang.Object & java.lang.Runnable, U extends T> " +
                    "void() throws T, java.lang.Exception\n"
            ),
            ShowCase("Java unbounded type parameter", listOf("show", "<T:>(TT;)V"), "<T> void(T)\n"),
            ShowCase(
                "Java class signature",
                listOf("show", "<T:>Ljava/lang/Object;Ljava/util/List<TT;>;Ljava/lang/Runnable;"),
                "<T> extends java.lang.Object implements java.util.List<T>, java.lang.Runnable\n"
            ),
            ShowCase(
                "Kotlin reference array",
                listOf("show", "--kotlin", "[Ljava/lang/String;"),
                "Array<java.lang.String>\n"
            ),
            ShowCase(
                "Kotlin upper-bounded wildcard",
                listOf("show", "--kotlin", "Ljava/util/List<+Ljava/lang/Number;>;"),
                "java.util.List<out java.lang.Number>\n"
            ),
            ShowCase(
                "Kotlin shortened generic method",
                listOf(
                    "show",
                    "--kotlin",
                    "--short",
                    "<T:Ljava/lang/Object;U:>(Ljava/util/List<+TT;>;[[JI)V"
                ),
                "<T : Object, U> (List<out T>, Array<LongArray>, Int) -> Unit\n"
            ),
            ShowCase(
                "Kotlin named generic method and throws omission",
                listOf(
                    "show",
                    "--kotlin",
                    "--name",
                    "max",
                    "<T::Ljava/lang/Comparable<-TT;>;>(Ljava/util/List<+TT;>;)TT;^Ljava/io/IOException;"
                ),
                "fun <T : java.lang.Comparable<in T>> max(java.util.List<out T>): T\n"
            ),
            ShowCase(
                "Kotlin class signature",
                listOf("show", "--kotlin", "<T:>Ljava/lang/Object;Ljava/util/List<TT;>;"),
                "<T> : java.lang.Object, java.util.List<T>\n"
            ),
            ShowCase(
                "Name is ignored for a non-method without semantic validation",
                listOf("show", "--name", "x", "TT;"),
                "T\n"
            )
        )

        cases.forEach { case ->
            val output = Output()
            val exitCode = Cli.run(case.args, lazyOf(byteArrayOf()), output)

            assertContentEquals(
                case.stdout.toByteArray(Charsets.UTF_8),
                output.stdout,
                "stdout for ${case.name}"
            )
            assertContentEquals(byteArrayOf(), output.stderr, "stderr for ${case.name}")
            kotlin.test.assertEquals(0, exitCode, "exit code for ${case.name}")
        }
    }
}
