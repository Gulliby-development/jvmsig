package io.github.gulliby.jvmsig.cmd

import io.github.gulliby.jvmsig.base.Failure
import io.github.gulliby.jvmsig.base.Options
import io.github.gulliby.jvmsig.base.Output
import io.github.gulliby.jvmsig.erase.Eraser
import io.github.gulliby.jvmsig.jni.Jni
import io.github.gulliby.jvmsig.model.Problem
import io.github.gulliby.jvmsig.model.Sig
import io.github.gulliby.jvmsig.read.SigReader

object JniCommand {
    fun run(args: List<String>, out: Output): Int {
        val options = Options.parse(args, emptyMap())
        if (options.operands.size !in 2..3) {
            throw Failure(2, "jni needs CLASS METHOD [DESCRIPTOR]")
        }

        val className = options.operands[0]
        val methodName = options.operands[1]
        Jni.validateClassName(className)
        Jni.validateMethodName(methodName)

        if (options.operands.size == 2) {
            out.outLine(Jni.shortName(className, methodName))
            return 0
        }

        val parsed = try {
            SigReader.read(options.operands[2])
        } catch (problem: Problem) {
            out.errLine("ARG#3:${problem.col}: ${problem.code} ${problem.text}")
            return 1
        }
        val method = parsed.sig as? Sig.Method
        if (method == null) {
            val problem = Problem.methodRequired()
            out.errLine("ARG#3:${problem.col}: ${problem.code} ${problem.text}")
            return 1
        }

        val erasedParameters = Eraser.eraseMethod(method).params.joinToString("") { parameter ->
            Eraser.erase(Sig.Field(parameter))
        }
        out.outLine(Jni.overloadedName(className, methodName, erasedParameters))
        return 0
    }
}
