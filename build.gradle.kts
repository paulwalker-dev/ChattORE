plugins {
    val kotlinVersion = "2.1.10"
    kotlin("jvm") version kotlinVersion apply false
    id("com.gradleup.shadow") version "8.3.6" apply false
    id("org.jetbrains.kotlin.kapt") version "1.9.22" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
}
