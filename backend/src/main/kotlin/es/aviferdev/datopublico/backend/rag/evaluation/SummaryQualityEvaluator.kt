package es.aviferdev.datopublico.backend.rag.evaluation

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.rag.summary.CitizenSummaryGenerator
import es.aviferdev.datopublico.backend.rag.summary.CitizenSummaryResult
import es.aviferdev.datopublico.backend.rag.summary.SummaryGenerationDto
import es.aviferdev.datopublico.model.ResumenDto

/**
 * Generador **inyectable** de la evaluación de resúmenes (FT00024).
 *
 * Es un `fun interface` cuya firma encaja con
 * [es.aviferdev.datopublico.backend.rag.summary.CitizenSummaryGenerator.generate]
 * (`publication`, `limit`): la CLI la implementa con una lambda que delega en
 * `CitizenSummaryGenerator`, y los tests usan un doble determinista. Así el
 * evaluador **no** conoce OpenCode, la recuperación, el repositorio ni Ktor.
 */
fun interface SummaryGenerator {
    /**
     * Genera el resumen ciudadano de [publication].
     *
     * @param publication publicación de origen; debe tener categoría.
     * @param limit número máximo de fragmentos de contexto (`>= 1`).
     * @return [CitizenSummaryResult.Valid] si cumple el contrato, o
     *   [CitizenSummaryResult.Invalid] con las violaciones si no.
     */
    suspend fun generate(publication: Publicacion, limit: Int): CitizenSummaryResult
}

/**
 * **Evaluador** de la calidad del resumen ciudadano (FT00024): genera una vez por
 * entrada del golden set y mide el acierto contra su resumen de referencia.
 *
 * Es un servicio **sin BD ni proveedor conocido**: la generación la delega en el
 * [SummaryGenerator] inyectado, así que es testeable con un doble determinista y
 * sin red. Por entrada calcula el **F1 léxico** de «¿Qué cambia?» y «¿A quién
 * afecta?» ([SummaryQualityMetrics.tokenF1]) y el ***recall* de cifras**; un
 * resultado [CitizenSummaryResult.Invalid] **no lanza**: cuenta como fallo.
 *
 * @param generator generador de resúmenes que resuelve cada entrada.
 * @param hitThreshold umbral mínimo de ambos F1 para contar la entrada como
 *   acierto (`[0, 1]`); por defecto [DEFAULT_HIT_THRESHOLD].
 */
