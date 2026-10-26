package io.github.gulliby.jvmsig.cmd

import io.github.gulliby.jvmsig.base.Failure
import io.github.gulliby.jvmsig.base.Options
import io.github.gulliby.jvmsig.base.Output
import io.github.gulliby.jvmsig.model.Problem
import io.github.gulliby.jvmsig.read.SigReader
import io.github.gulliby.jvmsig.slots.Slots

object SlotsCommand {
    fun run(args: List<String>, out: Output): Int {
        val options = Options.parse(args, mapOf("--static" to Options.Kind.FLAG))
        if (options.operands.isEmpty()) {
            throw Failure(2, "slots needs at least one signature")
        }

        val tables = mutableListOf<String>()
        var exitCode = 0
        for ((index, operand) in options.operands.withIndex()) {
            try {
                val parsed = SigReader.read(operand)
                val table = Slots.render(parsed.sig, "--static" in options.flags)
                tables.add(table.removeSuffix("\n"))
            } catch (problem: Problem) {
                out.errLine("ARG#${index + 1}:${problem.col}: ${problem.code} ${problem.text}")
                exitCode = 1
            }
        }

        for ((index, table) in tables.withIndex()) {
            if (index > 0) {
                out.outLine("")
            }
            table.split('\n').forEach(out::outLine)
        }
        return exitCode
    }
}
