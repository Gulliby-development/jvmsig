package io.github.gulliby.jvmsig.read

import io.github.gulliby.jvmsig.base.Text
import io.github.gulliby.jvmsig.model.Problem
import io.github.gulliby.jvmsig.model.Ty

internal class TypeReader(private val cursor: Cursor) {
    private val variableUses = mutableListOf<Pair<String, Int>>()

    val uses: List<Pair<String, Int>>
        get() = variableUses.toList()

    fun readName(): String {
        val first = cursor.peek()
        if (first == null) {
            throw Problem.unexpectedEnd(cursor.column, "a name")
        }
        if (isNameDelimiter(first)) {
            throw Problem.emptyName(cursor.column)
        }

        val name = StringBuilder()
        var codePoint = cursor.peek()
        while (codePoint != null && !isNameDelimiter(codePoint)) {
            name.appendCodePoint(codePoint)
            cursor.advance()
            codePoint = cursor.peek()
        }
        return name.toString()
    }

    fun readType(expected: String = "a type"): Ty {
        val codePoint = cursor.peek()
        if (codePoint == null) {
            throw Problem.unexpectedEnd(cursor.column, expected)
        }

        if (codePoint == 'V'.code) {
            throw Problem.voidNotReturn(cursor.column)
        }
        if (Text.has(PRIMITIVES, codePoint)) {
            cursor.advance()
            return Ty.Prim(codePoint.toChar())
        }

        return when (codePoint) {
            'L'.code -> readClassType()
            'T'.code -> readVariableType()
            '['.code -> readArrayType()
            else -> throw Problem.notType(cursor.column, Text.shown(codePoint))
        }
    }

    fun readReference(expected: String = "a type", wildcardBound: Boolean = false): Ty {
        val codePoint = cursor.peek()
        if (codePoint == null) {
            throw Problem.unexpectedEnd(cursor.column, expected)
        }
        if (codePoint == 'V'.code) {
            throw Problem.voidNotReturn(cursor.column)
        }
        if (Text.has(PRIMITIVES, codePoint)) {
            if (wildcardBound) {
                throw Problem.wildcardBoundNotReference(cursor.column)
            }
            throw Problem.typeArgumentNotReference(cursor.column)
        }

        return when (codePoint) {
            'L'.code -> readClassType()
            'T'.code -> readVariableType()
            '['.code -> readArrayType()
            else -> throw Problem.notType(cursor.column, Text.shown(codePoint))
        }
    }

    fun readClassType(): Ty.Cls {
        cursor.expect('L'.code)
        var firstName = readName()
        while (cursor.consumeIf('/'.code)) {
            firstName += "/" + readName()
        }

        val parts = mutableListOf<Ty.Part>()
        parts.add(Ty.Part(firstName, readOptionalArguments()))
        while (cursor.consumeIf('.'.code)) {
            val innerName = readName()
            parts.add(Ty.Part(innerName, readOptionalArguments()))
        }
        cursor.expect(';'.code)
        return Ty.Cls(parts)
    }

    fun readVariableType(): Ty.Var {
        val column = cursor.column
        cursor.expect('T'.code)
        val name = readName()
        variableUses.add(name to column)
        cursor.expect(';'.code)
        return Ty.Var(name)
    }

    fun readThrownType(): Ty {
        val codePoint = cursor.peek()
        if (codePoint == null) {
            throw Problem.unexpectedEnd(cursor.column, "a class or type variable")
        }
        if (codePoint == 'V'.code) {
            throw Problem.voidNotReturn(cursor.column)
        }

        return when (codePoint) {
            'L'.code -> readClassType()
            'T'.code -> readVariableType()
            else -> throw Problem.invalidThrownType(cursor.column)
        }
    }

    private fun readArrayType(): Ty {
        var dimensions = 0
        while (cursor.peek() == '['.code) {
            val bracketColumn = cursor.column
            cursor.advance()
            dimensions++
            if (dimensions > MAX_ARRAY_DIMENSIONS) {
                throw Problem.tooManyDimensions(bracketColumn)
            }
        }

        var element = readType()
        repeat(dimensions) {
            element = Ty.Arr(element)
        }
        return element
    }

    private fun readOptionalArguments(): List<Ty>? {
        if (!cursor.consumeIf('<'.code)) {
            return null
        }

        if (cursor.isAtEnd) {
            throw Problem.unexpectedEnd(cursor.column, Text.shown('>'.code))
        }
        if (cursor.peek() == '>'.code) {
            throw Problem.emptyTypeArguments(cursor.column)
        }

        val arguments = mutableListOf<Ty>()
        while (true) {
            arguments.add(readTypeArgument())
            if (cursor.consumeIf('>'.code)) {
                return arguments
            }
            if (cursor.isAtEnd) {
                throw Problem.unexpectedEnd(cursor.column, Text.shown('>'.code))
            }
        }
    }

    private fun readTypeArgument(): Ty {
        return when (cursor.peek()) {
            '*'.code -> {
                cursor.advance()
                Ty.Star
            }
            '+'.code -> {
                cursor.advance()
                Ty.Bound(true, readReference(wildcardBound = true))
            }
            '-'.code -> {
                cursor.advance()
                Ty.Bound(false, readReference(wildcardBound = true))
            }
            else -> readReference()
        }
    }

    private fun isNameDelimiter(codePoint: Int): Boolean {
        return Text.has(NAME_DELIMITERS, codePoint)
    }

    private companion object {
        const val PRIMITIVES = "BCDFIJSZ"
        const val NAME_DELIMITERS = ".;[/<>:"
        const val MAX_ARRAY_DIMENSIONS = 255
    }
}
