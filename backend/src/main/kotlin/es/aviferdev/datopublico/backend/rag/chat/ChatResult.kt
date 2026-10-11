package es.aviferdev.datopublico.backend.rag.chat

import es.aviferdev.datopublico.backend.rag.generation.TokenUsage

/**
 * Estado de **dominio** de una respuesta del chat RAG (FT00025).
 *
 * Es un valor **interno** (sin sufijo, **no** `@Serializable`): no es un tipo de
 * transporte. La UI y la API del chat (FT00037/FT00031) definirán su propio wire a
 * partir de los tres estados del dominio (`respondida`, `refutada`,
 * `sin_fundamento`); el parser ([ChatOutputParser.statusOf]) traduce los tokens de
 * la fuente a este enum.
 */
enum class ChatStatus {
    /** La pregunta se responde con el corpus recuperado. */
    ANSWERED,

    /** La afirmación se refuta con el corpus recuperado. */
    REFUTED,

    /** El corpus no permite confirmar ni refutar; no se inventa respuesta. */
    UNFOUNDED,
}

/**
 * Cita a la **fuente oficial** de una publicación del BOE usada como sustento.
 *
 * Es un valor **interno** (sin sufijo: no es un DTO de transporte). La
 * [officialUrl] **no** la produce el modelo: la inyecta de forma determinista el
 * [CitationResolver] a partir de la publicación de origen del fragmento citado
 * (mismo principio que la fuente inyectada del resumen, FT00020).
 *
 * @property publicationId identificador oficial de la publicación del BOE.
 * @property title título oficial de la publicación citada.
 * @property officialUrl URL oficial de la publicación (enlace a la fuente).
 */
data class ChatCitation(
    val publicationId: String,
    val title: String,
    val officialUrl: String,
)

/**
 * Resultado **interno** de responder una consulta de chat (sin sufijo: no es un
 * DTO de transporte).
 *
 * Modela los tres desenlaces del dominio (`docs/domain-model.md` §Consulta):
 * [Answered] (pregunta respondida), [Refuted] (afirmación refutada) y [Unfounded]
 * (sin fundamento). Una respuesta citada ([Answered]/[Refuted]) **siempre** lleva
 * al menos una [ChatCitation] resoluble a la fuente oficial; un
 * [Unfounded] **nunca** lleva citas.
 *
 * @property answer texto de la respuesta; en [Unfounded] es el mensaje del modelo
 *   (si declaró `sin_fundamento`) o el mensaje de dominio [RagChatService.UNFOUNDED_MESSAGE]
 *   cuando el corpus no permite publicar la afirmación.
 * @property citations citas a la fuente oficial; vacía en [Unfounded].
 * @property usage consumo de tokens del proveedor, si lo aportó (control de coste
 *   futuro, FT00030/FT00031); `null` cuando el proveedor no devolvió bloque
 *   `usage`.
 */
sealed interface ChatResult {
    /** Texto de la respuesta. */
    val answer: String

    /** Citas a la fuente oficial (vacía en [Unfounded]). */
    val citations: List<ChatCitation>

    /** Consumo de tokens del proveedor, si lo aportó. */
    val usage: TokenUsage?

    /**
     * Pregunta **respondida** con el contexto recuperado.
     *
     * @property answer texto de la respuesta.
     * @property citations citas a la fuente oficial (no vacía).
     * @property usage consumo de tokens, si lo aportó.
     */
    data class Answered(
        override val answer: String,
        override val citations: List<ChatCitation>,
        override val usage: TokenUsage? = null,
    ) : ChatResult

    /**
     * Afirmación **refutada** con el contexto recuperado.
     *
     * @property answer texto de la refutación.
     * @property citations citas a la fuente oficial (no vacía).
     * @property usage consumo de tokens, si lo aportó.
     */
    data class Refuted(
        override val answer: String,
        override val citations: List<ChatCitation>,
        override val usage: TokenUsage? = null,
    ) : ChatResult

    /**
     * Consulta **sin fundamento**: el corpus no permite confirmar ni refutar.
     *
     * No lleva citas (no se inventa una respuesta) y **no** publica una afirmación
     * fuera del corpus.
     *
     * @property answer mensaje de la respuesta.
     * @property usage consumo de tokens, si lo aportó.
     */
    data class Unfounded(
        override val answer: String,
        override val usage: TokenUsage? = null,
    ) : ChatResult {
        override val citations: List<ChatCitation> = emptyList()
    }
}

/**
 * Error de frontera del chat RAG: la salida del proveedor no es interpretable
 * (vacía, sin JSON válido), el estado es desconocido o el modelo citó un índice de
 * fragmento **fuera de rango**.
 *
 * Es **tipado** y distinto de un resultado de dominio esperado ([ChatResult]): un
 * `sin_fundamento` o una degradación por grounding son resultados, no errores; una
 * salida no interpretable es un fallo de programación/frontera.
 */
class ChatGenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)
