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
}
