package io.github.gulliby.jvmsig.render

import io.github.gulliby.jvmsig.model.Sig
import io.github.gulliby.jvmsig.model.Ty
import io.github.gulliby.jvmsig.model.TypeParam

object JavaRenderer {
    fun render(type: Ty, short: Boolean = false): String =
        renderType(type, short)

    fun render(sig: Sig, short: Boolean = false, name: String? = null): String =
        when (sig) {
            is Sig.Field -> renderType(sig.type, short)
            is Sig.Method -> renderMethod(sig, short, name)
            is Sig.ClassSig -> renderClass(sig, short)
        }

    private fun renderType(type: Ty, short: Boolean): String =
        when (type) {
            is Ty.Prim -> primitiveName(type.code)
            is Ty.Arr -> "${renderType(type.elem, short)}[]"
            is Ty.Var -> type.name
            is Ty.Bound -> {
                val variance = if (type.extends) "? extends " else "? super "
                variance + renderType(type.bound, short)
            }
            is Ty.Cls -> type.parts.mapIndexed { index, part ->
                val partName = if (index == 0) Names.className(part.name, short) else part.name
                partName + renderArguments(part.args, short)
            }.joinToString(".")
            Ty.Star -> "?"
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
            'Z' -> "boolean"
            'V' -> "void"
            else -> error("Unknown primitive code: $code")
        }

    private fun renderArguments(args: List<Ty>?, short: Boolean): String =
        args?.joinToString(prefix = "<", postfix = ">", separator = ", ") {
            renderType(it, short)
        } ?: ""

    private fun renderTypeParams(params: List<TypeParam>, short: Boolean): String =
        if (params.isEmpty()) {
            ""
        } else {
            params.joinToString(prefix = "<", postfix = ">", separator = ", ") { param ->
                val bounds = param.bounds()
                if (bounds.isEmpty()) {
                    param.name
                } else {
                    "${param.name} extends " +
                        bounds.joinToString(separator = " & ") { renderType(it, short) }
                }
            }
        }

    private fun renderMethod(sig: Sig.Method, short: Boolean, name: String?): String {
        val typeParams = renderTypeParams(sig.tparams, short)
        val prefix = if (typeParams.isEmpty()) "" else "$typeParams "
        val methodName = name?.let { " $it" } ?: ""
        val parameters = sig.params.joinToString(separator = ", ") { renderType(it, short) }
        val thrown = if (sig.throws.isEmpty()) {
            ""
        } else {
            " throws " + sig.throws.joinToString(separator = ", ") { renderType(it, short) }
        }
        return "$prefix${renderType(sig.ret, short)}$methodName($parameters)$thrown"
    }

    private fun renderClass(sig: Sig.ClassSig, short: Boolean): String {
        val typeParams = renderTypeParams(sig.tparams, short)
        val prefix = if (typeParams.isEmpty()) "" else "$typeParams "
        val interfaces = if (sig.ifaces.isEmpty()) {
            ""
        } else {
            " implements " + sig.ifaces.joinToString(separator = ", ") { renderType(it, short) }
        }
        return "${prefix}extends ${renderType(sig.sup, short)}$interfaces"
    }
}
