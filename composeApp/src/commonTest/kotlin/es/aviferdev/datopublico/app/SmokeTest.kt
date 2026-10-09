package es.aviferdev.datopublico.app

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Smoke test del módulo `:composeApp`. En FT00001 no hay todavía UI Compose: solo
 * comprueba que el módulo KMP con target JVM compila y ejecuta tests.
 */
class SmokeTest {
    @Test
    fun moduleIsWiredForTests() {
        assertEquals(4, 2 + 2)
    }
}
