package es.aviferdev.datopublico.backend.relevance

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion

/**
 * Motivo **objetivo** por el que una publicación del BOE se considera destacada.
 *
 * Es un vocabulario interno del backend (no es contrato de `:shared` ni se
 * serializa): describe **por qué** el ranker sumó puntos, para que el feed pueda
 * explicarlo al ciudadano sin criterio editorial opaco. Los textos de UI se
 * localizan en el cliente (FT00032+); aquí solo se declara la señal.
 *
 * Cada valor corresponde a una señal documentada en
 * [HighlightRanker] y a un peso aditivo de su tabla de puntuación.
 */
enum class HighlightReason {
    /** La publicación pertenece a la Sección I del BOE (normas). */
    NORMATIVE_SECTION,

    /** El rango normativo es de los que encabezan la jerarquía (ley, decreto…). */
    NORMATIVE_RANK,

    /** La categoría curada (FT00012) es de interés ciudadano directo. */
    THEMATIC_KEYWORD,

    /** La convocatoria tiene un plazo de solicitud fiable ([Publicacion.plazo]). */
    OPEN_DEADLINE,

    /** El organismo emisor está en la lista curada de emisores de referencia. */
    RELEVANT_BODY,
}

/**
 * Publicación destacada del día: la [publication] original, su [score] aditivo y
 * los [reasons] objetivos que lo justifican.
 *
 * Es un **modelo de dominio interno** (ni serializable ni `Dto`): lo producirá
 * [HighlightRanker] a partir de la lista del día y lo consumirá el feed (FT00026).
 * [reasons] **nunca** está vacía: un destacado siempre expone al menos un motivo.
 *
 * @property publication publicación del BOE destacada.
 * @property score puntuación aditiva por señales objetivas (siempre `>= MIN_SCORE`).
 * @property reasons motivos que dispararon la puntuación; no vacía.
 */
data class Highlight(
    val publication: Publicacion,
    val score: Int,
    val reasons: List<HighlightReason>,
)
