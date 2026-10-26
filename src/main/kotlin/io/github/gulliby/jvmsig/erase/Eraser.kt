package io.github.gulliby.jvmsig.erase

import io.github.gulliby.jvmsig.model.Sig
import io.github.gulliby.jvmsig.model.Ty
import io.github.gulliby.jvmsig.model.TypeParam

object Eraser {
    private val objectType = Ty.Cls(listOf(Ty.Part("java/lang/Object", null)))

    fun erase(sig: Sig): String =
        when (sig) {
            is Sig.Field -> descriptor(eraseType(sig.type))
            is Sig.Method -> {
                val erased = eraseMethod(sig)
                erased.params.joinToString(separator = "", prefix = "(", postfix = ")") { descriptor(it) } +
                    descriptor(erased.ret)
            }
            is Sig.ClassSig -> {
                (listOf(sig.sup) + sig.ifaces)
                    .joinToString(" ") { descriptor(eraseType(it, sig.tparams)) }
            }
        }

    fun eraseType(type: Ty, tparams: List<TypeParam> = emptyList()): Ty =
        eraseType(type, declarations(tparams), mutableSetOf())

    fun eraseMethod(method: Sig.Method): Sig.Method =
        Sig.Method(
            tparams = emptyList(),
            params = method.params.map { eraseType(it, method.tparams) },
            ret = eraseType(method.ret, method.tparams),
            throws = emptyList()
        )

    private fun declarations(tparams: List<TypeParam>): Map<String, TypeParam> =
        buildMap {
            for (tparam in tparams) {
                if (tparam.name !in this) {
                    put(tparam.name, tparam)
                }
            }
        }

    private fun eraseType(
        type: Ty,
        declarations: Map<String, TypeParam>,
        activeVariables: MutableSet<String>
    ): Ty =
        when (type) {
            is Ty.Prim -> type
            is Ty.Arr -> Ty.Arr(eraseType(type.elem, declarations, activeVariables))
            is Ty.Cls -> {
                val binaryName = type.parts.joinToString("$") { it.name }
                Ty.Cls(listOf(Ty.Part(binaryName, null)))
            }
            is Ty.Var -> eraseVariable(type.name, declarations, activeVariables)
            is Ty.Bound -> eraseType(type.bound, declarations, activeVariables)
            Ty.Star -> objectType
        }

    private fun eraseVariable(
        name: String,
        declarations: Map<String, TypeParam>,
        activeVariables: MutableSet<String>
    ): Ty {
        if (!activeVariables.add(name)) {
            return objectType
        }

        return try {
            declarations[name]
                ?.firstBound()
                ?.let { eraseType(it, declarations, activeVariables) }
                ?: objectType
        } finally {
            activeVariables.remove(name)
        }
    }

    private fun descriptor(type: Ty): String =
        when (type) {
            is Ty.Prim -> type.code.toString()
            is Ty.Arr -> "[${descriptor(type.elem)}"
            is Ty.Cls -> "L${type.parts.joinToString("$") { it.name }};"
            is Ty.Var -> descriptor(objectType)
            is Ty.Bound -> descriptor(type.bound)
            Ty.Star -> descriptor(objectType)
        }
}
