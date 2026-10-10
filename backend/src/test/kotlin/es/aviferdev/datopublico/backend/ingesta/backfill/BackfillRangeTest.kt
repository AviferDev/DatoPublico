package es.aviferdev.datopublico.backend.ingesta.backfill

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests de gate de [BackfillRange] (escenario 1): división en lotes con el
 * último más corto, cobertura sin repeticiones y validación del rango.
 */
class BackfillRangeTest {

    @Test
    fun `splits the range into batches with a shorter last batch`() {
        val range = BackfillRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 10))

        val batches = range.batches(4)

        assertEquals(
            listOf(
                daysOf(1, 4),
                daysOf(5, 8),
                daysOf(9, 10),
            ),
            batches,
        )
        assertEquals(listOf(9, 10), batches.last().map { it.dayOfMonth })
    }

    @Test
    fun `covers every day once in chronological order`() {
        val range = BackfillRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 10))

        val dates = range.batches(4).flatten()

        assertEquals(10, dates.size)
        assertEquals(dates.sorted(), dates)
        assertEquals(dates.size, dates.toSet().size, "cada día aparece una sola vez")
    }

    @Test
    fun `a single-day range yields one date and one batch`() {
        val range = BackfillRange(DATE, DATE)

        assertEquals(listOf(DATE), range.dates())
        assertEquals(listOf(listOf(DATE)), range.batches(7))
    }

    @Test
    fun `rejects a range whose from is after to`() {
        assertFailsWith<IllegalArgumentException> {
            BackfillRange(LocalDate.of(2024, 1, 10), LocalDate.of(2024, 1, 1))
        }
    }

    @Test
    fun `rejects a non-positive batch size`() {
        val range = BackfillRange(DATE, DATE)

        listOf(0, -1).forEach { size ->
            assertFailsWith<IllegalArgumentException> { range.batches(size) }
        }
    }

    private fun daysOf(firstDay: Int, lastDay: Int): List<LocalDate> =
        (firstDay..lastDay).map { day -> LocalDate.of(2024, 1, day) }

    private companion object {
        val DATE: LocalDate = LocalDate.of(2024, 1, 1)
    }
}
