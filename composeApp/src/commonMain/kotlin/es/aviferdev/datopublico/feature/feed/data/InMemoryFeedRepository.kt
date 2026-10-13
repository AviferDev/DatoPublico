package es.aviferdev.datopublico.feature.feed.data

import es.aviferdev.datopublico.feature.feed.domain.DailyFeed
import es.aviferdev.datopublico.feature.feed.domain.Deadline
import es.aviferdev.datopublico.feature.feed.domain.FeedLoadResult
import es.aviferdev.datopublico.feature.feed.domain.HighlightReason
import es.aviferdev.datopublico.feature.feed.domain.Publication
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory
import kotlinx.coroutines.delay

/** Fecha del feed de ejemplo. */
private const val SAMPLE_DATE = "2026-10-13"

/**
 * Comportamiento del repositorio en memoria; permite ejercitar los estados de la
 * pantalla del feed sin red ni base de datos.
 */
enum class InMemoryFeedMode {
    /** Devuelve el feed de ejemplo con destacados, categorías y plazos. */
    SAMPLE,

    /** Devuelve un día sin publicaciones (estado vacío). */
    EMPTY,

    /** Devuelve un fallo tipado (estado de error con reintento). */
    UNAVAILABLE,
}

/**
 * Implementación **en memoria** de [FeedRepository] con datos de ejemplo.
 *
 * Es un **placeholder** hasta FT00035, que implementará el cliente HTTP real y la
 * caché offline sobre el contrato de `:shared`. Sirve para montar y verificar la
 * UI del feed sin backend (el feed real todavía sale vacío porque el pipeline de
 * resumen no está cableado).
 *
 * @param mode modo de comportamiento (contenido, vacío o fallo).
 * @param latencyMillis latencia simulada antes de responder; se inyecta **0** en
 *   los tests para que sean instantáneos.
 */
class InMemoryFeedRepository(
    private val mode: InMemoryFeedMode = InMemoryFeedMode.SAMPLE,
    private val latencyMillis: Long = DEFAULT_LATENCY_MILLIS,
) : FeedRepository {
    override suspend fun load(): FeedLoadResult {
        delay(latencyMillis)
        return when (mode) {
            InMemoryFeedMode.SAMPLE -> FeedLoadResult.Success(sampleFeed())
            InMemoryFeedMode.EMPTY -> FeedLoadResult.Success(DailyFeed(date = SAMPLE_DATE))
            InMemoryFeedMode.UNAVAILABLE -> FeedLoadResult.Failure
        }
    }

    companion object {
        /** Latencia por defecto de la simulación, en milisegundos. */
        const val DEFAULT_LATENCY_MILLIS: Long = 250L
    }
}

/**
 * Feed de ejemplo del cliente: seis publicaciones de categorías distintas, tres
 * de ellas destacadas (con sus motivos) y dos convocatorias con plazo.
 *
 * Es determinista (sin reloj ni aleatoriedad) para que los tests y las `@Preview`
 * puedan afirmar su contenido.
 *
 * @return el feed de ejemplo del [SAMPLE_DATE].
 */
internal fun sampleFeed(): DailyFeed {
    val highlights = listOf(
        normativeRegulationSample(),
        publicEmploymentSample(),
        researchAgreementSample(),
    )
    val publications = highlights + listOf(
        appointmentSample(),
        subsidiesSample(),
        publicInformationSample(),
    )
    return DailyFeed(date = SAMPLE_DATE, highlights = highlights, publications = publications)
}

/** Norma destacada por su sección y su rango normativo (sin plazo). */
private fun normativeRegulationSample(): Publication = Publication(
    id = "BOE-A-2026-21001",
    title = "Real Decreto 900/2026, de 12 de octubre, por el que se regula el acceso " +
        "a los servicios digitales de las administraciones públicas.",
    category = PublicationCategory.NORMATIVE_REGULATION,
    publishedDate = "2026-10-13",
    shortSummary = "Establece el derecho a relacionarse digitalmente con la Administración " +
        "y fija los plazos de adaptación de las sedes electrónicas.",
    isHighlighted = true,
    highlightReasons = listOf(HighlightReason.NORMATIVE_SECTION, HighlightReason.NORMATIVE_RANK),
    deadline = null,
    officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21001",
)

