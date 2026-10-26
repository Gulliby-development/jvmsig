package io.github.gulliby.jvmsig.cmd

import io.github.gulliby.jvmsig.base.Failure
import io.github.gulliby.jvmsig.base.Inputs
import io.github.gulliby.jvmsig.base.Options
import io.github.gulliby.jvmsig.base.Output
import io.github.gulliby.jvmsig.base.Text
import io.github.gulliby.jvmsig.check.Semantic
import io.github.gulliby.jvmsig.model.Problem
import io.github.gulliby.jvmsig.read.SigReader

object CheckCommand {
    fun run(args: List<String>, stdin: Lazy<ByteArray>, out: Output): Int {
        val options = Options.parse(args, mapOf("--var" to Options.Kind.MULTI))
        if (options.operands.isEmpty()) {
            throw Failure(2, "check needs at least one file")
        }

        val externalNames = options.multi["--var"].orEmpty().toSet()
        for (name in externalNames) {
            if (name.codePoints().toArray().any { Text.has(FORBIDDEN_VARIABLE_CHARACTERS, it) }) {
                throw Failure(2, "invalid --var value: $name")
            }
        }

        var signatureCount = 0
        var problemCount = 0
        for (file in options.operands) {
            val label = if (file == "-") "<stdin>" else file
            val bytes = if (file == "-") stdin.value else Inputs.read(file)
            val text = Inputs.decode(bytes, label).removePrefix("\uFEFF")

            for ((index, line) in signatureLines(text).withIndex()) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue
                }

                signatureCount++
                val problem = try {
                    Semantic.validate(SigReader.read(line), externalNames)
                } catch (syntaxProblem: Problem) {
                    syntaxProblem
                }
                if (problem != null) {
                    out.outLine("$label:${index + 1}:${problem.col}: ${problem.code} ${problem.text}")
                    problemCount++
                }
            }
        }

        out.outLine(
            "checked $signatureCount ${Text.plural(signatureCount, "signature")}, " +
                "$problemCount ${Text.plural(problemCount, "problem")}"
        )
        return if (problemCount == 0) 0 else 1
    }

    private fun signatureLines(text: String): List<String> {
        val lines = mutableListOf<String>()
        var start = 0
        for (index in text.indices) {
            if (text[index] == '\n') {
                var end = index
                if (end > start && text[end - 1] == '\r') {
                    end--
                }
                lines.add(text.substring(start, end))
                start = index + 1
            }
        }
        if (start < text.length) {
            lines.add(text.substring(start))
        }
        return lines
    }

    private const val FORBIDDEN_VARIABLE_CHARACTERS = ".;[/<>:"
}
