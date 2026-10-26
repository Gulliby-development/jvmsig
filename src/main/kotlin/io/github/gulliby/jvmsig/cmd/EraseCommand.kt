package io.github.gulliby.jvmsig.cmd

import io.github.gulliby.jvmsig.base.Failure
import io.github.gulliby.jvmsig.base.Options
import io.github.gulliby.jvmsig.base.Output
import io.github.gulliby.jvmsig.erase.Eraser
import io.github.gulliby.jvmsig.model.Problem
import io.github.gulliby.jvmsig.read.SigReader

object EraseCommand {
    fun run(args: List<String>, out: Output): Int {
        val options = Options.parse(args, emptyMap())
        if (options.operands.isEmpty()) {
            throw Failure(2, "erase needs at least one signature")
        }

        var failed = false
        options.operands.forEachIndexed { index, operand ->
            try {
                val parsed = SigReader.read(operand)
                out.outLine(Eraser.erase(parsed.sig))
            } catch (problem: Problem) {
                out.errLine("ARG#${index + 1}:${problem.col}: ${problem.code} ${problem.text}")
                failed = true
            }
        }

        return if (failed) 1 else 0
    }
}
