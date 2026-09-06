plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

application {
    mainClass.set("com.ansen.cms.server.ApplicationKt")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation("io.ktor:ktor-server-core:3.5.1")
    implementation("io.ktor:ktor-server-netty:3.5.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.5.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.1")
    implementation("io.ktor:ktor-server-status-pages:3.5.1")
    implementation("io.ktor:ktor-server-default-headers:3.5.1")
    implementation("software.amazon.awssdk:s3:2.31.42")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:3.5.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
