plugins {
    kotlin("jvm") version "2.4.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("io.github.gulliby.jvmsig.cmd.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
