package io.github.gulliby.jvmsig.base

import java.util.Properties

object Version {
    val text: String by lazy { load() }

    private fun load(): String {
        val stream = Version::class.java.getResourceAsStream("/version.properties") ?: return "unknown"
        return stream.use { input ->
            val properties = Properties()
            properties.load(input)
            properties.getProperty("version", "unknown")
        }
    }
}
