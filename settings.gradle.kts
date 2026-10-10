pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // `PREFER_SETTINGS` (en vez de `FAIL_ON_PROJECT_REPOS`): el plugin de
    // Kotlin/Wasm registra de forma programática repositorios Ivy para las
    // distribuciones de herramientas (Binaryen, Node, Yarn), lo que rompería el
    // build en modo `FAIL_ON_PROJECT_REPOS`. Con `PREFER_SETTINGS` el settings
    // sigue siendo la fuente de verdad: los repos del proyecto se ignoran y las
    // distribuciones se declaran aquí con el layout Ivy exacto del plugin.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        // Binaryen (wasm-opt) para la optimización Wasm de producción.
        // Layout Ivy del plugin (BinaryenSetupTask).
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "Binaryen distributions"
            patternLayout {
                artifact("version_[revision]/binaryen-version_[revision]-[classifier].[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
        // Node.js (lo descarga el plugin de Kotlin/JS-Wasm). Layout Ivy del plugin
        // (NodeJsSetupTask).
        ivy("https://nodejs.org/dist") {
            name = "Node.js distributions"
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        // Yarn (lo descarga el plugin de Kotlin/JS-Wasm). Layout Ivy del plugin
        // (YarnSetupTask).
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn distributions"
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]).[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
    }
}

rootProject.name = "datopublico"

// `:shared` y `:backend` son SDK-free. `:composeApp` aplica AGP (exige el Android
// SDK) y se incluye salvo con `-PskipClient`, que deja el gate estándar sin SDK
// (lo usan `init.sh` por defecto y, en el futuro, la imagen del backend).
include(":shared", ":backend")

if (!providers.gradleProperty("skipClient").isPresent) {
    include(":composeApp")
}
