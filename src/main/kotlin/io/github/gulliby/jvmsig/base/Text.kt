package io.github.gulliby.jvmsig.base

object Text {
    fun shown(codePoint: Int): String {
        if (codePoint in 0x0000..0x0020 || codePoint in 0x007F..0x009F) {
            return "U+${uppercaseHex(codePoint)}"
        }
        return "'${String(Character.toChars(codePoint))}'"
    }

    fun has(set: String, codePoint: Int): Boolean {
        return codePoint in Char.MIN_VALUE.code..Char.MAX_VALUE.code && set.indexOf(codePoint.toChar()) >= 0
    }

    fun plural(count: Int, word: String): String {
        return if (count == 1) word else word + "s"
    }

    private fun uppercaseHex(value: Int): String {
        val digits = Integer.toHexString(value).padStart(4, '0')
        return buildString(digits.length) {
            for (digit in digits) {
                append(if (digit in 'a'..'f') (digit.code - 'a'.code + 'A'.code).toChar() else digit)
            }
        }
    }
}
