package es.aviferdev.datopublico.feature.chat.data.remote

import es.aviferdev.datopublico.feature.chat.domain.ChatAnswer
import es.aviferdev.datopublico.feature.chat.domain.ChatCitation
import es.aviferdev.datopublico.feature.chat.domain.ChatStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTOs de **wire** y mapeo del chat RAG en el cliente (FT00037, sobre
 * `core/network` de FT00035).
 *
 * Son la **capa anti-corrupción** del cliente: replican el subconjunto del
 * contrato `POST /v1/chat` que consume el chat, con los mismos tokens
 * `snake_case`, y se mapean a los modelos de dominio de
 * `feature/chat/domain` ([ChatAnswer]). Se mantienen aquí (y no en `:shared`)
 * porque `:composeApp` no depende de `:shared` (ADR 0027) para no romper el gate
 * SDK-free. Cada propiedad lleva `@SerialName` explícito aunque el nombre Kotlin
 * coincida con la clave JSON (regla MUST de `CONSTRAINTS.md` §«Serialización»).
 */

/**
 * Petición de `POST /v1/chat`: la consulta en lenguaje natural.
 *
 * @property query pregunta o afirmación a verificar contra el corpus del BOE.
 */
@Serializable
data class ChatRequestDto(
    @SerialName("consulta")
    val query: String,
)

/**
 * Respuesta de `POST /v1/chat` (contrato de `docs/specs/api-chat.md`).
 *
 * [status] viaja con los tokens `respondida`/`refutada`/`sin_fundamento`;
 * `sin_fundamento` **no** es un error y lleva [citations] vacía.
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

/**
 * Cita de wire a la fuente oficial.
 *
 * @property publicationId identificador oficial de la publicación del BOE.
 * @property title título oficial de la publicación citada.
 * @property officialUrl URL oficial de la publicación (enlace a la fuente).
 */
@Serializable
data class ChatCitationDto(
    @SerialName("publicacionId")
    val publicationId: String,
    @SerialName("titulo")
    val title: String,
    @SerialName("urlOficial")
    val officialUrl: String,
)

/**
 * Estado de wire del chat RAG (espejo de `ChatStatusDto` de `:shared`).
 *
 * Cada valor declara su token `snake_case`; el mapper lo traduce al enum de
 * dominio `ChatStatus`.
 */
@Serializable
enum class ChatStatusDto {
    @SerialName("respondida")
    ANSWERED,

    @SerialName("refutada")
    REFUTED,

    @SerialName("sin_fundamento")
    UNFOUNDED,
}

/**
 * Proyecta la respuesta de wire al modelo de dominio.
 *
 * @return la respuesta de dominio lista para la UI.
 */
internal fun ChatResponseDto.toChatAnswer(): ChatAnswer = ChatAnswer(
    status = status.toDomain(),
    text = answer,
    citations = citations.map { citation -> citation.toDomain() },
)

/** Traduce el token de estado de wire al enum de dominio (exhaustivo). */
internal fun ChatStatusDto.toDomain(): ChatStatus = when (this) {
    ChatStatusDto.ANSWERED -> ChatStatus.ANSWERED
    ChatStatusDto.REFUTED -> ChatStatus.REFUTED
    ChatStatusDto.UNFOUNDED -> ChatStatus.UNFOUNDED
}

/** Proyecta una cita de wire al modelo de dominio. */
internal fun ChatCitationDto.toDomain(): ChatCitation = ChatCitation(
    publicationId = publicationId,
    title = title,
    officialUrl = officialUrl,
)
