package io.github.gulliby.jvmsig.read

import io.github.gulliby.jvmsig.base.Text
import io.github.gulliby.jvmsig.model.Problem

internal class Cursor(text: String) {
    private val codePoints: IntArray = text.codePoints().toArray()
    private var position: Int = 0

    val column: Int
        get() = position + 1

    val isAtEnd: Boolean
        get() = position >= codePoints.size

    fun peek(): Int? {
        return if (isAtEnd) null else codePoints[position]
    }

    fun advance() {
        if (!isAtEnd) {
            position++
        }
    }

    fun consumeIf(codePoint: Int): Boolean {
        if (peek() != codePoint) {
            return false
        }
        advance()
        return true
    }

    fun expect(codePoint: Int) {
        val actual = peek()
        if (actual == null) {
            throw Problem.unexpectedEnd(column, Text.shown(codePoint))
        }
        if (actual != codePoint) {
            throw Problem.unexpected(column, Text.shown(actual), Text.shown(codePoint))
        }
        advance()
    }
}
