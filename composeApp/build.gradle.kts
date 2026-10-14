import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
}

kotlin {
    jvmToolchain(21)

    // Cliente de producto: Android, iOS y Web (Wasm). `jvm()` se conserva solo
    // como target de tests/dev (mantiene `:composeApp:jvmTest` y la task raíz
    // `test`); no se distribuye app de escritorio.
    androidTarget()
    jvm()

    iosArm64 {
        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            // Recursos i18n (FT00032): los textos visibles viven en
            // `composeResources/values/strings.xml` y se leen con
            // `stringResource(Res.string.*)`.
            implementation(compose.components.resources)
            // Navegación y estado (FT00034): `StateFlow` del ViewModel, su
            // recolección con `collectAsStateWithLifecycle()` y el grafo
            // `NavHost`. Familia `org.jetbrains.androidx.*`, la oficial de
            // Compose Multiplatform.
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation.compose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(compose.preview)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // `runTest` para los tests de estado y de repositorio (FT00032), que
            // esperan funciones `suspend` sin abrir sockets ni tocar la BD.
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Paquete explícito de la clase `Res` generada por Compose Resources: así los
// composables importan `es.aviferdev.datopublico.resources.Res` en vez del
// paquete por defecto derivado del módulo.
compose.resources {
    packageOfResClass = "es.aviferdev.datopublico.resources"
}

android {
    namespace = "es.aviferdev.datopublico"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "es.aviferdev.datopublico"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

// Los tests en navegador de Wasm (Karma/Chrome) quedan fuera del gate: el
// `SmokeTest` ya corre en `jvmTest`, en los tests unitarios de Android y en
// `iosSimulatorArm64Test`, y el código Wasm se compila con
// `wasmJsBrowserDistribution`. Sin este ajuste, `./gradlew build` exigiría un
// Chrome instalado en la máquina (no está en todos los entornos locales).
tasks.matching { it.name == "wasmJsBrowserTest" }.configureEach { enabled = false }
