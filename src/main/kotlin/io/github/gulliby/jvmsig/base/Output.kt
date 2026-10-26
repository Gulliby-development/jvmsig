package io.github.gulliby.jvmsig.base

import java.io.ByteArrayOutputStream

class Output {
    private val stdoutBuffer = ByteArrayOutputStream()
    private val stderrBuffer = ByteArrayOutputStream()

    val stdout: ByteArray
        get() = stdoutBuffer.toByteArray()

    val stderr: ByteArray
        get() = stderrBuffer.toByteArray()

    fun outLine(text: String) {
        appendLine(stdoutBuffer, text)
    }

    fun errLine(text: String) {
        appendLine(stderrBuffer, text)
    }

    private fun appendLine(buffer: ByteArrayOutputStream, text: String) {
        buffer.write(text.toByteArray(Charsets.UTF_8))
        buffer.write(0x0A)
    }
}
