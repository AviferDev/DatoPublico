package es.aviferdev.datopublico

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Smoke test del módulo `:shared`. En FT00001 no hay todavía DTOs ni lógica: solo
 * comprueba que el módulo KMP con target JVM compila y ejecuta tests.
 */
class SmokeTest {
    @Test
    fun moduleIsWiredForTests() {
        assertEquals(4, 2 + 2)
    }
}
