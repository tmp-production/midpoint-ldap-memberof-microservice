import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.20"
    kotlin("plugin.serialization") version "1.7.20"
    application
}

group = "com.tmp-production.ldapservice"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

//    implementation("org.apache.logging.log4j:log4j-api:2.19.0")
//    implementation("org.apache.logging.log4j:log4j-core:2.19.0")

    implementation("org.slf4j:slf4j-api:2.0.3")
    implementation("org.slf4j:slf4j-simple:2.0.3")

    implementation("io.ktor:ktor-server-core:2.1.3")
    implementation("io.ktor:ktor-server-netty:2.1.3")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
    implementation("io.ktor:ktor-server-content-negotiation:2.1.3")
//    implementation("io.ktor:ktor-gson:2.1.3")
//    implementation("io.ktor:ktor-gson:1.6.8")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.1.3")
//    implementation("io.ktor:ktor-serialization-kotlinx-xml:2.1.3")

    // ktor-client for making requests
    implementation("io.ktor:ktor-client-core:2.1.3")
    implementation("io.ktor:ktor-client-cio:2.1.3")
    implementation("io.ktor:ktor-client-logging:2.1.3")

    implementation("org.apache.kafka:kafka-streams:3.3.1")

    testImplementation("io.ktor:ktor-server-tests-jvm:2.1.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.7.20")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClass.set("MainKt")
}