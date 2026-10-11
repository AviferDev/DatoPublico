package es.aviferdev.datopublico.backend.rag.summary

/**
 * Clave **pura** y determinista de la caché del resumen ciudadano (FT00021).
 *
 * La clave combina **publicación + modelo + versión del prompt** con el
 * delimitador `|` (que no aparece en identificadores del BOE, modelos ni
 * versiones): un cambio del modelo o de la versión del prompt produce una clave
 * distinta y, por tanto, un **fallo de caché** (no se sirve un resumen obsoleto).
 *
 * Es un `object` sin estado ni dependencias: no toca red, BD ni reloj.
 */
object SummaryCacheKey {

    /** Delimitador entre los componentes de la clave. */
    private const val DELIMITER = "|"

    /**
     * Calcula la clave de caché de una generación.
     *
     * @param publicationId identificador de la publicación del BOE.
     * @param model nombre del modelo de generación (p. ej. `deepseek-v4.1-flash`).
     * @param promptVersion versión del prompt ([CitizenSummaryPromptBuilder.PROMPT_VERSION]).
     * @return clave determinista `"$publicationId|$model|$promptVersion"`.
     * @throws IllegalArgumentException si alguno de los tres componentes está en
     *   blanco (fail-fast: es un error de programación, no un estado de dominio).
     */
    fun of(publicationId: String, model: String, promptVersion: String): String {
        require(publicationId.isNotBlank()) { "El identificador de publicación no puede estar en blanco." }
        require(model.isNotBlank()) { "El modelo no puede estar en blanco." }
        require(promptVersion.isNotBlank()) { "La versión del prompt no puede estar en blanco." }
        return listOf(publicationId, model, promptVersion).joinToString(DELIMITER)
    }
}
