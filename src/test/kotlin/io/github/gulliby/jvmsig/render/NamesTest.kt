package io.github.gulliby.jvmsig.render

import kotlin.test.Test
import kotlin.test.assertEquals

class NamesTest {
    @Test
    fun classNamesPreserveOrShortenOnlyPackagePath() {
        assertEquals("java.util.Map\$Entry", Names.className("java/util/Map\$Entry"))

        val cases = listOf(
            Triple("java/util/Map\$Entry", true, "Map\$Entry"),
            Triple("Simple", true, "Simple"),
            Triple("java.lang.String", true, "java.lang.String")
        )

        cases.forEach { (name, short, expected) ->
            assertEquals(expected, Names.className(name, short), "name=$name, short=$short")
        }
    }
}
