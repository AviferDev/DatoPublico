package es.aviferdev.datopublico.backend

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Smoke test del módulo `:backend`. En FT00001 no hay aún servidor Ktor: verifica
 * que el módulo JVM compila, resuelve `:shared` y ejecuta tests.
 */
class SmokeTest {
    @Test
    fun moduleIsWiredForTests() {
        assertEquals(4, 2 + 2)
    }
}
