package es.aviferdev.datopublico.backend.ingesta.backfill

import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests de gate de [BackfillConfig] (sin red ni base de datos): defaults,
 * overrides del entorno `BACKFILL_*` y validación fail-fast.
 */
class BackfillConfigTest {

    @Test
    fun `uses the default values without environment`() {
        val config = BackfillConfig.fromEnv(emptyMap(), today = TODAY)

        assertEquals(TODAY, config.to)
        assertEquals(TODAY.minusYears(1), config.from, "por defecto cubre el último año")
        assertEquals(BackfillConfig.DEFAULT_BATCH_DAYS, config.batchDays)
        assertEquals(BackfillConfig.DEFAULT_DELAY_MS, config.delayMillis)
        assertEquals(Path.of(BackfillConfig.DEFAULT_STATE_FILE), config.stateFile)
        assertEquals(BackfillRange(config.from, config.to), config.range)
    }

    @Test
    fun `reads the environment overrides`() {
        val config = BackfillConfig.fromEnv(
            mapOf(
                "BACKFILL_FROM" to "2024-01-01",
                "BACKFILL_TO" to "2024-01-10",
                "BACKFILL_BATCH_DAYS" to "4",
                "BACKFILL_DELAY_MS" to "0",
                "BACKFILL_STATE_FILE" to "/tmp/estado.txt",
            ),
            today = TODAY,
        )

        assertEquals(LocalDate.of(2024, 1, 1), config.from)
        assertEquals(LocalDate.of(2024, 1, 10), config.to)
        assertEquals(4, config.batchDays)
        assertEquals(0L, config.delayMillis)
        assertEquals(Path.of("/tmp/estado.txt"), config.stateFile)
    }

    @Test
    fun `ignores blank values and uses the defaults`() {
        val config = BackfillConfig.fromEnv(
            mapOf(
                "BACKFILL_FROM" to "  ",
                "BACKFILL_TO" to "",
                "BACKFILL_BATCH_DAYS" to " ",
                "BACKFILL_DELAY_MS" to "  ",
                "BACKFILL_STATE_FILE" to " ",
            ),
            today = TODAY,
        )

        assertEquals(TODAY, config.to)
        assertEquals(TODAY.minusYears(1), config.from)
        assertEquals(BackfillConfig.DEFAULT_BATCH_DAYS, config.batchDays)
        assertEquals(BackfillConfig.DEFAULT_DELAY_MS, config.delayMillis)
        assertEquals(Path.of(BackfillConfig.DEFAULT_STATE_FILE), config.stateFile)
    }

    @Test
    fun `fails when from is after to`() {
        val error = assertFailsWith<IllegalArgumentException> {
            BackfillConfig.fromEnv(
                mapOf("BACKFILL_FROM" to "2024-02-01", "BACKFILL_TO" to "2024-01-01"),
                today = TODAY,
            )
        }

        assertTrue(error.message!!.contains("BACKFILL_FROM"), error.message!!)
    }

    @Test
    fun `fails with invalid dates`() {
        listOf("BACKFILL_FROM", "BACKFILL_TO").forEach { key ->
            val error = assertFailsWith<IllegalArgumentException> {
                BackfillConfig.fromEnv(mapOf(key to "no-es-fecha"), today = TODAY)
            }
            assertTrue(error.message!!.contains(key), error.message!!)
        }
    }

    @Test
    fun `fails with invalid batch days`() {
        listOf("0", "-3", "muchos").forEach { raw ->
            val error = assertFailsWith<IllegalArgumentException> {
                BackfillConfig.fromEnv(mapOf("BACKFILL_BATCH_DAYS" to raw), today = TODAY)
            }
            assertTrue(error.message!!.contains("BACKFILL_BATCH_DAYS"), error.message!!)
        }
    }

    @Test
    fun `fails with an invalid delay`() {
        listOf("-1", "medio").forEach { raw ->
            val error = assertFailsWith<IllegalArgumentException> {
                BackfillConfig.fromEnv(mapOf("BACKFILL_DELAY_MS" to raw), today = TODAY)
            }
            assertTrue(error.message!!.contains("BACKFILL_DELAY_MS"), error.message!!)
        }
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 10, 10)
    }
}
