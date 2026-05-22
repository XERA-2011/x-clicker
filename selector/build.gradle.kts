plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}
