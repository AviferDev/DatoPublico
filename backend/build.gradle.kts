plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

// `:backend` depende de `:shared` desde el arranque (frontera de arquitectura).
// El plugin `application` y Ktor llegan con FT00004.
dependencies {
    implementation(project(":shared"))

    testImplementation(kotlin("test"))
}
