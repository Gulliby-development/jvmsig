package io.github.gulliby.jvmsig.model

sealed interface Sig {
    data class Field(val type: Ty) : Sig

    data class Method(
        val tparams: List<TypeParam>,
        val params: List<Ty>,
        val ret: Ty,
        val throws: List<Ty>
    ) : Sig

    data class ClassSig(
        val tparams: List<TypeParam>,
        val sup: Ty.Cls,
        val ifaces: List<Ty.Cls>
    ) : Sig
}
