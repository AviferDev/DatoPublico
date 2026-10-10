package es.aviferdev.datopublico.backend.relevance

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import es.aviferdev.datopublico.model.CategoriaDto
import es.aviferdev.datopublico.model.SeccionBoeDto
import java.text.Normalizer

/**
 * Ranker **puro, determinista y explicable** de los destacados del día del BOE.
 *
 * Dada la lista de [Publicacion] de una misma fecha (la que devuelve
 * `PublicationRepository.listByDate`), marca un **subconjunto** como destacado
 * mediante una **puntuación aditiva por señales objetivas** y expone el **motivo**
 * de cada destacado ([Highlight.reasons]). Es la base del feed (FT00026).
 *
 * Es **puro**: no usa red, base de datos, IA/LLM, reloj, aleatoriedad ni estado
 * compartido, así que se verifica solo en el gate con tests unitarios y la salida
 * **no depende del orden de entrada** (orden por `score` descendente y, a igual
 * puntuación, por `id` ascendente; tope de [MAX_HIGHLIGHTS]).
 *
 * Señales y pesos (semilla determinista documentada; el afinado con datos reales
 * es deuda aceptada, no bloquea la 1.0.0):
 * - [HighlightReason.NORMATIVE_SECTION]: Sección I (normas) → [NORMATIVE_SECTION_WEIGHT].
 * - [HighlightReason.NORMATIVE_RANK]: por [Publicacion.rango] normalizado →
 *   [HIGH_RANK_WEIGHT] (ley orgánica, ley, real decreto-ley, real decreto
 *   legislativo), [REAL_DECREE_WEIGHT] (real decreto), [MEDIUM_RANK_WEIGHT]
 *   (orden, orden ministerial, instrucción), [LOW_RANK_WEIGHT] (resolución,
 *   acuerdo, circular) o [NO_WEIGHT] si es desconocido o `null`.
 * - [HighlightReason.THEMATIC_KEYWORD]: categoría curada (FT00012) de interés
 *   ciudadano → [THEMATIC_WEIGHT].
 * - [HighlightReason.OPEN_DEADLINE]: convocatoria con plazo fiable → [OPEN_DEADLINE_WEIGHT].
 * - [HighlightReason.RELEVANT_BODY]: organismo en [RELEVANT_BODIES] → [RELEVANT_BODY_WEIGHT]
 *   (peso **modesto**: **nunca** decide por sí solo un destacado).
 *
 * Es destacada la publicación con `score >= MIN_SCORE` ([MIN_SCORE]). Un día sin
 * señales devuelve la lista vacía (no se fuerza un mínimo artificial).
 */
class HighlightRanker {

    /**
     * Puntúa, filtra y ordena la lista del día.
     *
     * @param publications publicaciones de una misma fecha; puede ser vacía.
     * @return destacados ordenados por `score` desc / `id` asc, con tope
     *   [MAX_HIGHLIGHTS]; lista vacía si ninguna alcanza [MIN_SCORE].
     */
    fun rank(publications: List<Publicacion>): List<Highlight> =
        publications
            .map { publication -> highlightOf(publication) }
            .filter { highlight -> highlight.score >= MIN_SCORE }
            .sortedWith(compareByDescending<Highlight> { it.score }.thenBy { it.publication.id })
            .take(MAX_HIGHLIGHTS)

    /** Construye el [Highlight] de una publicación a partir de sus razones. */
    private fun highlightOf(publication: Publicacion): Highlight {
        val reasons = reasonsOf(publication)
        return Highlight(publication, scoreOf(publication, reasons), reasons)
    }

    /**
     * Razones objetivas que dispara [publication], en orden estable.
     *
     * Es la **única** fuente de verdad de las señales: [scoreOf] suma el peso de
     * cada razón devuelta, así que puntuación y explicación no pueden divergir.
     */
    private fun reasonsOf(publication: Publicacion): List<HighlightReason> = buildList {
        if (publication.seccion == SeccionBoeDto.I) {
            add(HighlightReason.NORMATIVE_SECTION)
        }
        if (rankScoreOf(publication.rango) > NO_WEIGHT) {
            add(HighlightReason.NORMATIVE_RANK)
        }
        if (publication.categoria in THEMATIC_CATEGORIES) {
            add(HighlightReason.THEMATIC_KEYWORD)
        }
        if (publication.plazo != null) {
            add(HighlightReason.OPEN_DEADLINE)
        }
        if (isRelevantBody(publication.organismo)) {
            add(HighlightReason.RELEVANT_BODY)
        }
    }

