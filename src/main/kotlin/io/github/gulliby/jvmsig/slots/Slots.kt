package io.github.gulliby.jvmsig.slots

import io.github.gulliby.jvmsig.erase.Eraser
import io.github.gulliby.jvmsig.model.Problem
import io.github.gulliby.jvmsig.model.Sig
import io.github.gulliby.jvmsig.model.Ty

object Slots {
    fun render(sig: Sig, isStatic: Boolean = false): String {
        if (sig !is Sig.Method) {
            throw Problem.methodRequired()
        }

        val erased = Eraser.eraseMethod(sig)
        var total = if (isStatic) 0 else 1
        for (parameter in erased.params) {
            total += parameter.slotWidth()
            if (total > MAX_SLOTS) {
                throw Problem.tooManySlots()
            }
        }

        val rows = mutableListOf<Row>()
        var nextSlot = 0
        if (!isStatic) {
            rows += Row("0", "this", "")
            nextSlot = 1
        }
        for ((index, parameter) in erased.params.withIndex()) {
            val width = parameter.slotWidth()
            val slot = if (width == 2) "$nextSlot-${nextSlot + 1}" else nextSlot.toString()
            rows += Row(slot, index.toString(), parameter.javaName())
            nextSlot += width
        }

        val slotWidth = maxOf(HEADER_SLOT.length, rows.maxOfOrNull { it.slot.length } ?: 0)
        val argWidth = maxOf(HEADER_ARG.length, rows.maxOfOrNull { it.arg.length } ?: 0)
        val lines = mutableListOf<String>()
        lines += formatRow(Row(HEADER_SLOT, HEADER_ARG, HEADER_TYPE), slotWidth, argWidth)
        for (row in rows) {
            lines += formatRow(row, slotWidth, argWidth)
        }
        lines += "total $total ${if (total == 1) "slot" else "slots"}"
        lines += "returns ${erased.ret.javaName()}"
        return lines.joinToString(separator = "\n", postfix = "\n")
    }

    private fun formatRow(row: Row, slotWidth: Int, argWidth: Int): String =
        (
            row.slot.padEnd(slotWidth) + "  " +
                row.arg.padEnd(argWidth) + "  " +
                row.type
            ).trimEnd()

    private fun Ty.slotWidth(): Int =
        if (this is Ty.Prim && (code == 'J' || code == 'D')) 2 else 1

    private fun Ty.javaName(): String =
        when (this) {
            is Ty.Prim -> primitiveName(code)
            is Ty.Arr -> "${elem.javaName()}[]"
            is Ty.Cls -> parts.joinToString(".") { it.name.replace('/', '.') }
            is Ty.Var -> name
            is Ty.Bound -> bound.javaName()
            Ty.Star -> "java.lang.Object"
        }

    private fun primitiveName(code: Char): String =
        when (code) {
            'B' -> "byte"
            'C' -> "char"
            'D' -> "double"
            'F' -> "float"
            'I' -> "int"
            'J' -> "long"
            'S' -> "short"
            'V' -> "void"
            'Z' -> "boolean"
            else -> code.toString()
        }

    private data class Row(val slot: String, val arg: String, val type: String)

    private const val HEADER_SLOT = "SLOT"
    private const val HEADER_ARG = "ARG"
    private const val HEADER_TYPE = "TYPE"
    private const val MAX_SLOTS = 255
}
