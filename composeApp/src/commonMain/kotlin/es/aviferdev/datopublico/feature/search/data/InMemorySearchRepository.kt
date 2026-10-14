package es.aviferdev.datopublico.feature.search.data

import es.aviferdev.datopublico.feature.feed.domain.Deadline
import es.aviferdev.datopublico.feature.feed.domain.Publication
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory
import es.aviferdev.datopublico.feature.search.domain.SearchFilters
import es.aviferdev.datopublico.feature.search.domain.SearchLoadResult
import es.aviferdev.datopublico.feature.search.domain.SearchResults
import kotlinx.coroutines.delay

/**
 * Comportamiento del repositorio en memoria; permite ejercitar los estados de la
 * pantalla de búsqueda sin red ni base de datos.
 */
enum class InMemorySearchMode {
    /** Devuelve los resultados que casan con la consulta sobre datos de ejemplo. */
    SAMPLE,

    /** Devuelve una búsqueda sin resultados (estado «sin resultados»). */
    EMPTY,

    /** Devuelve un fallo tipado (estado de error con reintento). */
    UNAVAILABLE,
}

/**
 * Implementación **en memoria** de [SearchRepository] con datos de ejemplo.
 *
 * Sirve a `@Preview` y a los tests; el cliente HTTP real es [HttpSearchRepository]
 * (FT00035 habilitó `core/network`). Filtra los datos de ejemplo por **texto**
 * (título/resumen, sin distinguir mayúsculas), **categoría** y **rango de fechas**
 * (inclusivo, comparando cadenas ISO).
 *
 * @param mode modo de comportamiento (contenido, vacío o fallo).
 * @param latencyMillis latencia simulada antes de responder; se inyecta **0** en
 *   los tests para que sean instantáneos.
 */
class InMemorySearchRepository(
    private val mode: InMemorySearchMode = InMemorySearchMode.SAMPLE,
    private val latencyMillis: Long = DEFAULT_LATENCY_MILLIS,
) : SearchRepository {
    override suspend fun search(query: String, filters: SearchFilters): SearchLoadResult {
        delay(latencyMillis)
        return when (mode) {
            InMemorySearchMode.SAMPLE -> SearchLoadResult.Success(filterSample(query, filters))
            InMemorySearchMode.EMPTY -> SearchLoadResult.Success(
                SearchResults(query = query, publications = emptyList()),
            )
            InMemorySearchMode.UNAVAILABLE -> SearchLoadResult.Failure
        }
    }

    /**
     * Filtra los datos de ejemplo por texto, categoría y rango de fechas.
     *
     * @param query término de búsqueda.
     * @param filters filtros opcionales.
     * @return los resultados que casan (pueden ser vacíos).
     */
    private fun filterSample(query: String, filters: SearchFilters): SearchResults {
        val normalized = query.lowercase()
        val matches = sampleSearchPublications().filter { publication ->
            matchesText(publication, normalized) &&
                matchesCategory(publication, filters) &&
                matchesDates(publication, filters)
        }
        return SearchResults(query = query, publications = matches)
    }

    /** Comprueba si el término casa con el título o el resumen (sin mayúsculas). */
    private fun matchesText(publication: Publication, normalizedQuery: String): Boolean =
        publication.title.lowercase().contains(normalizedQuery) ||
            publication.shortSummary.lowercase().contains(normalizedQuery)

    /** Comprueba el filtro de categoría: `null` no filtra. */
    private fun matchesCategory(publication: Publication, filters: SearchFilters): Boolean =
        filters.category?.let { category -> publication.category == category } ?: true

    /** Comprueba el rango de fechas inclusivo: una fecha ausente no filtra. */
    private fun matchesDates(publication: Publication, filters: SearchFilters): Boolean {
        val afterFrom = filters.fromDate?.let { from -> publication.publishedDate >= from } ?: true
        val beforeTo = filters.toDate?.let { to -> publication.publishedDate <= to } ?: true
        return afterFrom && beforeTo
    }

    companion object {
        /** Latencia por defecto de la simulación, en milisegundos. */
        const val DEFAULT_LATENCY_MILLIS: Long = 250L
    }
}