/** Convocatoria de empleo público destacada, con plazo abierto. */
private fun publicEmploymentSample(): Publication = Publication(
    id = "BOE-A-2026-21002",
    title = "Resolución de 9 de octubre de 2026, de la Secretaría de Estado de Función Pública, " +
        "por la que se convocan pruebas selectivas para el ingreso en el cuerpo general administrativo.",
    category = PublicationCategory.PUBLIC_EMPLOYMENT_AND_EXAMS,
    publishedDate = "2026-10-13",
    shortSummary = "Convocatoria de 1.250 plazas de acceso libre, con baremo de méritos y fase de oposición.",
    isHighlighted = true,
    highlightReasons = listOf(HighlightReason.OPEN_DEADLINE, HighlightReason.THEMATIC_KEYWORD),
    deadline = Deadline(limitDate = "2026-10-30"),
    officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21002",
)

/** Convenio destacado por el organismo emisor (sin plazo). */
private fun researchAgreementSample(): Publication = Publication(
    id = "BOE-A-2026-21003",
    title = "Resolución de 6 de octubre de 2026, de la Agencia Estatal de Investigación, " +
        "por la que se publica el convenio con el Consejo Superior de Investigaciones Científicas.",
    category = PublicationCategory.AGREEMENTS_AND_CONVENTIONS,
    publishedDate = "2026-10-13",
    shortSummary = "Convenio de colaboración científica con una dotación de 4,2 millones de euros hasta 2029.",
    isHighlighted = true,
    highlightReasons = listOf(HighlightReason.RELEVANT_BODY),
    deadline = null,
    officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21003",
)

/** Nombramiento sin destacado ni plazo. */
private fun appointmentSample(): Publication = Publication(
    id = "BOE-A-2026-21004",
    title = "Real Decreto 905/2026, de 13 de octubre, por el que se nombra Subdirector " +
        "General de Recursos Humanos a don A. B. C.",
    category = PublicationCategory.APPOINTMENTS_AND_DISMISSALS,
    publishedDate = "2026-10-13",
    shortSummary = "Nombramiento efectivo desde el día siguiente al de su publicación.",
    isHighlighted = false,
    highlightReasons = emptyList(),
    deadline = null,
    officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21004",
)

/** Convocatoria de ayudas (categoría de becas y subvenciones), con plazo. */
private fun subsidiesSample(): Publication = Publication(
    id = "BOE-A-2026-21005",
    title = "Extracto de la Orden de 8 de octubre de 2026 por la que se convocan ayudas " +
        "para la renaturalización de espacios urbanos.",
    category = PublicationCategory.GRANTS_AND_SUBSIDIES,
    publishedDate = "2026-10-13",
    shortSummary = "Ayudas de hasta 150.000 euros por municipio para proyectos de adaptación al cambio climático.",
    isHighlighted = false,
    highlightReasons = emptyList(),
    deadline = Deadline(limitDate = "2026-11-16"),
    officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21005",
)

/** Anuncio de información pública sin destacado ni plazo. */
private fun publicInformationSample(): Publication = Publication(
    id = "BOE-A-2026-21006",
    title = "Anuncio de la Confederación Hidrográfica del Ebro sobre información pública " +
        "de la solicitud de concesión de aguas.",
    category = PublicationCategory.PUBLIC_INFORMATION_AND_CONCESSIONS,
    publishedDate = "2026-10-13",
    shortSummary = "Periodo de información pública de la solicitud de aprovechamiento de aguas superficiales.",
    isHighlighted = false,
    highlightReasons = emptyList(),
    deadline = null,
    officialUrl = "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21006",
)
