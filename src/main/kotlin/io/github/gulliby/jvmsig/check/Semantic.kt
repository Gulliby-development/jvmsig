package io.github.gulliby.jvmsig.check

import io.github.gulliby.jvmsig.model.Parsed
import io.github.gulliby.jvmsig.model.Problem
import io.github.gulliby.jvmsig.model.Ty
import io.github.gulliby.jvmsig.model.TypeParam

object Semantic {
    fun validate(parsed: Parsed, externalNames: Set<String> = emptySet()): Problem? {
        val firstDeclarations = linkedMapOf<String, TypeParam>()
        val candidates = mutableListOf<Problem>()

        for (parameter in parsed.tparams) {
            if (firstDeclarations.putIfAbsent(parameter.name, parameter) != null) {
                candidates += Problem.duplicateParameter(parameter.col, parameter.name)
            }
        }

        val declaredNames = firstDeclarations.keys
        for ((name, col) in parsed.uses) {
            if (name !in declaredNames && name !in externalNames) {
                candidates += Problem.undeclaredVariable(col, name)
            }
        }

        for (parameter in parsed.tparams) {
            if (hasCycleReturningTo(parameter, firstDeclarations)) {
                candidates += Problem.cyclicBound(parameter.col, parameter.name)
            }
        }

        return candidates.minByOrNull { it.col }
    }

    private fun hasCycleReturningTo(
        start: TypeParam,
        firstDeclarations: Map<String, TypeParam>
    ): Boolean {
        val visited = mutableSetOf(start.name)
        var current = start

        while (true) {
            val nextName = (current.firstBound() as? Ty.Var)?.name ?: return false
            if (nextName == start.name) {
                return true
            }
            if (!visited.add(nextName)) {
                return false
            }
            current = firstDeclarations[nextName] ?: return false
        }
    }
}
