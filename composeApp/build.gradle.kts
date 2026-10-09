plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(21)

    // Solo target JVM en FT00001. El plugin Compose y los targets Android/iOS/
    // Wasm llegan con FT00005.
    jvm()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
