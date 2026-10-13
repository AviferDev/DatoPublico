package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Respuesta del chat RAG (FT00031): el **eco** de la consulta, el **estado** del
 * dominio, el texto de la respuesta y las **citas a la fuente oficial**.
 *
 * Los tres estados del dominio viajan en [status] (`respondida`/`refutada`/
 * `sin_fundamento`): `sin_fundamento` **no** es un error y lleva [citations]
 * vacía; `respondida`/`refutada` llevan **≥1** cita con su `urlOficial`. **No**
 * expone el consumo de tokens (`usage`): el control de coste es interno
 * (FT00030). Cada propiedad lleva `@SerialName` explícito (regla MUST de
 * `CONSTRAINTS.md` §«Serialización»).
 *
 * @property query eco de la consulta recibida.
 * @property status estado del dominio de la respuesta.
 * @property answer texto de la respuesta para la ciudadanía.
 * @property citations citas a la fuente oficial; vacía en `sin_fundamento`.
 */
@Serializable
data class ChatResponseDto(
    @SerialName("consulta")
    val query: String,
    @SerialName("estado")
    val status: ChatStatusDto,
    @SerialName("respuesta")
    val answer: String,
    @SerialName("citas")
    val citations: List<ChatCitationDto> = emptyList(),
)
