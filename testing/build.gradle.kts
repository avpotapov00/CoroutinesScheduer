plugins {
    kotlin("jvm") version "1.9.10"
    kotlin("kapt") version "1.9.10"
    id("me.champeau.jmh") version "0.6.6"
}

group = "org.example"
version = "86.0-SNAPSHOT"

repositories {
    mavenCentral()
}


dependencies {
    // internal
    implementation(project(":scheduler"))

    // stdlib
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
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

    // https://mvnrepository.com/artifact/org.kynosarges/tektosyne
    implementation("org.kynosarges:tektosyne:6.2.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

tasks.withType<Test> {
    maxHeapSize = "4096m"
    minHeapSize = "4096m"
    jvmArgs = listOf("-XX:MaxPermSize=4096m")
}


val jar by tasks.getting(Jar::class) {
    manifest {
        attributes["Main-Class"] = "org.jetbrains.kotlin.number.RunnerKt"
    }
}