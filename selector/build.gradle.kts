import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(rootProject.ext["kotlin.jvmTarget"] as JvmTarget)
        }
    }
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
            "-Xcontext-parameters",
            "-Xexplicit-backing-fields",
            "-XXLanguage:+MultiDollarInterpolation",
        )
    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.json5)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlin.test)
            }
        }
    }
}
