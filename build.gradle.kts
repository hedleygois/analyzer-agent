plugins {
    kotlin("jvm") version "2.0.10"
    kotlin("plugin.serialization") version "2.0.10"
    id("application")
}

repositories { mavenCentral() }

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("com.sun.mail:jakarta.mail:2.0.1")

    implementation("com.github.ajalt.clikt:clikt:4.4.0")

    implementation("com.aallam.openai:openai-client:4.0.1")
    implementation("io.ktor:ktor-client-core:3.0.1")
    runtimeOnly("io.ktor:ktor-client-okhttp:3.0.1")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")

    testImplementation(kotlin("test"))
}

application { mainClass.set("app.MainKt") }

kotlin { jvmToolchain(17) }