class SummaryQualityEvaluator(
    private val generator: SummaryGenerator,
    private val hitThreshold: Double = DEFAULT_HIT_THRESHOLD,
) {

    init {
        require(hitThreshold.isFinite() && hitThreshold in 0.0..1.0) {
            "El umbral de acierto debe ser un número finito en [0, 1], pero es $hitThreshold."
        }
    }

    /**
     * Evalúa [dataset] y devuelve el resultado agregado.
     *
     * Genera **una vez** por entrada (`generator.generate(publication, limit)`),
     * calcula las métricas contra su referencia y deriva el acierto
     * (`whatChangesF1 >= hitThreshold && whoIsAffectedF1 >= hitThreshold`). Las
     * medias usan [mean], que devuelve `0.0` para una lista vacía (nunca producen
     * `NaN`).
     *
     * @param dataset golden set validado a evaluar.
     * @return métricas por entrada y sus medias, la tasa de acierto y el número de
     *   entradas inválidas.
     */
    suspend fun evaluate(dataset: SummaryGoldenSet): SummaryQualityResult {
        val perEntry = dataset.entries.map { entry -> evaluateEntry(entry) }
        return SummaryQualityResult(
            entryCount = perEntry.size,
            meanWhatChangesF1 = perEntry.map { row -> row.whatChangesF1 }.mean(),
            meanWhoIsAffectedF1 = perEntry.map { row -> row.whoIsAffectedF1 }.mean(),
            meanFiguresRecall = perEntry.map { row -> row.figuresRecall }.mean(),
            hitRate = hitRateOf(perEntry),
            invalidCount = perEntry.count { row -> row.invalid },
            perEntry = perEntry,
        )
    }

    /** Genera el resumen de [entry] y lo puntúa contra su referencia. */
    private suspend fun evaluateEntry(entry: SummaryGoldenSetEntry): SummaryEntryResult {
        val result = generator.generate(entry.publication, DEFAULT_LIMIT)
        return entryResultOf(entry, result)
    }

    /** Traduce el resultado del generador a la puntuación de una entrada. */
    private fun entryResultOf(
        entry: SummaryGoldenSetEntry,
        result: CitizenSummaryResult,
    ): SummaryEntryResult = when (result) {
        is CitizenSummaryResult.Valid -> validEntryResult(entry, result.summary)
        is CitizenSummaryResult.Invalid -> invalidEntryResult(entry.id)
    }

    /** Puntúa un resumen **válido** contra su referencia. */
    private fun validEntryResult(entry: SummaryGoldenSetEntry, summary: ResumenDto): SummaryEntryResult {
        val whatChangesF1 = SummaryQualityMetrics.tokenF1(summary.queCambia, entry.reference.queCambia)
        val whoIsAffectedF1 = SummaryQualityMetrics.tokenF1(summary.aQuienAfecta, entry.reference.aQuienAfecta)
        return SummaryEntryResult(
            entryId = entry.id,
            whatChangesF1 = whatChangesF1,
            whoIsAffectedF1 = whoIsAffectedF1,
            figuresRecall = SummaryQualityMetrics.figuresRecall(
                summary.toGenerationDto(),
                SummaryQualityMetrics.figuresOf(entry.reference),
            ),
            hit = whatChangesF1 >= hitThreshold && whoIsAffectedF1 >= hitThreshold,
            invalid = false,
        )
    }

    /** Registra un resumen **inválido** como fallo (F1 = 0 en ambos campos). */
    private fun invalidEntryResult(entryId: String): SummaryEntryResult = SummaryEntryResult(
        entryId = entryId,
        whatChangesF1 = NO_METRIC,
        whoIsAffectedF1 = NO_METRIC,
        figuresRecall = NO_METRIC,
        hit = false,
        invalid = true,
    )

    /** Tasa de acierto `aciertos / entradas`, o `0.0` si no hay entradas (sin `NaN`). */
    private fun hitRateOf(rows: List<SummaryEntryResult>): Double =
        if (rows.isEmpty()) 0.0 else rows.count { row -> row.hit }.toDouble() / rows.size

    companion object {
        /**
         * Umbral de acierto por defecto: ambos F1 deben llegar a `0.5`.
         *
         * Es un punto de partida **conservador y ajustable**; el valor definitivo se
         * calibra con datos reales. El gate no depende de su valor (usa un doble
         * determinista) y el test live usa el suyo.
         */
        const val DEFAULT_HIT_THRESHOLD: Double = 0.5

        /** Fragmentos de contexto por defecto: el mismo que la generación real. */
        const val DEFAULT_LIMIT: Int = CitizenSummaryGenerator.DEFAULT_LIMIT

        /** Valor de una métrica sin base de comparación (resumen inválido). */
        private const val NO_METRIC: Double = 0.0
    }
}

/**
 * Resultado agregado de una evaluación de resúmenes.
 *
 * @property entryCount número de entradas evaluadas.
 * @property meanWhatChangesF1 media del F1 de «¿Qué cambia?» (0.0 si no hay entradas).
 * @property meanWhoIsAffectedF1 media del F1 de «¿A quién afecta?» (0.0 si no hay entradas).
 * @property meanFiguresRecall media del *recall* de cifras (0.0 si no hay entradas).
 * @property hitRate fracción de entradas con ambos F1 por encima del umbral.
 * @property invalidCount número de resúmenes inválidos (contados como fallo).
 * @property perEntry resultado de cada entrada, en el orden del dataset.
 */
data class SummaryQualityResult(
    val entryCount: Int,
    val meanWhatChangesF1: Double,
    val meanWhoIsAffectedF1: Double,
    val meanFiguresRecall: Double,
    val hitRate: Double,
    val invalidCount: Int,
    val perEntry: List<SummaryEntryResult>,
)

/**
 * Resultado de una entrada del golden set.
 *
 * @property entryId identificador de la entrada.
 * @property whatChangesF1 F1 léxico de «¿Qué cambia?».
 * @property whoIsAffectedF1 F1 léxico de «¿A quién afecta?».
 * @property figuresRecall *recall* de cifras.
 * @property hit `true` si ambos F1 alcanzan el umbral.
 * @property invalid `true` si el generador devolvió un resumen inválido.
 */
data class SummaryEntryResult(
    val entryId: String,
    val whatChangesF1: Double,
    val whoIsAffectedF1: Double,
    val figuresRecall: Double,
    val hit: Boolean,
    val invalid: Boolean,
)

/**
 * Mapea el resumen publicado [ResumenDto] a la forma comparable
 * [SummaryGenerationDto] (los campos que la métrica necesita).
 *
 * Es una extensión **privada** y pura: solo cierra el encaje de tipos entre el
 * contrato de wire y el DTO que consumen las métricas.
 */
private fun ResumenDto.toGenerationDto(): SummaryGenerationDto = SummaryGenerationDto(
    queCambia = queCambia,
    aQuienAfecta = aQuienAfecta,
    cifrasClave = cifrasClave,
    plazo = plazo,
)
