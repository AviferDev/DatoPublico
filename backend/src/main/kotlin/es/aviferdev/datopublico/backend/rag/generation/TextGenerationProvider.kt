package es.aviferdev.datopublico.backend.rag.generation

/**
 * Proveedor intercambiable de **generación de texto** (resumen ciudadano y, más
 * adelante, chat).
 *
 * Es la frontera que consumirán FT00020 (resumen) y FT00025 (chat): el resto del
 * código no conoce OpenCode, Ktor ni HTTP. La implementación por defecto es
 * [OpenCodeTextGenerationProvider], pero el servicio de resumen se prueba con un
 * **doble** que implementa esta interfaz, sin abrir sockets.
 *
 * Contrato:
 * - [generate] es **suspend**: la generación es una llamada de red.
 * - El proveedor trabaja **solo** con el prompt recibido (que incluye el contexto
 *   recuperado); no tiene estado ni memoria propia.
 * - Los fallos de red, un estado HTTP distinto de `200` o un cuerpo ilegible se
 *   traducen a [TextGenerationException].
 */
interface TextGenerationProvider {
    /**
     * Genera texto a partir de [request] y devuelve la respuesta del proveedor.
     *
     * @param request prompt de sistema y de usuario ya construidos.
     * @return la respuesta del proveedor con su texto.
     * @throws TextGenerationException si la llamada falla o la respuesta no es
     *   interpretable.
     */
    suspend fun generate(request: GenerationRequest): GenerationResponse
}

/**
 * Petición **interna** de generación de texto (sin sufijo `Dto`: no es un tipo de
 * transporte ni `@Serializable`).
 *
 * @property systemPrompt instrucciones del sistema (rol, idioma, formato de salida
 *   y la regla de usar solo el contexto).
 * @property userPrompt contenido concreto: metadatos de la publicación y el
 *   contexto recuperado, delimitado como **datos**, no como instrucciones.
 */
data class GenerationRequest(
    val systemPrompt: String,
    val userPrompt: String,
)

/**
 * Respuesta **interna** del proveedor de texto (sin sufijo `Dto`).
 *
 * @property text texto plano devuelto por el modelo; su estructura la interpreta
 *   el parser del consumidor (p. ej. `SummaryOutputParser`).
 * @property usage consumo de tokens de la generación, si el proveedor lo aporta;
 *   `null` cuando la respuesta no trae bloque `usage` (no es un error). Lo
 *   consume el control de coste (FT00021).
 */
data class GenerationResponse(val text: String, val usage: TokenUsage? = null)

/**
 * Consumo de **tokens** de una generación (valor interno, sin sufijo: no es un
 * DTO de transporte ni `@Serializable`).
 *
 * Los tres valores vienen del bloque `usage` del proveedor (OpenAI-compatible:
 * `prompt_tokens`/`completion_tokens`/`total_tokens`). Se registran tal cual, sin
 * inventar consumo cuando el proveedor no lo aporta.
 *
 * @property promptTokens tokens de entrada (prompt).
 * @property completionTokens tokens de salida (completion).
 * @property totalTokens tokens totales del intercambio.
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
)

/**
 * Error de frontera del proveedor de generación de texto.
 *
 * Es **tipado** para que quien lo consuma distinga el fallo del proveedor (red,
 * estado HTTP o cuerpo ilegible) del de validación del contrato del resumen.
 */
class TextGenerationException(message: String, cause: Throwable? = null) : Exception(message, cause)
