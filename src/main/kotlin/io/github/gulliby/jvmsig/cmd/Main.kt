package io.github.gulliby.jvmsig.cmd

import io.github.gulliby.jvmsig.base.Output
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val out = Output()
    val stdin = lazy { System.`in`.readBytes() }
    val exitCode = Cli.run(args.toList(), stdin, out)

    System.out.write(out.stdout)
    System.out.flush()
    System.err.write(out.stderr)
    System.err.flush()
    exitProcess(exitCode)
}
