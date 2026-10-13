package es.aviferdev.datopublico.backend.chat

import es.aviferdev.datopublico.backend.rag.chat.RagChatService
import es.aviferdev.datopublico.model.ChatResponseDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Servicio de aplicación del **chat RAG** (FT00031).
 *
 * Compone la librería `rag/chat` ([RagChatService], FT00025) —que recupera el
 * contexto, invoca al proveedor de texto, verifica el grounding e inyecta la
 * fuente oficial— y mapea su resultado al contrato de `:shared`. No conoce Ktor:
 * la ruta delgada (`plugins/Chat.kt`) recibe el cuerpo, valida y traduce los
 * fallos a `400`/`502`/`500`.
 *
 * La recuperación ONNX/JDBC y la resolución de citas JDBC son **bloqueantes**:
 * se ejecutan dentro de [Dispatchers.IO], fuera del event loop. El contexto se
 * acota al `limit` por defecto de [RagChatService] (no controlable por el
 * cliente) y la consulta con [MAX_QUERY_LENGTH]. **No** hay historial ni caché, y
 * el `usage` no se expone.
 *
 * @param ragChatService servicio de generación del chat RAG (FT00025).
 */
class ChatService(
    private val ragChatService: RagChatService,
) {

    /**
     * Responde [query] fundamentándose en el corpus del BOE.
     *
     * @param query pregunta o afirmación en lenguaje natural.
     * @return la respuesta de wire con su estado y sus citas.
     * @throws IllegalArgumentException si [query] está en blanco o supera
     *   [MAX_QUERY_LENGTH] (fail-fast **antes** de recuperar o invocar al
     *   proveedor; la ruta lo traduce a `400`).
     * @throws es.aviferdev.datopublico.backend.rag.generation.TextGenerationException
     *   si el proveedor de texto falla (la ruta responde `502`).
     * @throws es.aviferdev.datopublico.backend.rag.chat.ChatGenerationException
     *   si la salida del modelo no es interpretable (la ruta responde `502`).
     * @throws es.aviferdev.datopublico.backend.rag.embeddings.EmbeddingException
     *   si falla la inferencia local (la ruta responde `500`).
     */
    suspend fun ask(query: String): ChatResponseDto {
        require(query.isNotBlank()) { "La consulta del chat no puede estar en blanco." }
        require(query.length <= MAX_QUERY_LENGTH) {
            "La consulta del chat supera el máximo de $MAX_QUERY_LENGTH caracteres."
        }
        return withContext(Dispatchers.IO) {
            ChatDtoMapper.toResponseDto(query, ragChatService.answer(query))
        }
    }

    companion object {
        /**
         * Longitud máxima aceptada de la consulta (anti-abuso y control de coste).
         *
         * Acota la consulta para no inflar el prompt ni el consumo de tokens; es
         * una constante documentada y ajustable sin cambiar firmas. La ruta
         * responde `400` si se supera.
         */
        const val MAX_QUERY_LENGTH = 1_000
    }
}
