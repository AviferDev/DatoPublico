package es.aviferdev.datopublico.backend.rag.evaluation

import es.aviferdev.datopublico.backend.rag.guardrails.SummaryTextNormalizer
import es.aviferdev.datopublico.backend.rag.summary.SummaryGenerationDto

/**
 * Métricas **puras** de evaluación de la calidad del resumen ciudadano (FT00024):
 * F1 léxico por campo («¿Qué cambia?» y «¿A quién afecta?») y *recall* de cifras.
 *
 * No tiene estado ni dependencias (sin red, BD, modelo, reloj ni aleatoriedad):
 * recibe cadenas o el DTO del resumen y devuelve un `Double`. Reutiliza
 * [SummaryTextNormalizer] (FT00022) para tokenizar, así que la comparación es
 * **léxica** (sin *stemming* ni sinónimos): una paráfrasis correcta puede bajar el
 * F1; el objetivo es **detectar regresiones**, no certificar calidad absoluta. Los
 * casos límite están documentados por función y son los que cubre
 * `SummaryQualityMetricsTest`.
 */
object SummaryQualityMetrics {

    /** F1 de una comparación sin ningún término en común (o sin referencia). */
    const val NO_OVERLAP_F1: Double = 0.0

    /**
     * *Recall* de cifras cuando la referencia **no tiene** cifras: verdad vacua.
     *
     * Si la referencia no exige ninguna cifra, cualquier resumen la cumple por
     * vacuidad (mismo criterio que `precision`/`recall` de conjuntos vacíos en la
     * evaluación de recuperación).
     */
    const val VACUOUS_FIGURES_RECALL: Double = 1.0

    /**
     * **F1 léxico** entre [hypothesis] (resumen generado) y [reference] (resumen de
     * referencia), con el **multiconjunto** de `contentTokens` de
     * [SummaryTextNormalizer]:
     *
     * - `precision = |∩| / |hipótesis|`
     * - `recall = |∩| / |referencia|`
     * - `F1 = 2·P·R / (P + R)`
     *
     * Es `0.0` si `P + R = 0` (sin términos en común, hipótesis vacía o referencia
     * sin términos); en particular, una referencia sin términos da `0.0` porque su
     * `recall` es `0.0`.
     *
     * @param hypothesis resumen generado.
     * @param reference resumen de referencia.
     * @return F1 en `[0, 1]`.
     */
    fun tokenF1(hypothesis: String, reference: String): Double {
        val hypothesisTokens = SummaryTextNormalizer.contentTokens(hypothesis)
        val referenceTokens = SummaryTextNormalizer.contentTokens(reference)
        val overlap = overlapCount(hypothesisTokens, referenceTokens)
        val precision = ratio(overlap, hypothesisTokens.size)
        val recall = ratio(overlap, referenceTokens.size)
        val total = precision + recall
        return if (total == 0.0) NO_OVERLAP_F1 else 2.0 * precision * recall / total
    }

    /**
     * *Recall* de **cifras**: fracción de [referenceFigures] (cifras de la
     * referencia) que aparecen entre las cifras de [summary].
     *
     * Las cifras del resumen son la **unión** de las extraídas de `queCambia`,
     * `aQuienAfecta` y `cifrasClave` ([figuresOf]); se comparan como conjunto (una
     * cifra repetida cuenta una vez). Si [referenceFigures] está vacío devuelve
     * [VACUOUS_FIGURES_RECALL] (verdad vacua).
     *
     * @param summary resumen generado.
     * @param referenceFigures cifras que la referencia exige.
     * @return cobertura de cifras en `[0, 1]`.
     */
    fun figuresRecall(summary: SummaryGenerationDto, referenceFigures: List<String>): Double {
        val requiredFigures = referenceFigures.distinct()
        val summaryFigures = figuresOf(summary).toSet()
        return if (requiredFigures.isEmpty()) {
            VACUOUS_FIGURES_RECALL
        } else {
            requiredFigures.count { figure -> figure in summaryFigures }.toDouble() / requiredFigures.size
        }
    }

    /**
     * Extrae las **cifras** de [summary]: los tokens con dígitos de la unión de
     * `queCambia`, `aQuienAfecta` y `cifrasClave`.
     *
     * Es el valor que [figuresRecall] compara contra las cifras de la referencia.
     *
     * @param summary resumen del que extraer las cifras.
     * @return cifras en orden de aparición; vacío si no hay ninguna.
     */
    fun figuresOf(summary: SummaryGenerationDto): List<String> =
        SummaryTextNormalizer.figures(fieldsText(summary))

    /** Concadena los campos comparables de [summary] en un único texto. */
    private fun fieldsText(summary: SummaryGenerationDto): String =
        listOf(summary.queCambia, summary.aQuienAfecta, summary.cifrasClave.joinToString(separator = " "))
            .joinToString(separator = " ")

    /** `part / total` como `Double`, o `0.0` si [total] es `0` (sin `NaN`). */
    private fun ratio(part: Int, total: Int): Double =
        if (total == 0) 0.0 else part.toDouble() / total

    /**
     * Cuenta el solape de [left] contra [right] **como multiconjunto**: cada
     * término de [right] se consume una sola vez (mínimo de repeticiones).
     */
    private fun overlapCount(left: List<String>, right: List<String>): Int {
        val remaining = right.groupingBy { token -> token }.eachCount().toMutableMap()
        return left.count { token ->
            val available = remaining[token] ?: 0
            remaining[token] = available - 1
            available > 0
        }
    }
}
