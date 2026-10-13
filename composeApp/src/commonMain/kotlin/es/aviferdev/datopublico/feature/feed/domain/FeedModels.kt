package es.aviferdev.datopublico.feature.feed.domain

/**
 * Modelos de **dominio** del feed diario en el cliente.
 *
 * Son modelos propios del cliente (no DTOs de red): el mapeo desde el contrato de
 * `:shared` (`PublicacionDto` → [Publication]) llega con el cliente HTTP real en
 * FT00035. Todos los identificadores van en inglés; el KDoc, en castellano.
 *
 * Las fechas ([DailyFeed.date], [Publication.publishedDate] y
 * [Deadline.limitDate]) son cadenas **ISO-8601** (`aaaa-mm-dd`): en `commonMain`
 * no se usa `java.time` para no romper iOS/Wasm.
 */

/**
 * Feed de un día del BOE: sus **destacados** ([highlights], ya ordenados por el
 * ranker del backend) y la lista de [publications] del día.
 *
 * @property date fecha del feed en ISO-8601.
 * @property highlights publicaciones destacadas del día, cada una con sus
 *   motivos; puede estar vacía.
 * @property publications publicaciones del día; puede estar vacía (día sin
 *   contenido publicado).
 */
data class DailyFeed(
    val date: String,
    val highlights: List<Publication> = emptyList(),
    val publications: List<Publication> = emptyList(),
)

/**
 * Publicación del BOE tal y como la muestra el feed.
 *
 * @property id identificador oficial del BOE (`BOE-A-aaaa-nnnnn`).
 * @property title título oficial de la disposición.
 * @property category categoría curada de la publicación.
 * @property publishedDate fecha de publicación en ISO-8601.
 * @property shortSummary resumen corto para la tarjeta del feed.
 * @property isHighlighted `true` si el ranker la marcó como destacada.
 * @property highlightReasons motivos objetivos del destacado; vacío si no lo es.
 * @property deadline plazo de solicitud, solo en convocatorias; `null` si no
 *   aplica.
 * @property officialUrl enlace a la fuente oficial (se muestra en el detalle,
 *   FT00033).
 */
data class Publication(
    val id: String,
    val title: String,
    val category: PublicationCategory,
    val publishedDate: String,
    val shortSummary: String,
    val isHighlighted: Boolean,
    val highlightReasons: List<HighlightReason>,
    val deadline: Deadline?,
    val officialUrl: String,
)

/**
 * Categoría curada de una publicación (taxonomía de 11 valores, espejo de
 * `CategoriaDto` de `:shared`).
 *
 * Los nombres son identificadores internos en inglés; el texto que ve el
 * ciudadano se resuelve por recurso i18n en `design/components/CategoryLabel`.
 */
enum class PublicationCategory {
    /** Normas y legislación (sección normativa). */
    NORMATIVE_REGULATION,

    /** Nombramientos y ceses. */
    APPOINTMENTS_AND_DISMISSALS,

    /** Oposiciones y empleo público (convocatorias). */
    PUBLIC_EMPLOYMENT_AND_EXAMS,

    /** Becas, subvenciones y ayudas (convocatorias). */
    GRANTS_AND_SUBSIDIES,

    /** Premios. */
    AWARDS,

    /** Convenios y acuerdos. */
    AGREEMENTS_AND_CONVENTIONS,

    /** Educación y planes de estudio. */
    EDUCATION_AND_STUDY_PLANS,

    /** Medio ambiente. */
    ENVIRONMENT,

    /** Recursos y resoluciones. */
    APPEALS_AND_RESOLUTIONS,

    /** Información pública y concesiones. */
    PUBLIC_INFORMATION_AND_CONCESSIONS,

    /** Otras disposiciones y anuncios. */
    OTHER_PROVISIONS_AND_ANNOUNCEMENTS,
}

/**
 * Motivo **objetivo** por el que una publicación se marca como destacada (espejo
 * de `HighlightReasonDto` de `:shared`). El texto visible se resuelve por recurso
 * i18n en `design/components/PublicationBadges`.
 */
enum class HighlightReason {
    /** La publicación pertenece a la sección normativa (Sección I). */
    NORMATIVE_SECTION,

    /** El rango normativo encabeza la jerarquía (ley, decreto…). */
    NORMATIVE_RANK,

    /** La categoría curada es de interés ciudadano directo. */
    THEMATIC_KEYWORD,

    /** La convocatoria tiene un plazo de solicitud fiable. */
    OPEN_DEADLINE,

    /** El organismo emisor está en la lista curada de referencia. */
    RELEVANT_BODY,
}

/**
 * Plazo de solicitud de una convocatoria.
 *
 * @property limitDate fecha límite en ISO-8601.
 */
data class Deadline(val limitDate: String)

/**
 * Resultado **tipado** de cargar el feed: un fallo es un valor esperado
 * ([Failure]), no una excepción, para que la UI lo muestre como estado de error
 * con reintento y la app nunca se cierre.
 */
sealed interface FeedLoadResult {
    /** El repositorio devolvió el feed (que puede venir vacío). */
    data class Success(val feed: DailyFeed) : FeedLoadResult

    /** El repositorio no pudo devolver el feed. */
    data object Failure : FeedLoadResult
}

/**
 * Formatea una fecha ISO-8601 (`aaaa-mm-dd`) como `dd/mm/aaaa` para mostrarla.
 *
 * Es una función **pura** y libre de plataforma (no usa `java.time`, que rompería
 * iOS/Wasm). Si la cadena no tiene el formato esperado se devuelve **tal cual**,
 * sin lanzar: un dato inesperado nunca debe cerrar la app.
 *
 * @param isoDate fecha en ISO-8601.
 * @return la fecha en `dd/mm/aaaa`, o [isoDate] sin cambios si no se puede
 *   interpretar.
 */
internal fun formatIsoDate(isoDate: String): String {
    val parts = isoDate.split("-")
    val formatted = if (parts.size == 3) {
        val (year, month, day) = parts
        "$day/$month/$year"
    } else {
        isoDate
    }
    return formatted
}
