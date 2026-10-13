package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Petición de chat RAG (FT00031): la **pregunta** o la **afirmación a verificar**
 * en lenguaje natural.
 *
 * Es el cuerpo JSON de `POST /chat`; la propiedad lleva `@SerialName` explícito
 * (regla MUST de `CONSTRAINTS.md` §«Serialización») y el token de wire `consulta`
 * coincide con el eco de [ChatResponseDto].
 *
 * @property query pregunta o afirmación a verificar contra el corpus del BOE.
 */
@Serializable
data class ChatRequestDto(
    @SerialName("consulta")
    val query: String,
)
