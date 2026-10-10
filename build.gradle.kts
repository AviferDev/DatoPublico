// Plugins raíz declarados con `apply false`: cada módulo los activa según lo que
// necesite. Los que aún no usa ningún módulo (AGP, Compose Multiplatform, Ktor)
// quedan predeclarados en el version catalog para las features siguientes.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ktor) apply false
    // Gate de calidad: detekt se aplica a cada módulo (abajo) y CPD de PMD crea un
    // único `:cpdCheck` global para detectar duplicados incluso entre módulos.
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.cpd)
}

// --- Detección de código duplicado (CPD de PMD) ---
// Token-based: detecta bloques copiados, no equivalencia semántica. El umbral de
// 100 tokens busca duplicaciones de componentes/funciones reales y evita el ruido
// de fragmentos cortos. PMD 7 ya soporta Kotlin (`language = "kotlin"`).
cpd {
    language = "kotlin"
    toolVersion = "7.7.0"
    minimumTokenCount = 100
}

// Un solo `cpdCheck` en la raíz sobre todo el Kotlin de producción del repo: así se
// detectan duplicados incluso entre módulos o features distintas. Se excluyen tests
// (su duplicación es esperada) y artefactos de build.
tasks.named<de.aaschmid.gradle.plugins.cpd.Cpd>("cpdCheck") {
    source = fileTree(rootDir) {
        include("**/src/**/*.kt")
        exclude(
            "**/build/**",
            "**/test/**",
            "**/commonTest/**",
            "**/androidTest/**",
            "**/iosTest/**",
            "**/wasmJsTest/**",
            "**/jvmTest/**",
        )
    }
}

// --- Análisis estático (detekt) ---
// Se aplica a cada módulo y apunta a las fuentes de producción. El config vive en
// `config/detekt/detekt.yml` (ajustado a las convenciones del proyecto) y el
// baseline en `config/detekt/<módulo>-baseline.xml` (deuda conocida; cualquier
// hallazgo nuevo rompe el build).
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        basePath = rootProject.projectDir.absolutePath
        baseline = rootProject.file("config/detekt/${project.name}-baseline.xml")
        source.setFrom(layout.projectDirectory.dir("src/main/kotlin"))
        if (path == ":shared" || path == ":composeApp") {
            source.setFrom(
                layout.projectDirectory.dir("src/commonMain/kotlin"),
                layout.projectDirectory.dir("src/androidMain/kotlin"),
                layout.projectDirectory.dir("src/iosMain/kotlin"),
                layout.projectDirectory.dir("src/wasmJsMain/kotlin"),
            )
        }
    }

    // `./gradlew detekt` (y `check`) ejecutan también el CPD global para que un
    // duplicado introducido en cualquier feature haga fallar el build.
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        dependsOn(rootProject.tasks.named("cpdCheck"))
    }
}

// KMP no genera una tarea `test` a nivel de proyecto: `./gradlew test` desde la
// raíz solo encontraría `:backend:test` y dejaría sin ejecutar los tests de
// `:shared` y `:composeApp`. Esta tarea raíz agrega los tests de los módulos
// incluidos (`:composeApp` solo cuando no se pasa `-PskipClient`).
tasks.register("test") {
    group = "verification"
    description = "Ejecuta los tests de :shared, :backend y (si se incluye) :composeApp."
    dependsOn(":backend:test", ":shared:jvmTest")
    if (!providers.gradleProperty("skipClient").isPresent) {
        dependsOn(":composeApp:jvmTest")
    }
}