/**
 * Publicaciones de ejemplo del cliente: seis publicaciones de categorías y fechas
 * distintas, dos de ellas con **plazo**, para poder ejercitar los filtros de
 * categoría y de rango de fechas.
 *
 * Es determinista (sin reloj ni aleatoriedad) para que los tests puedan afirmar
 * su contenido.
 *
 * @return las publicaciones de ejemplo de la búsqueda.
 */
private fun sampleSearchPublications(): List<Publication> =
    sampleSearchGrantsAndJobs() + sampleSearchRegulation() + sampleSearchAnnouncements()

/** Convocatorias de ejemplo (becas y empleo público), ambas con plazo. */
private fun sampleSearchGrantsAndJobs(): List<Publication> = listOf(
    Publication(
        id = "BOE-A-2026-31001",
        title = "Extracto de la Orden por la que se convocan becas para jóvenes investigadores.",
        category = PublicationCategory.GRANTS_AND_SUBSIDIES,
        publishedDate = "2026-09-20",
        shortSummary = "Becas de movilidad para jóvenes investigadores con una dotación de 1.800 euros.",
        isHighlighted = false,
        highlightReasons = emptyList(),
        deadline = Deadline(limitDate = "2026-10-20"),
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-31001",
    ),
    Publication(
        id = "BOE-A-2026-31002",
        title = "Resolución por la que se convocan oposiciones de sanidad en el Servicio Público.",
        category = PublicationCategory.PUBLIC_EMPLOYMENT_AND_EXAMS,
        publishedDate = "2026-10-05",
        shortSummary = "Convocatoria de 320 plazas de oposiciones de sanidad con fase de concurso.",
        isHighlighted = false,
        highlightReasons = emptyList(),
        deadline = Deadline(limitDate = "2026-11-04"),
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-31002",
    ),
)

/** Norma y nombramiento de ejemplo, sin plazo. */
private fun sampleSearchRegulation(): List<Publication> = listOf(
    Publication(
        id = "BOE-A-2026-31003",
        title = "Real Decreto sobre el acceso digital a los servicios públicos.",
        category = PublicationCategory.NORMATIVE_REGULATION,
        publishedDate = "2026-01-15",
        shortSummary = "Regula el acceso digital de la ciudadanía a los servicios públicos.",
        isHighlighted = false,
        highlightReasons = emptyList(),
        deadline = null,
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-31003",
    ),
    Publication(
        id = "BOE-A-2026-31004",
        title = "Real Decreto por el que se nombra Subdirectora General de Recursos Humanos.",
        category = PublicationCategory.APPOINTMENTS_AND_DISMISSALS,
        publishedDate = "2026-06-10",
        shortSummary = "Nombramiento efectivo desde el día siguiente al de su publicación.",
        isHighlighted = false,
        highlightReasons = emptyList(),
        deadline = null,
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-31004",
    ),
)

/** Anuncios de ejemplo (medio ambiente y premios), sin plazo. */
private fun sampleSearchAnnouncements(): List<Publication> = listOf(
    Publication(
        id = "BOE-A-2026-31005",
        title = "Anuncio sobre la renaturalización de espacios urbanos y zonas verdes.",
        category = PublicationCategory.ENVIRONMENT,
        publishedDate = "2026-03-22",
        shortSummary = "Información pública del plan de renaturalización de espacios urbanos.",
        isHighlighted = false,
        highlightReasons = emptyList(),
        deadline = null,
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-31005",
    ),
    Publication(
        id = "BOE-A-2026-31006",
        title = "Orden por la que se concede el premio nacional de investigación.",
        category = PublicationCategory.AWARDS,
        publishedDate = "2026-10-13",
        shortSummary = "Concesión del premio nacional de investigación a tres equipos científicos.",
        isHighlighted = false,
        highlightReasons = emptyList(),
        deadline = null,
        officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-31006",
    ),
)
