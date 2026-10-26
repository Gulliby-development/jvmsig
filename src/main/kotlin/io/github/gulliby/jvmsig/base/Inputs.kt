package io.github.gulliby.jvmsig.base

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Paths

object Inputs {
    fun read(file: String): ByteArray {
        try {
            return Files.readAllBytes(Paths.get(file))
        } catch (_: IOException) {
            throw Failure(2, "cannot read $file")
        } catch (_: InvalidPathException) {
            throw Failure(2, "cannot read $file")
        } catch (_: SecurityException) {
            throw Failure(2, "cannot read $file")
        }
    }

    fun decode(bytes: ByteArray, name: String): String {
        if (bytes.any { it == 0.toByte() }) {
            throw Failure(2, "$name: contains NUL")
        }

        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            throw Failure(2, "$name: not valid UTF-8 text")
        }
    }
}
