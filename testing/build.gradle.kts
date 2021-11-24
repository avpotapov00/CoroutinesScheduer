plugins {
    kotlin("jvm") version "1.6.0"
    id("org.jetbrains.kotlin.kapt") version "1.6.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // internal
    implementation(project(":scheduler"))

    // stdlib
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("org.jetbrains.kotlinx:atomicfu:0.16.3")

    // external
    implementation("com.google.guava:guava:31.0.1-jre")

    // testing
    testImplementation("org.openjdk.jmh:jmh-core:1.30")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.5.0")
    kaptTest("org.openjdk.jmh:jmh-generator-annprocess:1.30")
}

