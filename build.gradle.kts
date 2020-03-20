import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "io.gihub.alelk"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

plugins {
    kotlin("jvm") version "1.3.70"
}

java.sourceCompatibility = JavaVersion.VERSION_13

dependencies {
    /* Fake data generator */
    implementation("com.github.javafaker:javafaker:1.0.2")

    /* Reactive Streams */
    implementation("io.reactivex.rxjava2:rxkotlin:2.4.0")

    /* Kotlin */
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "13"
    targetCompatibility = "13"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "12"
    }
}