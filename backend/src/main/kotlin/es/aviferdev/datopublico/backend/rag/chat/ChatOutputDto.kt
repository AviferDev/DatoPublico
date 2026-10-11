package es.aviferdev.datopublico.backend.rag.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Salida **estructurada** del modelo para el chat RAG (DTO de transporte, sufijo
 * `Dto`).
 *
 * Es lo que el modelo debe devolver como JSON y lo que parsea [ChatOutputParser].
 * **No** incluye la fuente oficial: el modelo solo aporta los **índices** de los
 * fragmentos citados ([citations]) y el servicio los resuelve a URLs oficiales con
 * el [CitationResolver] (nunca las produce el modelo).
 *
 * Cada propiedad lleva `@SerialName` explícito (regla MUST de `CONSTRAINTS.md`
 * §«Serialización»).
 *
 * @property status token de wire del estado (`respondida`, `refutada` o
 *   `sin_fundamento`); lo traduce [ChatOutputParser.statusOf].
 * @property answer respuesta del modelo para la ciudadanía.
 * @property citations índices (base 0) de los fragmentos que sustentan la
 *   respuesta; puede estar vacía (solo es válida vacía para `sin_fundamento`).
 */
@Serializable
data class ChatOutputDto(
    @SerialName("estado") val status: String,
    @SerialName("respuesta") val answer: String,
    @SerialName("citas") val citations: List<Int> = emptyList(),
)
