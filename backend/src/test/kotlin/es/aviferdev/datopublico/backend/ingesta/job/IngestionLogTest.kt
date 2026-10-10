package es.aviferdev.datopublico.backend.ingesta.job

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Test de gate del mapeo puro de campos del evento estructurado de ingesta
 * (escenarios 1–2 del spec): una ejecución correcta produce
 * `ingestion.run.completed` con sus recuentos y una fallida produce
 * `ingestion.run.failed` con el motivo. Sin Logback ni red.
 */
class IngestionLogTest {

    private val date: LocalDate = LocalDate.of(2026, 10, 9)

    @Test
    fun `a successful run exposes the completed event fields`() {
        val result = IngestionResult(date, totalEntries = 205, saved = 205, failed = 0)

        val fields = IngestionLog.runFields(result)

        assertEquals(IngestionLog.EVENT_RUN_COMPLETED, fields[IngestionLog.FIELD_EVENT])
        assertEquals("2026-10-09", fields[IngestionLog.FIELD_INGESTION_DATE])
        assertEquals(205, fields[IngestionLog.FIELD_TOTAL_ENTRIES])
        assertEquals(205, fields[IngestionLog.FIELD_PUBLICATIONS_SAVED])
        assertEquals(0, fields[IngestionLog.FIELD_PUBLICATIONS_FAILED])
        assertFalse(fields.containsKey(IngestionLog.FIELD_ERROR))
    }

    @Test
    fun `a failed run exposes the failed event with its error`() {
        val result = IngestionResult(
            date = date,
            totalEntries = 0,
            saved = 0,
            failed = 0,
            error = "BOE caído",
        )

        val fields = IngestionLog.runFields(result)

        assertEquals(IngestionLog.EVENT_RUN_FAILED, fields[IngestionLog.FIELD_EVENT])
        assertEquals("2026-10-09", fields[IngestionLog.FIELD_INGESTION_DATE])
        assertEquals("BOE caído", fields[IngestionLog.FIELD_ERROR])
    }
}
