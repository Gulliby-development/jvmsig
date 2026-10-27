plugins {
    kotlin("jvm") version "2.4.21"
    application
}

version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("io.github.gulliby.jvmsig.cmd.MainKt")
}

tasks.processResources {
    val projectVersion = project.version.toString()
    inputs.property("projectVersion", projectVersion)
    filesMatching("version.properties") {
        expand("projectVersion" to projectVersion)
    }
}

tasks.test {
    useJUnitPlatform()
}
