package io.github.gulliby.jvmsig.cmd

import io.github.gulliby.jvmsig.base.Failure
import io.github.gulliby.jvmsig.base.Options
import io.github.gulliby.jvmsig.base.Output
import io.github.gulliby.jvmsig.model.Problem
import io.github.gulliby.jvmsig.read.SigReader
import io.github.gulliby.jvmsig.render.JavaRenderer
import io.github.gulliby.jvmsig.render.KotlinRenderer

object ShowCommand {
    fun run(args: List<String>, out: Output): Int {
        val options = Options.parse(
            args,
            mapOf(
                "--kotlin" to Options.Kind.FLAG,
                "--short" to Options.Kind.FLAG,
                "--name" to Options.Kind.VALUE
            )
        )
        if (options.operands.isEmpty()) {
            throw Failure(2, "show needs at least one signature")
        }

        val short = "--short" in options.flags
        val name = options.values["--name"]
        val kotlin = "--kotlin" in options.flags
        var failed = false

        options.operands.forEachIndexed { index, operand ->
            try {
                val parsed = SigReader.read(operand)
                val rendered = if (kotlin) {
                    KotlinRenderer.render(parsed.sig, short, name)
                } else {
                    JavaRenderer.render(parsed.sig, short, name)
                }
                out.outLine(rendered)
            } catch (problem: Problem) {
                out.errLine("ARG#${index + 1}:${problem.col}: ${problem.code} ${problem.text}")
                failed = true
            }
        }

        return if (failed) 1 else 0
    }
}
