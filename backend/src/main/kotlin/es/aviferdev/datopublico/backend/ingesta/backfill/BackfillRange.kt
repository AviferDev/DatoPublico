package es.aviferdev.datopublico.backend.ingesta.backfill

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Rango **inclusivo** de fechas del backfill y su división en lotes.
 *
 * Es pura (sin red ni base de datos): solo calcula el calendario de fechas y su
 * agrupación para ejecutar el histórico en baja carga. Cada día aparece **una
 * sola vez** y en orden cronológico.
 *
 * @property from primera fecha del rango (incluida).
 * @property to última fecha del rango (incluida); no puede ser anterior a [from].
 */
data class BackfillRange(
    val from: LocalDate,
    val to: LocalDate,
) {
    init {
        require(!from.isAfter(to)) { "Rango de backfill inválido: $from es posterior a $to." }
    }

    /** Fechas del rango en orden cronológico, ambas incluidas. */
    fun dates(): List<LocalDate> {
        val totalDays = ChronoUnit.DAYS.between(from, to).toInt()
        return (0..totalDays).map { offset -> from.plusDays(offset.toLong()) }
    }

    /**
     * Divide el rango en lotes consecutivos de [batchDays] fechas.
     *
     * El último lote puede ser **más corto** si el rango no es divisible; cada
     * día aparece una sola vez y los lotes conservan el orden cronológico.
     *
     * @param batchDays días por lote; debe ser mayor que 0.
     * @throws IllegalArgumentException si [batchDays] no es positivo.
     */
    fun batches(batchDays: Int): List<List<LocalDate>> {
        require(batchDays > 0) { "El tamaño de lote debe ser mayor que 0: '$batchDays'." }
        return dates().chunked(batchDays)
    }
}
