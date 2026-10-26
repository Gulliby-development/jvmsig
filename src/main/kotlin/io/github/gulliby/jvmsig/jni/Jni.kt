package io.github.gulliby.jvmsig.jni

import io.github.gulliby.jvmsig.base.Failure

/**
 * Validates JNI class and method names and constructs their native symbol names.
 *
 * [overloadedName] accepts erased parameter descriptor text from its caller; this
 * object deliberately does not parse or erase a method signature.
 */
object Jni {
    fun validateClassName(name: String) {
        if (name.isEmpty() || hasEmptyClassPart(name) || name.any { it in ";[<>" }) {
            throw Failure(2, "invalid class name: $name")
        }
    }

    fun validateMethodName(name: String) {
        if (name.isEmpty() || name.any { it in ".;[/<>" }) {
            throw Failure(2, "invalid method name: $name")
        }
    }

    fun shortName(className: String, methodName: String): String {
        validateClassName(className)
        validateMethodName(methodName)
        return "Java_${mangleClass(className)}_${mangle(methodName)}"
    }

    /**
     * Builds an overloaded JNI symbol using caller-supplied erased parameter
     * descriptors only (without parentheses or a return descriptor).
     */
    fun overloadedName(className: String, methodName: String, erasedParameters: String): String {
        validateClassName(className)
        validateMethodName(methodName)
        return "Java_${mangleClass(className)}_${mangle(methodName)}__${mangle(erasedParameters)}"
    }

    private fun hasEmptyClassPart(name: String): Boolean {
        var partHasCharacters = false
        for (character in name) {
            if (character == '/' || character == '.') {
                if (!partHasCharacters) {
                    return true
                }
                partHasCharacters = false
            } else {
                partHasCharacters = true
            }
        }
        return !partHasCharacters
    }

    private fun mangleClass(name: String): String = buildString {
        for (character in name) {
            if (character == '.') {
                append('_')
            } else {
                append(mangle(character))
            }
        }
    }

    private fun mangle(text: String): String = buildString {
        for (character in text) {
            append(mangle(character))
        }
    }

    private fun mangle(character: Char): String = when (character) {
        in 'a'..'z', in 'A'..'Z', in '0'..'9' -> character.toString()
        '_' -> "_1"
        ';' -> "_2"
        '[' -> "_3"
        '/' -> "_"
        else -> "_0" + character.code.toString(16).padStart(4, '0')
    }
}
