package es.aviferdev.datopublico.backend.ingesta.job

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Estado en memoria de las **ventanas ya intentadas** por el scheduler.
 *
 * El vigilante ([IngestionWatchdog]) lo consulta para no alertar de una ventana
 * que sí se está ejecutando (o se ejecutó). Es seguro para concurrencia: el
 * scheduler y el vigilante corren en corrutinas distintas sobre el mismo scope.
 *
 * Es **efímero** (vive solo mientras el proceso está en marcha); recuperar las
 * ventanas perdidas con el proceso caído es del backfill (FT00011).
 */
class IngestionRunState {
    private val attempted: MutableSet<Instant> = ConcurrentHashMap.newKeySet()

    /**
     * Marca [window] como intentada.
     *
     * El scheduler llama a este método **antes** de ejecutar el job: así una
     * ejecución larga no se confunde con una ingesta ausente.
     *
     * @param window instante de la ventana (inicio del disparo) a marcar.
     */
    fun markAttempted(window: Instant) {
        attempted.add(window)
    }

    /**
     * Indica si [window] ya se intentó.
     *
     * @param window instante de la ventana a consultar.
     */
    fun isAttempted(window: Instant): Boolean = attempted.contains(window)
}
