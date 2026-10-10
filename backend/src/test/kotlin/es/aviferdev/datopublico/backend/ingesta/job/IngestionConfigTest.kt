package es.aviferdev.datopublico.backend.ingesta.job

import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests de gate de [IngestionConfig] (sin red ni base de datos): defaults,
 * overrides y validación fail-fast del entorno `INGESTA_*`.
 */
class IngestionConfigTest {

    @Test
    fun `uses the default values without environment`() {
        val config = IngestionConfig.fromEnv(emptyMap())

        assertTrue(config.enabled)
        assertEquals(ZoneId.of(IngestionConfig.DEFAULT_TIMEZONE), config.zone)
        assertEquals(LocalTime.of(9, 30), config.primaryTime)
        assertEquals(LocalTime.of(18, 0), config.secondaryTime)
        assertEquals(Duration.ofMinutes(30), config.grace)
        assertEquals(listOf(LocalTime.of(9, 30), LocalTime.of(18, 0)), config.times)
    }

    @Test
    fun `reads the environment overrides`() {
        val config = IngestionConfig.fromEnv(
            mapOf(
                "INGESTA_ENABLED" to "false",
                "INGESTA_TIMEZONE" to "Atlantic/Canary",
                "INGESTA_PRIMARY_TIME" to "08:00",
                "INGESTA_SECONDARY_TIME" to "20:15",
                "INGESTA_GRACE_MINUTES" to "45",
            )
        )

        assertFalse(config.enabled)
        assertEquals(ZoneId.of("Atlantic/Canary"), config.zone)
        assertEquals(LocalTime.of(8, 0), config.primaryTime)
        assertEquals(LocalTime.of(20, 15), config.secondaryTime)
        assertEquals(Duration.ofMinutes(45), config.grace)
    }

    @Test
    fun `accepts the boolean in uppercase and with blank spaces`() {
        assertEquals(true, IngestionConfig.fromEnv(mapOf("INGESTA_ENABLED" to "TRUE")).enabled)
        assertEquals(false, IngestionConfig.fromEnv(mapOf("INGESTA_ENABLED" to " false ")).enabled)
    }

    @Test
    fun `ignores blank values and uses the defaults`() {
        val config = IngestionConfig.fromEnv(
            mapOf(
                "INGESTA_TIMEZONE" to "  ",
                "INGESTA_PRIMARY_TIME" to "",
                "INGESTA_SECONDARY_TIME" to " ",
                "INGESTA_GRACE_MINUTES" to "  ",
            )
        )

        assertEquals(ZoneId.of(IngestionConfig.DEFAULT_TIMEZONE), config.zone)
        assertEquals(LocalTime.of(9, 30), config.primaryTime)
        assertEquals(LocalTime.of(18, 0), config.secondaryTime)
        assertEquals(Duration.ofMinutes(30), config.grace)
    }

    @Test
    fun `fails with an invalid time`() {
        assertFailsWith<IllegalArgumentException> {
            IngestionConfig.fromEnv(mapOf("INGESTA_PRIMARY_TIME" to "no-es-hora"))
        }
        assertFailsWith<IllegalArgumentException> {
            IngestionConfig.fromEnv(mapOf("INGESTA_SECONDARY_TIME" to "25:00"))
        }
    }

    @Test
    fun `fails with an invalid timezone`() {
        val error = assertFailsWith<IllegalArgumentException> {
            IngestionConfig.fromEnv(mapOf("INGESTA_TIMEZONE" to "Marte/Olympus"))
        }

        assertTrue(error.message!!.contains("INGESTA_TIMEZONE"), error.message!!)
    }

    @Test
    fun `fails with an invalid boolean`() {
        assertFailsWith<IllegalArgumentException> {
            IngestionConfig.fromEnv(mapOf("INGESTA_ENABLED" to "quizas"))
        }
    }

    @Test
    fun `fails with an invalid grace`() {
        listOf("0", "-5", "media hora").forEach { raw ->
            val error = assertFailsWith<IllegalArgumentException> {
                IngestionConfig.fromEnv(mapOf("INGESTA_GRACE_MINUTES" to raw))
            }
            assertTrue(error.message!!.contains("INGESTA_GRACE_MINUTES"), error.message!!)
        }
    }
}
