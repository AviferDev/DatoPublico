package es.aviferdev.datopublico.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cita a la **fuente oficial** de una publicación del BOE usada como sustento de
 * una respuesta del chat RAG (FT00031).
 *
 * La [officialUrl] **no** la produce el modelo: el servidor la **inyecta** de
 * forma determinista desde `Publicacion.urlOficial` (el modelo solo aporta
 * índices de fragmento), de modo que el cliente puede saltar siempre al enlace
 * oficial. Cada propiedad lleva `@SerialName` explícito (regla MUST de
 * `CONSTRAINTS.md` §«Serialización»).
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
