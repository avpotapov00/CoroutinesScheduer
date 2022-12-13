plugins {
    kotlin("jvm") version "1.6.0"
    id("org.jetbrains.kotlin.kapt") version "1.6.0"
    id("me.champeau.jmh") version "0.6.6"
}

group = "org.example"
version = "40.9-SNAPSHOT"

repositories {
    mavenCentral()
}


dependencies {
    // internal
    implementation(project(":scheduler"))

    // stdlib
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("org.jetbrains.kotlinx:atomicfu:0.16.3")

    // external
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("io.github.geniot:indexedtreemap:1.1")

    // testing
    testImplementation("org.openjdk.jmh:jmh-core:1.30")
    kaptTest("org.openjdk.jmh:jmh-generator-annprocess:1.30")

    testImplementation("org.jetbrains.kotlin:kotlin-test:1.6.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.7.0")

    // https://mvnrepository.com/artifact/com.fasterxml.jackson.module/jackson-module-kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.1")

}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
}

tasks.withType<Test> {
    minHeapSize = "512m"
    maxHeapSize = "1024m"
    jvmArgs = listOf("-XX:MaxPermSize=512m")
}

val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = "org.jetbrains.kotlin.number.RunnerKt"
    }
}