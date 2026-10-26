package io.github.gulliby.jvmsig.base

import kotlin.test.Test
import kotlin.test.assertEquals

class TextTest {
    @Test
    fun shownFormatsControlBoundariesAndUnicodeCodePoints() {
        val cases = listOf(
            0x0000 to "U+0000",
            0x001F to "U+001F",
            0x0020 to "U+0020",
            0x0021 to "'!'",
            0x0051 to "'Q'",
            0x007E to "'~'",
            0x007F to "U+007F",
            0x0085 to "U+0085",
            0x009F to "U+009F",
            0x00A0 to "'\u00A0'",
            0x1F600 to "'\uD83D\uDE00'"
        )

        for ((codePoint, expected) in cases) {
            assertEquals(expected, Text.shown(codePoint), "shown($codePoint)")
        }
    }

    @Test
    fun hasChecksBmpMembershipAndPluralHandlesZeroSingularAndPlural() {
        val membershipCases = listOf(
            Triple(".;[/<>:", 0x003B, true),
            Triple(".;[/<>:", 0x0041, false),
            Triple(".;[/<>:", 0x1003B, false),
            Triple("", 0x003B, false)
        )

        for ((set, codePoint, expected) in membershipCases) {
            assertEquals(expected, Text.has(set, codePoint), "has($set, $codePoint)")
        }

        val pluralCases = listOf(
            Triple(0, "problem", "problems"),
            Triple(1, "signature", "signature"),
            Triple(2, "slot", "slots")
        )

        for ((count, word, expected) in pluralCases) {
            assertEquals(expected, Text.plural(count, word), "plural($count, $word)")
        }
    }
}
