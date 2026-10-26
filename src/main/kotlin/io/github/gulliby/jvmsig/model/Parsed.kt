package io.github.gulliby.jvmsig.model

data class Parsed(
    val sig: Sig,
    val tparams: List<TypeParam>,
    val uses: List<Pair<String, Int>>
)
