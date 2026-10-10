package es.aviferdev.datopublico.backend.ingesta.backfill

import java.time.LocalDate

/**
 * Estado persistido de un backfill: el rango cubierto y las fechas completadas.
 *
 * El runner solo **reanuda** si el `(from, to)` guardado coincide con el rango
 * configurado; si no, empieza de cero. Una fecha entra en [completedDates] solo
 * si se procesó sin error y sin entradas fallidas.
 *
 * @property from primera fecha del rango del checkpoint.
 * @property to última fecha del rango del checkpoint.
 * @property completedDates fechas ya completadas del rango.
 */
data class BackfillCheckpoint(
    val from: LocalDate,
    val to: LocalDate,
    val completedDates: Set<LocalDate>,
)

/**
 * Almacén del [BackfillCheckpoint] entre ejecuciones del backfill.
 *
 * La implementación por defecto es un **fichero local** (*one-shot* en una
 * máquina única); el contrato es agnóstico para poder sustituirlo sin tocar el
 * runner.
 */
interface BackfillCheckpointStore {
    /** Devuelve el checkpoint guardado, o `null` si todavía no hay ninguno. */
    fun load(): BackfillCheckpoint?

    /** Persiste [checkpoint] de forma duradera. */
    fun save(checkpoint: BackfillCheckpoint)
}
