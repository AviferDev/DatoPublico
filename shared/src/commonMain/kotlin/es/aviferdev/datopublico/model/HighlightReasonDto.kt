package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Motivo **objetivo** por el que el feed marca una [PublicationDto] como destacada.
 *
 * Es la cara de **wire** del vocabulario interno `HighlightReason` de `:backend`
 * (no se comparte el enum interno para no acoplar el contrato a la implementación):
 * describe la **señal** que disparó el destacado sin exponer la puntuación numérica
 * (`score`), que es interna. Los **textos** dirigidos al ciudadano se localizan en
 * el cliente (FT00032+).
 *
 * Cada valor declara su `@SerialName` en `snake_case` **sin acentos ni espacios**:
 * es el token de wire compartido entre backend y cliente y no debe renombrarse sin
 * una migración (mismo criterio que [CategoryDto]).
 */
@Serializable
enum class HighlightReasonDto {
    /** La publicación pertenece a la Sección I del BOE (normas). */
    @SerialName("seccion_normativa")
    NORMATIVE_SECTION,

    /** El rango normativo es de los que encabezan la jerarquía (ley, decreto…). */
    @SerialName("rango_normativo")
    NORMATIVE_RANK,

    /** La categoría curada es de interés ciudadano directo. */
    @SerialName("categoria_tematica")
    THEMATIC_KEYWORD,

    /** La convocatoria tiene un plazo de solicitud fiable. */
    @SerialName("plazo_abierto")
    OPEN_DEADLINE,

    /** El organismo emisor está en la lista curada de emisores de referencia. */
    @SerialName("organismo_relevante")
    RELEVANT_BODY,
}
