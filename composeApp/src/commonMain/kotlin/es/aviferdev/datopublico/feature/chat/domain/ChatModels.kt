package es.aviferdev.datopublico.feature.chat.domain

/**
 * Modelos de **dominio** del chat RAG en el cliente.
 *
 * Son modelos propios del cliente (no DTOs de red): el mapeo desde el contrato de
 * `:shared` (`ChatResponseDto`/`ChatStatusDto`/`ChatCitationDto` → [ChatAnswer])
 * lo realiza la capa de datos ([es.aviferdev.datopublico.feature.chat.data]). Los
 * identificadores van en inglés; el KDoc, en castellano.
 *
 * El chat es **un solo intercambio** efímero (pregunta + respuesta): no hay
 * historial, ni en el dispositivo ni en el servidor (`docs/domain-model.md`
 * §Consulta).
 */

/**
 * Estado del dominio de una respuesta del chat RAG.
 *
 * Espeja el enum de wire `ChatStatusDto` (tokens `respondida`/`refutada`/
 * `sin_fundamento`). El **texto visible** se resuelve por recurso i18n, no por el
 * nombre del enum.
 */
enum class ChatStatus {
    /** La pregunta se responde con el corpus recuperado. */
    ANSWERED,

    /** La afirmación se refuta con el corpus recuperado. */
    REFUTED,

    /** El corpus no permite confirmar ni refutar; **no** es un error. */
    UNFOUNDED,
}

/**
 * Cita a la **fuente oficial** del BOE que sustenta una respuesta.
 *
 * @property publicationId identificador oficial de la publicación citada.
 * @property title título oficial de la publicación.
 * @property officialUrl URL oficial de la publicación (enlace a la fuente).
 */
data class ChatCitation(
    val publicationId: String,
    val title: String,
    val officialUrl: String,
)

/**
 * Respuesta del chat RAG lista para la UI.
 *
 * Invariante del contrato: [ChatStatus.UNFOUNDED] lleva [citations] **vacía**;
 * [ChatStatus.ANSWERED]/[ChatStatus.REFUTED] llevan **al menos** una cita.
 *
 * @property status estado del dominio de la respuesta.
 * @property text texto de la respuesta para la ciudadanía.
 * @property citations citas a la fuente oficial; vacía en `sin_fundamento`.
 */
data class ChatAnswer(
    val status: ChatStatus,
    val text: String,
    val citations: List<ChatCitation>,
)

/**
 * Resultado **tipado** de pedir una respuesta al chat: un fallo es un valor
 * esperado ([Failure]), nunca una excepción, para que la UI lo muestre como
 * estado gestionado y la app nunca se cierre.
 */
sealed interface ChatLoadResult {
    /** El repositorio devolvió una respuesta fundamentada. */
    data class Success(val answer: ChatAnswer) : ChatLoadResult

    /** El repositorio no pudo devolver la respuesta. */
    data object Failure : ChatLoadResult
}
