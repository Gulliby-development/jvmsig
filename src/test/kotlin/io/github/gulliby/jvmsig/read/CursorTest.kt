package io.github.gulliby.jvmsig.read

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CursorTest {
    @Test
    fun traversesEmptyBmpAndSupplementaryTextByCodePoint() {
        val cases = listOf(
            Triple("", emptyList<Int>(), 1),
            Triple("A", listOf('A'.code), 2),
            Triple("A\uD83D\uDE00B", listOf('A'.code, 0x1F600, 'B'.code), 4)
        )

        for ((text, codePoints, eofColumn) in cases) {
            val cursor = Cursor(text)
            assertEquals(1, cursor.column)
            for ((index, codePoint) in codePoints.withIndex()) {
                assertFalse(cursor.isAtEnd)
                assertEquals(index + 1, cursor.column)
                assertEquals(codePoint, cursor.peek())
                cursor.advance()
            }
            assertTrue(cursor.isAtEnd)
            assertEquals(eofColumn, cursor.column)
            cursor.advance()
            assertEquals(eofColumn, cursor.column)
        }
    }

    @Test
    fun consumeIfMatchesWholeCodePointsAndLeavesMismatchesInPlace() {
        val cursor = Cursor("A\uD800\uDC3B;")

        assertTrue(cursor.consumeIf('A'.code))
        assertEquals(2, cursor.column)
        assertFalse(cursor.consumeIf(';'.code))
        assertEquals(0x1003B, cursor.peek())
        assertEquals(2, cursor.column)
        assertTrue(cursor.consumeIf(0x1003B))
        assertEquals(3, cursor.column)
        assertTrue(cursor.consumeIf(';'.code))
        assertTrue(cursor.isAtEnd)
        assertEquals(4, cursor.column)
    }

    @Test
    fun nameReaderPreservesSupplementaryCodePointThatSharesSemicolonLowBits() {
        val cursor = Cursor("\uD800\uDC3B;")
        val reader = TypeReader(cursor)

        assertEquals("\uD800\uDC3B", reader.readName())
        assertEquals(2, cursor.column)
        assertEquals(';'.code, cursor.peek())
        cursor.expect(';'.code)
        assertTrue(cursor.isAtEnd)
        assertEquals(3, cursor.column)
    }
}
