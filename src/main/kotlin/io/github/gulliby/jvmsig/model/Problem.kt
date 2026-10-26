package io.github.gulliby.jvmsig.model

class Problem(val code: String, val col: Int, val text: String) : Exception(text) {
    companion object {
        fun unexpectedEnd(col: Int, what: String): Problem =
            Problem("JS001", col, "unexpected end, expected $what")

        fun unexpected(col: Int, shown: String, what: String): Problem =
            Problem("JS002", col, "unexpected $shown, expected $what")

        fun notType(col: Int, shown: String): Problem =
            Problem("JS003", col, "not a type: $shown")

        fun voidNotReturn(col: Int): Problem =
            Problem("JS004", col, "void is only allowed as a return type")

        fun emptyName(col: Int): Problem =
            Problem("JS005", col, "empty name")

        fun emptyTypeArguments(col: Int): Problem =
            Problem("JS006", col, "empty type argument list")

        fun tooManyDimensions(col: Int): Problem =
            Problem("JS007", col, "more than 255 array dimensions")

        fun trailingText(col: Int): Problem =
            Problem("JS008", col, "unexpected text after the signature")

        fun emptyTypeParameters(col: Int): Problem =
            Problem("JS009", col, "empty type parameter list")

        fun wildcardBoundNotReference(col: Int): Problem =
            Problem("JS010", col, "a wildcard bound must be a reference type")

        fun typeArgumentNotReference(col: Int): Problem =
            Problem("JS011", col, "a type argument must be a reference type")

        fun undeclaredVariable(col: Int, name: String): Problem =
            Problem("JS012", col, "type variable $name is not declared")

        fun duplicateParameter(col: Int, name: String): Problem =
            Problem("JS013", col, "type parameter $name is declared twice")

        fun invalidThrownType(col: Int): Problem =
            Problem("JS014", col, "a thrown type must be a class or a type variable")

        fun cyclicBound(col: Int, name: String): Problem =
            Problem("JS015", col, "type parameter $name has a cyclic bound")

        fun methodRequired(): Problem =
            Problem("JS016", 1, "a method signature is required")

        fun tooManySlots(): Problem =
            Problem("JS017", 1, "more than 255 argument slots")
    }
}
