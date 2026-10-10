package es.aviferdev.datopublico.backend.ingesta.job

import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests de gate de [IngestaConfig] (sin red ni base de datos): defaults,
 * overrides y validación fail-fast del entorno `INGESTA_*`.
 */
class IngestaConfigTest {

    @Test
    fun `usa los valores por defecto sin entorno`() {
        val config = IngestaConfig.fromEnv(emptyMap())

        assertTrue(config.enabled)
        assertEquals(ZoneId.of(IngestaConfig.DEFAULT_TIMEZONE), config.zone)
        assertEquals(LocalTime.of(9, 30), config.primaryTime)
        assertEquals(LocalTime.of(18, 0), config.secondaryTime)
        assertEquals(listOf(LocalTime.of(9, 30), LocalTime.of(18, 0)), config.times)
    }

    @Test
    fun `lee los overrides del entorno`() {
        val config = IngestaConfig.fromEnv(
            mapOf(
                "INGESTA_ENABLED" to "false",
                "INGESTA_TIMEZONE" to "Atlantic/Canary",
                "INGESTA_PRIMARY_TIME" to "08:00",
                "INGESTA_SECONDARY_TIME" to "20:15",
            )
        )

        assertFalse(config.enabled)
        assertEquals(ZoneId.of("Atlantic/Canary"), config.zone)
        assertEquals(LocalTime.of(8, 0), config.primaryTime)
        assertEquals(LocalTime.of(20, 15), config.secondaryTime)
    }

    @Test
    fun `acepta el booleano en mayusculas y espacios en blanco`() {
        assertEquals(true, IngestaConfig.fromEnv(mapOf("INGESTA_ENABLED" to "TRUE")).enabled)
        assertEquals(false, IngestaConfig.fromEnv(mapOf("INGESTA_ENABLED" to " false ")).enabled)
    }

    @Test
    fun `ignora valores en blanco y usa los defaults`() {
        val config = IngestaConfig.fromEnv(
            mapOf(
                "INGESTA_TIMEZONE" to "  ",
                "INGESTA_PRIMARY_TIME" to "",
                "INGESTA_SECONDARY_TIME" to " ",
            )
        )

        assertEquals(ZoneId.of(IngestaConfig.DEFAULT_TIMEZONE), config.zone)
        assertEquals(LocalTime.of(9, 30), config.primaryTime)
        assertEquals(LocalTime.of(18, 0), config.secondaryTime)
    }

    @Test
    fun `falla con un horario invalido`() {
        assertFailsWith<IllegalArgumentException> {
            IngestaConfig.fromEnv(mapOf("INGESTA_PRIMARY_TIME" to "no-es-hora"))
        }
        assertFailsWith<IllegalArgumentException> {
            IngestaConfig.fromEnv(mapOf("INGESTA_SECONDARY_TIME" to "25:00"))
        }
    }

    @Test
    fun `falla con una zona horaria invalida`() {
        val error = assertFailsWith<IllegalArgumentException> {
            IngestaConfig.fromEnv(mapOf("INGESTA_TIMEZONE" to "Marte/Olympus"))
        }

        assertTrue(error.message!!.contains("INGESTA_TIMEZONE"), error.message!!)
    }

    @Test
    fun `falla con un booleano invalido`() {
        assertFailsWith<IllegalArgumentException> {
            IngestaConfig.fromEnv(mapOf("INGESTA_ENABLED" to "quizas"))
        }
    }
}
