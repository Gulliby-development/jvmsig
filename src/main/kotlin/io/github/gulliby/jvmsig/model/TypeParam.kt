package io.github.gulliby.jvmsig.model

data class TypeParam(
    val name: String,
    val col: Int,
    val classBound: Ty?,
    val interfaceBounds: List<Ty>
) {
    fun firstBound(): Ty? = classBound ?: interfaceBounds.firstOrNull()

    fun bounds(): List<Ty> = classBound?.let { listOf(it) + interfaceBounds } ?: interfaceBounds
}
