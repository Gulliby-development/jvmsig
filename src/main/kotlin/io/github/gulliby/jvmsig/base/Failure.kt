package io.github.gulliby.jvmsig.base

class Failure(val exitCode: Int, message: String) : Exception(message)
