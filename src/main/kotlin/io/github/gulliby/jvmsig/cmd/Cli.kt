package io.github.gulliby.jvmsig.cmd

import io.github.gulliby.jvmsig.base.Failure
import io.github.gulliby.jvmsig.base.Output
import io.github.gulliby.jvmsig.base.Version

object Cli {
    fun run(args: List<String>, stdin: Lazy<ByteArray>, out: Output): Int {
        if (args.isEmpty()) {
            writeUsage(out::errLine)
            return 2
        }
        if (args[0] == "--help" || args[0] == "-h") {
            writeUsage(out::outLine)
            return 0
        }
        if (args[0] == "--version" || args[0] == "-V") {
            out.outLine("jvmsig ${Version.text}")
            return 0
        }

        val command = args[0]
        val commandArgs = args.drop(1)
        return try {
            when (command) {
                "show" -> ShowCommand.run(commandArgs, out)
                "check" -> CheckCommand.run(commandArgs, stdin, out)
                "erase" -> EraseCommand.run(commandArgs, out)
                "slots" -> SlotsCommand.run(commandArgs, out)
                "jni" -> JniCommand.run(commandArgs, out)
                else -> throw Failure(2, "unknown command $command")
            }
        } catch (failure: Failure) {
            out.errLine("jvmsig: ${failure.message.orEmpty()}")
            failure.exitCode
        }
    }

    private fun writeUsage(writeLine: (String) -> Unit) {
        USAGE.lines().forEach(writeLine)
    }

    private const val USAGE = "usage: jvmsig show [--kotlin] [--short] [--name NAME] SIGNATURE...\n" +
        "       jvmsig check [--var NAME]... FILE...\n" +
        "       jvmsig erase SIGNATURE...\n" +
        "       jvmsig slots [--static] SIGNATURE...\n" +
        "       jvmsig jni CLASS METHOD [DESCRIPTOR]\n" +
        "       jvmsig --version"
}
