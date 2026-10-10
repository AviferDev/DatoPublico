package es.aviferdev.datopublico.backend.rag.summary

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.backend.persistence.FragmentEntity
import es.aviferdev.datopublico.backend.rag.generation.TextGenerationProvider
import es.aviferdev.datopublico.backend.rag.retrieval.HybridSearch
import es.aviferdev.datopublico.backend.rag.retrieval.SearchFilter
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.ResumenDto
import es.aviferdev.datopublico.validation.CitizenSummaryValidation
import es.aviferdev.datopublico.validation.CitizenSummaryValidator

/**
 * Servicio de **generación del resumen ciudadano** (FT00020): recupera el
 * contexto, construye el prompt, invoca el proveedor de texto, parsea la salida,
 * inyecta la fuente oficial y **valida el contrato** (FT00019).
 *
 * Es una **librería sin wiring** (no se construye en `Application.kt` ni en el
 * job): compone [HybridSearch] (recuperación, FT00017), un
 * [TextGenerationProvider] intercambiable (OpenCode en producción; un doble en los
 * tests), el [CitizenSummaryPromptBuilder] y el [SummaryOutputParser]. **No**
 * conoce Ktor, HTTP ni el modelo concreto.
 *
 * Un resumen que no cumple el contrato **no se publica**: se devuelve
 * [CitizenSummaryResult.Invalid] con las violaciones (no una excepción). La
 * **fuente oficial** se inyecta desde `Publicacion.urlOficial`, nunca la produce
 * el modelo.
 *
 * @param hybridSearch recuperación híbrida que aporta los fragmentos de contexto.
 * @param textGenerationProvider proveedor de texto (sustituible por un doble).
 * @param promptBuilder constructor puro del prompt.
 * @param parser parser puro de la salida del modelo.
 */
class CitizenSummaryGenerator(
    private val hybridSearch: HybridSearch,
    private val textGenerationProvider: TextGenerationProvider,
    private val promptBuilder: CitizenSummaryPromptBuilder = CitizenSummaryPromptBuilder(),
    private val parser: SummaryOutputParser = SummaryOutputParser(),
) {

    /**
     * Genera el resumen ciudadano de [publication].
     *
     * @param publication publicación de origen; debe tener `categoria`.
     * @param limit número máximo de fragmentos a recuperar (`>= 1`).
     * @return [CitizenSummaryResult.Valid] si cumple el contrato, o
     *   [CitizenSummaryResult.Invalid] con las violaciones si no.
     * @throws IllegalArgumentException si [publication] no tiene categoría
     *   (fail-fast **antes** de recuperar o invocar al proveedor).
     * @throws es.aviferdev.datopublico.backend.rag.generation.TextGenerationException
     *   si el proveedor falla.
     * @throws SummaryGenerationException si la salida del proveedor no es
     *   interpretable.
     */
    suspend fun generate(publication: Publicacion, limit: Int = DEFAULT_LIMIT): CitizenSummaryResult {
        val category = requireCategory(publication)
        val request = promptBuilder.build(publication, retrieve(publication, limit))
        val response = textGenerationProvider.generate(request)
        val summary = parser.parse(response.text).toResumenDto(publication.urlOficial)
        return resultOf(summary, CitizenSummaryValidator.validate(summary, category))
    }

    /** Categoría obligatoria de la publicación (fail-fast si falta). */
    private fun requireCategory(publication: Publicacion): CategoriaDto =
        publication.categoria
            ?: throw IllegalArgumentException(
                "La publicación ${publication.id} no tiene categoría; no se puede generar el resumen."
            )

    /** Recupera el contexto de la publicación con un filtro por sus metadatos. */
    private fun retrieve(publication: Publicacion, limit: Int): List<FragmentEntity> {
        val filter = SearchFilter(
            publishedFrom = publication.fechaPublicacion,
            publishedTo = publication.fechaPublicacion,
            category = publication.categoria,
            section = publication.seccion,
            organization = publication.organismo,
        )
        return hybridSearch.search(publication.titulo, filter, limit).map { match -> match.fragment }
    }

    /** Traduce la validación del contrato a un resultado publicable o inválido. */
    private fun resultOf(summary: ResumenDto, validation: CitizenSummaryValidation): CitizenSummaryResult =
        if (validation.isValid) {
            CitizenSummaryResult.Valid(summary)
        } else {
            CitizenSummaryResult.Invalid(validation.violations)
        }

    private companion object {
        /** Número de fragmentos de contexto por defecto. */
        const val DEFAULT_LIMIT = 5
    }
}
