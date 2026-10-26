package io.github.gulliby.jvmsig.model

sealed interface Ty {
    data class Prim(val code: Char) : Ty

    data class Arr(val elem: Ty) : Ty

    data class Var(val name: String) : Ty

    data class Bound(val extends: Boolean, val bound: Ty) : Ty

    data class Cls(val parts: List<Part>) : Ty

    data object Star : Ty

    data class Part(val name: String, val args: List<Ty>?)
}
