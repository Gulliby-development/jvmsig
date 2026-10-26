package io.github.gulliby.jvmsig.cmd

import io.github.gulliby.jvmsig.base.Output
import kotlin.test.Test
import kotlin.test.assertEquals

class EraseCommandTest {
    @Test
    fun workedErasureExamplesProduceTheirLiteralDescriptors() {
        val examples = listOf(
            "[[Ljava/lang/String;" to "[[Ljava/lang/String;",
            "Ljava/util/Map<TK;TV;>.Entry<TK;>;" to "Ljava/util/Map\$Entry;",
            "<T:TU;U::Ljava/lang/Number;>(TT;[TU;)TT;" to
                "(Ljava/lang/Number;[Ljava/lang/Number;)Ljava/lang/Number;",
            "<T::Ljava/lang/Comparable<TT;>;>([TT;)TT;" to
                "([Ljava/lang/Comparable;)Ljava/lang/Comparable;",
            "<A:TB;B:TA;>(TA;TB;)TA;" to
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            "<T:Ljava/lang/Number;>(TX;)V^TT;" to "(Ljava/lang/Object;)V",
            "<T:>Ljava/lang/Object;Ljava/util/List<TT;>;Ljava/lang/Runnable;" to
                "Ljava/lang/Object; Ljava/util/List; Ljava/lang/Runnable;",
            "TT;" to "Ljava/lang/Object;"
        )

        examples.forEachIndexed { index, (signature, expected) ->
            val out = Output()
            val status = Cli.run(listOf("erase", signature), lazy { byteArrayOf() }, out)

            assertEquals("$expected\n", out.stdout.decodeToString(), "stdout for example ${index + 1}")
            assertEquals("", out.stderr.decodeToString(), "stderr for example ${index + 1}")
            assertEquals(0, status, "exit code for example ${index + 1}")
        }
    }

    @Test
    fun continuesAfterSyntaxProblemAndNumbersEveryOperand() {
        val out = Output()

        val status = Cli.run(listOf("erase", "Q", "TT;", "I"), lazy { byteArrayOf() }, out)

        assertEquals("Ljava/lang/Object;\nI\n", out.stdout.decodeToString())
        assertEquals("ARG#1:1: JS003 not a type: 'Q'\n", out.stderr.decodeToString())
        assertEquals(1, status)
    }
}
