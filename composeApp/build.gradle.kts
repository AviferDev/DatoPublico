import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    // Serialización JSON del cliente HTTP (FT00035): los DTOs de wire del cliente
    // son `@Serializable` y Ktor los decodifica con `kotlinx.serialization`.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.application)
}

// --- URL base de la API de la Web (FT00039-PT1) ---
// La propiedad `-PapiBaseUrl=<url>` inyecta la URL de la API de **producción** en
// la build de la Web (Wasm); sin ella se conserva el valor de desarrollo. La
// constante se genera **solo** para `wasmJsMain`, así que Android, iOS y JVM no
// cambian su URL base. El artefacto resultante es el que publica FT00052-PT2
// (`composeApp/build/dist/wasmJs/productionExecutable/`).
val defaultApiBaseUrl = "http://localhost:8080"

/** URL base de la API ya normalizada (sin barra final y nunca vacía). */
val apiBaseUrl: String = providers.gradleProperty("apiBaseUrl")
    .getOrElse(defaultApiBaseUrl)
    .trim()
    .trimEnd('/')
    .ifBlank {
        throw GradleException(
            "La propiedad `apiBaseUrl` está vacía: define la URL base de la API " +
                "(p. ej. -PapiBaseUrl=https://datopublico.example).",
        )
    }

/** Directorio de las fuentes generadas de la configuración de la Web. */
val apiConfigDir: Provider<Directory> =
    layout.buildDirectory.dir("generated/apiConfig/wasmJsMain")

/** Tarea que escribe la constante `API_BASE_URL` de `wasmJsMain`. */
val generateApiBaseUrlConfig = tasks.register("generateApiBaseUrlConfig") {
    group = "build"
    description = "Genera la constante con la URL base de la API para la Web (FT00039-PT1)."
    inputs.property("apiBaseUrl", apiBaseUrl)
    outputs.dir(apiConfigDir)
    doLast {
        val target = apiConfigDir.get()
            .file("es/aviferdev/datopublico/core/network/ApiBaseUrlBuildConfig.kt")
            .asFile
        target.parentFile.mkdirs()
        target.writeText(
            """
            |// Fichero **generado** por `generateApiBaseUrlConfig` (FT00039-PT1):
            |// no se edita a mano ni se versiona.
            |package es.aviferdev.datopublico.core.network
            |
            |/**
            | * URL base de la API inyectada en la build de la Web (Wasm) con la
            | * propiedad Gradle `apiBaseUrl`; sin ella, el valor de desarrollo.
            | */
            |internal const val API_BASE_URL: String = "$apiBaseUrl"
            |""".trimMargin() + "\n",
        )
    }
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
            // Cliente HTTP común (FT00035): núcleo de Ktor, negociación de
            // contenido JSON y el serializador `kotlinx.serialization`. El motor
            // lo aporta cada source set (abajo); aquí no se fija ninguno.
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(compose.preview)
            // Motor OkHttp del cliente HTTP en Android.
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            // Motor CIO para el target de tests/dev de JVM.
            implementation(libs.ktor.client.cio)
        }
        iosMain.dependencies {
            // Motor Darwin (NSURLSession) para iOS.
            implementation(libs.ktor.client.darwin)
        }
        wasmJsMain {
            // La constante con la URL base de la API (FT00039-PT1) se genera en
            // build time y se añade **solo** al source set de la Web; pasar el
            // `TaskProvider` enlaza la dependencia de la tarea de generación, así
            // que cualquier compilación de Wasm la regenera antes de compilar.
            kotlin.srcDir(generateApiBaseUrlConfig)
        }
        wasmJsMain.dependencies {
            // Motor JS (fetch/XHR) para Web/Wasm.
            implementation(libs.ktor.client.js)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // `runTest` para los tests de estado y de repositorio (FT00032), que
            // esperan funciones `suspend` sin abrir sockets ni tocar la BD.
            implementation(libs.kotlinx.coroutines.test)
            // Doble de red de los tests del cliente HTTP (FT00035): `MockEngine`
            // simula respuestas sin abrir sockets.
            implementation(libs.ktor.client.mock)
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
