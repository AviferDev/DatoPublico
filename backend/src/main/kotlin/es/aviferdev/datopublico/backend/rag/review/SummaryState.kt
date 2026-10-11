package es.aviferdev.datopublico.backend.rag.review

/**
 * Estados del **resumen ciudadano** (FT00023).
 *
 * Es un valor **interno** (sin sufijo: no es un DTO de transporte) que representa
 * la máquina de estados de `docs/domain-model.md`:
 * `pendiente → generado → validado_por_guardrails → publicado | en_cola_revision
 * → revisado → publicado|descartado`.
 *
 * El identificador Kotlin va en **inglés**; el **token persistido** es el valor de
 * **dominio** (dato, no identificador) y coincide con `resumen.estado` (default
 * `pendiente` en `V2`) y con `cola_revision.estado`, así que no requiere migración
 * de datos. La disposición automática de FT00023 usa [PUBLISHED], [IN_REVIEW] y
 * [DISCARDED]; el resto queda reservado al wiring/operador (FT00026+).
 *
 * @property persistedValue token de dominio que se guarda en la base de datos.
 */
enum class SummaryState(val persistedValue: String) {
    /** Resumen creado pero aún no generado (estado inicial de la tabla). */
    PENDING("pendiente"),

    /** Resumen generado por FT00020, aún sin evaluar. */
    GENERATED("generado"),

    /** Resumen evaluado por los guardrails (FT00022). */
    VALIDATED("validado_por_guardrails"),

    /** Resumen publicado: es el único que servirá el feed (FT00026). */
    PUBLISHED("publicado"),

    /** Resumen de baja confianza pendiente de revisión interna. */
    IN_REVIEW("en_cola_revision"),

    /** Resumen revisado por un operador (transición diferida a FT00026+). */
    REVIEWED("revisado"),

    /** Resumen descartado: no se publica ni se encola. */
    DISCARDED("descartado"),
    ;

    companion object {
        /**
         * Devuelve el estado cuyo token persistido es [value], o `null` si no
         * corresponde a ningún estado conocido.
         *
         * @param value token de dominio leído de la base de datos.
         * @return el [SummaryState] correspondiente o `null`.
         */
        fun fromPersisted(value: String): SummaryState? =
            entries.firstOrNull { state -> state.persistedValue == value }
    }
}