    /** Suma aditiva de los pesos de [reasons] (el rango aporta su peso variable). */
    private fun scoreOf(publication: Publicacion, reasons: List<HighlightReason>): Int =
        reasons.sumOf { reason ->
            when (reason) {
                HighlightReason.NORMATIVE_RANK -> rankScoreOf(publication.rango)
                HighlightReason.NORMATIVE_SECTION -> NORMATIVE_SECTION_WEIGHT
                HighlightReason.THEMATIC_KEYWORD -> THEMATIC_WEIGHT
                HighlightReason.OPEN_DEADLINE -> OPEN_DEADLINE_WEIGHT
                HighlightReason.RELEVANT_BODY -> RELEVANT_BODY_WEIGHT
            }
        }

    /** Peso del rango normativo [rank] (texto ya normalizado) o [NO_WEIGHT]. */
    private fun rankScoreOf(rank: String?): Int = when (normalize(rank)) {
        in HIGH_RANKS -> HIGH_RANK_WEIGHT
        REAL_DECREE_RANK -> REAL_DECREE_WEIGHT
        in MEDIUM_RANKS -> MEDIUM_RANK_WEIGHT
        in LOW_RANKS -> LOW_RANK_WEIGHT
        else -> NO_WEIGHT
    }

    /**
     * ¿El organismo emisor [organism] es un emisor de referencia?
     *
     * La comparación es por **prefijo normalizado**: la lista curada recoge el
     * nombre canónico del emisor y los nombres oficiales largos (p. ej. un
     * ministerio con sus competencias) lo empiezan igual.
     */
    private fun isRelevantBody(organism: String?): Boolean {
        val normalized = normalize(organism)
        return normalized.isNotEmpty() && RELEVANT_BODIES.any { normalized.startsWith(it) }
    }

    /** Minúsculas, sin acentos y con espacios colapsados (determinista). */
    private fun normalize(text: String?): String =
        Normalizer.normalize(text.orEmpty(), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase()
            .replace(WHITESPACE, " ")
            .trim()

    private companion object {
        /** Marcas diacríticas que [Normalizer] separa tras descomponer en NFD. */
        val COMBINING_MARKS = Regex("\\p{Mn}+")

        /** Espacios repetidos (incluido el espacio duro ya convertido). */
        val WHITESPACE = Regex("\\s+")

        /** Sección I (normas). */
        const val NORMATIVE_SECTION_WEIGHT = 2

        /** Rangos que encabezan la jerarquía normativa. */
        const val HIGH_RANK_WEIGHT = 4

        /** Real Decreto (por debajo de ley y de real decreto-ley/legislativo). */
        const val REAL_DECREE_WEIGHT = 3

        /** Disposiciones de rango medio. */
        const val MEDIUM_RANK_WEIGHT = 2

        /** Disposiciones de rango menor. */
        const val LOW_RANK_WEIGHT = 1

        /** Categoría curada de interés ciudadano directo. */
        const val THEMATIC_WEIGHT = 2

        /** Convocatoria con plazo de solicitud fiable. */
        const val OPEN_DEADLINE_WEIGHT = 2

        /** Emisor de referencia (peso modesto, nunca decisivo por sí solo). */
        const val RELEVANT_BODY_WEIGHT = 1

        /** Señal ausente. */
        const val NO_WEIGHT = 0

        /** Puntuación mínima para destacar (semilla determinista). */
        const val MIN_SCORE = 4

        /** Tope de destacados del día (semilla determinista). */
        const val MAX_HIGHLIGHTS = 12

        /** Rangos normativos con peso [HIGH_RANK_WEIGHT] (normalizados). */
        val HIGH_RANKS = setOf(
            "ley organica",
            "ley",
            "real decreto-ley",
            "real decreto legislativo",
        )

        /** Rango normativo con peso [REAL_DECREE_WEIGHT] (normalizado). */
        const val REAL_DECREE_RANK = "real decreto"

        /** Rangos normativos con peso [MEDIUM_RANK_WEIGHT] (normalizados). */
        val MEDIUM_RANKS = setOf("orden", "orden ministerial", "instruccion")

        /** Rangos normativos con peso [LOW_RANK_WEIGHT] (normalizados). */
        val LOW_RANKS = setOf("resolucion", "acuerdo", "circular")

        /** Categorías curadas (FT00012) que activan [THEMATIC_WEIGHT]. */
        val THEMATIC_CATEGORIES = setOf(
            CategoriaDto.OPOSICIONES_Y_EMPLEO_PUBLICO,
            CategoriaDto.BECAS_SUBVENCIONES_Y_AYUDAS,
            CategoriaDto.PREMIOS,
        )

        /**
         * Lista **curada y modesta** de emisores de referencia del BOE
         * (normalizados): jefatura del Estado, Presidencia del Gobierno, Cortes
         * Generales, Consejo de Ministros y Presidencia. Es una semilla afinable
         * con datos reales (deuda aceptada) y su señal es siempre modesta.
         */
        val RELEVANT_BODIES = listOf(
            "jefatura del estado",
            "presidencia del gobierno",
            "cortes generales",
            "consejo de ministros",
            "ministerio de la presidencia",
        )
    }
}
