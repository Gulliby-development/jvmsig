package io.github.gulliby.jvmsig.read

import io.github.gulliby.jvmsig.base.Text
import io.github.gulliby.jvmsig.model.Parsed
import io.github.gulliby.jvmsig.model.Problem
import io.github.gulliby.jvmsig.model.Sig
import io.github.gulliby.jvmsig.model.Ty
import io.github.gulliby.jvmsig.model.TypeParam

object SigReader {
    fun read(text: String): Parsed {
        val cursor = Cursor(text)
        val typeReader = TypeReader(cursor)
        val typeParameters = if (cursor.consumeIf('<'.code)) {
            readTypeParameters(cursor, typeReader)
        } else {
            emptyList()
        }

        val signature = if (typeParameters.isEmpty()) {
            if (cursor.peek() == '('.code) {
                readMethod(cursor, typeReader, typeParameters)
            } else {
                Sig.Field(typeReader.readType())
            }
        } else {
            when (cursor.peek()) {
                '('.code -> readMethod(cursor, typeReader, typeParameters)
                'L'.code -> readClass(cursor, typeReader, typeParameters)
                null -> throw Problem.unexpectedEnd(cursor.column, TYPE_PARAMETER_KIND)
                else -> throw kindMismatch(cursor)
            }
        }

        if (!cursor.isAtEnd) {
            throw Problem.trailingText(cursor.column)
        }
        return Parsed(signature, typeParameters, typeReader.uses)
    }

    private fun readTypeParameters(cursor: Cursor, typeReader: TypeReader): List<TypeParam> {
        if (cursor.isAtEnd) {
            throw Problem.unexpectedEnd(cursor.column, Text.shown('>'.code))
        }
        if (cursor.peek() == '>'.code) {
            throw Problem.emptyTypeParameters(cursor.column)
        }

        val parameters = mutableListOf<TypeParam>()
        while (true) {
            val nameColumn = cursor.column
            val name = typeReader.readName()
            cursor.expect(':'.code)

            val classBound = if (cursor.peek() == ':'.code || cursor.peek() == '>'.code) {
                null
            } else {
                typeReader.readReference()
            }

            val interfaceBounds = mutableListOf<Ty>()
            while (cursor.consumeIf(':'.code)) {
                interfaceBounds.add(typeReader.readReference())
            }

            parameters.add(TypeParam(name, nameColumn, classBound, interfaceBounds))
            if (cursor.consumeIf('>'.code)) {
                return parameters
            }
            if (cursor.isAtEnd) {
                throw Problem.unexpectedEnd(cursor.column, Text.shown('>'.code))
            }
        }
    }

    private fun readMethod(
        cursor: Cursor,
        typeReader: TypeReader,
        typeParameters: List<TypeParam>
    ): Sig.Method {
        cursor.expect('('.code)
        val parameters = mutableListOf<Ty>()
        while (cursor.peek() != ')'.code) {
            if (cursor.isAtEnd) {
                throw Problem.unexpectedEnd(cursor.column, Text.shown(')'.code))
            }
            parameters.add(typeReader.readType())
        }
        cursor.advance()

        val returnType = when (cursor.peek()) {
            null -> throw Problem.unexpectedEnd(cursor.column, "a return type")
            'V'.code -> {
                cursor.advance()
                Ty.Prim('V')
            }
            else -> typeReader.readType("a return type")
        }

        val thrownTypes = mutableListOf<Ty>()
        while (cursor.consumeIf('^'.code)) {
            thrownTypes.add(typeReader.readThrownType())
        }
        return Sig.Method(typeParameters, parameters, returnType, thrownTypes)
    }

    private fun readClass(
        cursor: Cursor,
        typeReader: TypeReader,
        typeParameters: List<TypeParam>
    ): Sig.ClassSig {
        val superclass = typeReader.readClassType()
        val interfaces = mutableListOf<Ty.Cls>()
        while (cursor.peek() == 'L'.code) {
            interfaces.add(typeReader.readClassType())
        }
        return Sig.ClassSig(typeParameters, superclass, interfaces)
    }

    private fun kindMismatch(cursor: Cursor): Problem {
        val codePoint = cursor.peek()
        return if (codePoint == null) {
            Problem.unexpectedEnd(cursor.column, TYPE_PARAMETER_KIND)
        } else {
            Problem.unexpected(cursor.column, Text.shown(codePoint), TYPE_PARAMETER_KIND)
        }
    }

    private const val TYPE_PARAMETER_KIND = "'(' or the superclass"
}
