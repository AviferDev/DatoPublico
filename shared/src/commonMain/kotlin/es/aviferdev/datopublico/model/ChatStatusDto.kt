package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Estado de **wire** de una respuesta del chat RAG (FT00031).
 *
 * Es el contrato de transporte del estado **interno** `ChatStatus` de la librería
 * `rag/chat` (FT00025): cada valor declara un `@SerialName` en `snake_case` con
 * los tres tokens del dominio (`docs/domain-model.md` §Consulta) y **no** debe
 * renombrarse sin una migración. La UI de chat (FT00037) localizará estos tokens.
 */
@Serializable
enum class ChatStatusDto {
    /** La pregunta se responde con el corpus recuperado. */
    @SerialName("respondida")
    ANSWERED,

    /** La afirmación se refuta con el corpus recuperado. */
    @SerialName("refutada")
    REFUTED,

    /** El corpus no permite confirmar ni refutar; no es un error. */
    @SerialName("sin_fundamento")
    UNFOUNDED,
}
