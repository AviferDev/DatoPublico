package es.aviferdev.datopublico.backend.rag.summary

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests de gate de [GenerateSummariesConfig] (sin red, base de datos ni reloj
 * real): defaults, rango inclusivo de [GenerateSummariesConfig.dates], overrides
 * del entorno `SUMMARY_*` y validación fail-fast.
 */
class GenerateSummariesConfigTest {

    @Test
    fun `uses today as the default range without environment`() {
        val config = GenerateSummariesConfig.fromEnv(emptyMap(), today = TODAY)

        assertEquals(TODAY, config.from)
        assertEquals(TODAY, config.to)
        assertEquals(GenerateSummariesConfig.DEFAULT_LIMIT, config.limit)
        assertEquals(GenerateSummariesConfig.DEFAULT_DELAY_MS, config.delayMillis)
    }

    @Test
    fun `dates includes both ends of a three day range`() {
        val config = GenerateSummariesConfig.fromEnv(
            mapOf("SUMMARY_FROM" to "2026-10-08", "SUMMARY_TO" to "2026-10-10"),
            today = TODAY,
        )

        assertEquals(
            listOf(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10)),
            config.dates(),
        )
    }

    @Test
    fun `reads the environment overrides`() {
        val config = GenerateSummariesConfig.fromEnv(
            mapOf(
                "SUMMARY_FROM" to "2026-09-01",
                "SUMMARY_TO" to "2026-09-30",
                "SUMMARY_LIMIT" to "7",
                "SUMMARY_DELAY_MS" to "0",
            ),
            today = TODAY,
        )

        assertEquals(LocalDate.of(2026, 9, 1), config.from)
        assertEquals(LocalDate.of(2026, 9, 30), config.to)
        assertEquals(7, config.limit)
        assertEquals(0L, config.delayMillis)
    }

    @Test
    fun `ignores blank values and uses the defaults`() {
        val config = GenerateSummariesConfig.fromEnv(
            mapOf(
                "SUMMARY_FROM" to "  ",
                "SUMMARY_TO" to "",
                "SUMMARY_LIMIT" to " ",
                "SUMMARY_DELAY_MS" to "  ",
            ),
            today = TODAY,
        )

        assertEquals(TODAY, config.from)
        assertEquals(TODAY, config.to)
        assertEquals(GenerateSummariesConfig.DEFAULT_LIMIT, config.limit)
        assertEquals(GenerateSummariesConfig.DEFAULT_DELAY_MS, config.delayMillis)
    }

    @Test
    fun `fails when from is after to`() {
        val error = assertFailsWith<IllegalArgumentException> {
            GenerateSummariesConfig.fromEnv(
                mapOf("SUMMARY_FROM" to "2026-10-11", "SUMMARY_TO" to "2026-10-10"),
                today = TODAY,
            )
        }

        assertTrue(error.message!!.contains("SUMMARY_FROM"), error.message!!)
        assertTrue(error.message!!.contains("SUMMARY_TO"), error.message!!)
    }

    @Test
    fun `fails with a non positive limit`() {
        listOf("0", "-3").forEach { raw ->
            val error = assertFailsWith<IllegalArgumentException> {
                GenerateSummariesConfig.fromEnv(mapOf("SUMMARY_LIMIT" to raw), today = TODAY)
            }
            assertTrue(error.message!!.contains("SUMMARY_LIMIT"), error.message!!)
        }
    }

    @Test
    fun `fails with a negative delay`() {
        val error = assertFailsWith<IllegalArgumentException> {
            GenerateSummariesConfig.fromEnv(mapOf("SUMMARY_DELAY_MS" to "-1"), today = TODAY)
        }

        assertTrue(error.message!!.contains("SUMMARY_DELAY_MS"), error.message!!)
    }

    @Test
    fun `fails with a non ISO date`() {
        val error = assertFailsWith<IllegalArgumentException> {
            GenerateSummariesConfig.fromEnv(mapOf("SUMMARY_FROM" to "2026-13-01"), today = TODAY)
        }

        assertTrue(error.message!!.contains("SUMMARY_FROM"), error.message!!)
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 10)
    }
}
