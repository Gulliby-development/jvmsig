package io.github.gulliby.jvmsig.render

object Names {
    fun className(name: String, short: Boolean = false): String =
        if (short) name.substringAfterLast('/') else name.replace('/', '.')
}
