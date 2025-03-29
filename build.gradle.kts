plugins {
    val kotlinVersion = "2.1.10"
    kotlin("jvm") version kotlinVersion apply false
    kotlin("kapt") version kotlinVersion apply false
    kotlin("plugin.serialization") version "1.8.0" apply false
    id("com.gradleup.shadow") version "8.3.6" apply false
}
