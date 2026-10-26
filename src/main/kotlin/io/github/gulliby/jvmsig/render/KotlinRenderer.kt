package io.github.gulliby.jvmsig.render

import io.github.gulliby.jvmsig.model.Sig
import io.github.gulliby.jvmsig.model.Ty
import io.github.gulliby.jvmsig.model.TypeParam

object KotlinRenderer {
    fun render(type: Ty, short: Boolean = false): String =
        when (type) {
            is Ty.Prim -> primitive(type.code)
            is Ty.Arr -> primitiveArray(type.elem) ?: "Array<${render(type.elem, short)}>"
            is Ty.Var -> type.name
            Ty.Star -> "*"
            is Ty.Bound -> "${if (type.extends) "out" else "in"} ${render(type.bound, short)}"
            is Ty.Cls -> renderClass(type, short)
        }

    fun render(sig: Sig, short: Boolean = false, name: String? = null): String =
        when (sig) {
            is Sig.Field -> render(sig.type, short)
            is Sig.Method -> renderMethod(sig, short, name)
            is Sig.ClassSig -> renderClassSignature(sig, short)
        }

    private fun primitive(code: Char): String =
        when (code) {
            'B' -> "Byte"
            'C' -> "Char"
            'D' -> "Double"
            'F' -> "Float"
            'I' -> "Int"
            'J' -> "Long"
            'S' -> "Short"
            'Z' -> "Boolean"
            'V' -> "Unit"
            else -> error("Unsupported primitive code: $code")
        }

    private fun primitiveArray(elem: Ty): String? =
        when (elem) {
            is Ty.Prim -> when (elem.code) {
                'B' -> "ByteArray"
                'C' -> "CharArray"
                'D' -> "DoubleArray"
                'F' -> "FloatArray"
                'I' -> "IntArray"
                'J' -> "LongArray"
                'S' -> "ShortArray"
                'Z' -> "BooleanArray"
                else -> null
            }
            else -> null
        }

    private fun renderClass(type: Ty.Cls, short: Boolean): String =
        type.parts.mapIndexed { index, part ->
            val name = if (index == 0) Names.className(part.name, short) else part.name
            val arguments = part.args?.joinToString(", ") { render(it, short) }
            if (arguments == null) name else "$name<$arguments>"
        }.joinToString(".")

    private fun renderTypeParams(typeParams: List<TypeParam>, short: Boolean): String =
        if (typeParams.isEmpty()) {
            ""
        } else {
            typeParams.joinToString(", ", "<", ">") { typeParam ->
                val bounds = typeParam.bounds()
                if (bounds.isEmpty()) {
                    typeParam.name
                } else {
                    "${typeParam.name} : ${bounds.joinToString(" & ") { render(it, short) }}"
                }
            }
        }

    private fun renderMethod(sig: Sig.Method, short: Boolean, name: String?): String {
        val typeParams = renderTypeParams(sig.tparams, short)
        val params = sig.params.joinToString(", ") { render(it, short) }
        val returnType = render(sig.ret, short)

        return if (name == null) {
            val prefix = if (typeParams.isEmpty()) "" else "$typeParams "
            "$prefix($params) -> $returnType"
        } else {
            val prefix = if (typeParams.isEmpty()) "" else "$typeParams "
            "fun $prefix$name($params): $returnType"
        }
    }

    private fun renderClassSignature(sig: Sig.ClassSig, short: Boolean): String {
        val typeParams = renderTypeParams(sig.tparams, short)
        val prefix = if (typeParams.isEmpty()) "" else "$typeParams "
        val parents = (listOf(sig.sup) + sig.ifaces).joinToString(", ") { render(it, short) }
        return "$prefix: $parents"
    }
}
