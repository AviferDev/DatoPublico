package es.aviferdev.datopublico.backend.rag.generation

import es.aviferdev.datopublico.serialization.DatoPublicoJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

/**
 * Implementación Ktor de [TextGenerationProvider] contra la API
 * **OpenAI-compatible** de OpenCode, con `HttpClient` **inyectado**.
 *
 * Envía `POST {baseUrl}/chat/completions` con `Authorization: Bearer <apiKey>`, un
 * cuerpo OpenAI-compatible (`model`, `messages` con `system`/`user` y
 * `temperature`) y lee el texto de `choices[0].message.content`. La clave vive
 * **solo** en el servidor ([OpenCodeConfig.apiKey]) y nunca se registra.
 *
 * Cualquier fallo de red, un estado distinto de `200` o un cuerpo ilegible o sin
 * contenido se traduce a [TextGenerationException].
 *
 * @param httpClient cliente HTTP (CIO en producción; `MockEngine` en los tests).
 * @param config configuración resuelta del entorno (clave, base, modelo y
 *   temperatura).
 */
class OpenCodeTextGenerationProvider internal constructor(
    private val httpClient: HttpClient,
    private val config: OpenCodeConfig,
) : TextGenerationProvider {

    override suspend fun generate(request: GenerationRequest): GenerationResponse {
        val response = execute(request)
        return decode(response)
    }

    /** Ejecuta la petición HTTP y traduce los fallos de red a error tipado. */
    private suspend fun execute(request: GenerationRequest): HttpResponse = try {
        httpClient.post(endpoint()) {
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            setBody(toWire(request))
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        throw TextGenerationException("No se pudo invocar el proveedor de generación OpenCode.", error)
    }

    /** Valida el estado HTTP y parsea el cuerpo como respuesta de chat. */
    private suspend fun decode(response: HttpResponse): GenerationResponse {
        if (response.status != HttpStatusCode.OK) {
            throw TextGenerationException(
                "El proveedor de generación OpenCode respondió ${response.status.value}."
            )
        }
        val body = decodeBody(response)
        return GenerationResponse(body.firstContent())
    }

    /** Lee el cuerpo como DTO de respuesta; un cuerpo ilegible es error tipado. */
    private suspend fun decodeBody(response: HttpResponse): ChatCompletionResponseDto = try {
        response.body()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        throw TextGenerationException("Respuesta ilegible del proveedor de generación OpenCode.", error)
    }

    /** URL absoluta del endpoint de chat. */
    private fun endpoint(): String = "${config.baseUrl.trimEnd('/')}/chat/completions"

    /** Cuerpo OpenAI-compatible de la petición con el prompt de sistema y de usuario. */
    private fun toWire(request: GenerationRequest): ChatCompletionRequestDto = ChatCompletionRequestDto(
        model = config.model,
        messages = listOf(
            ChatMessageDto(role = ROLE_SYSTEM, content = request.systemPrompt),
            ChatMessageDto(role = ROLE_USER, content = request.userPrompt),
        ),
        temperature = config.temperature,
    )

    private companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
    }
}

/** Extrae el texto de la primera opción; sin opciones ni contenido es error tipado. */
private fun ChatCompletionResponseDto.firstContent(): String {
    val content = choices.firstOrNull()?.message?.content
    return content?.takeIf { it.isNotBlank() }
        ?: throw TextGenerationException("El proveedor de generación OpenCode no devolvió contenido.")
}

/**
 * Crea el [HttpClient] por defecto del proveedor OpenCode: motor **CIO**,
 * `ContentNegotiation` con [DatoPublicoJson] y **timeouts explícitos** (la
 * generación no debe colgarse indefinidamente). `expectSuccess = false` para
 * traducir a mano los estados distintos de `200`.
 */
fun openCodeHttpClient(): HttpClient = HttpClient(CIO) {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = HTTP_REQUEST_TIMEOUT_MS
        socketTimeoutMillis = HTTP_SOCKET_TIMEOUT_MS
        connectTimeoutMillis = HTTP_CONNECT_TIMEOUT_MS
    }
    install(ContentNegotiation) {
        json(DatoPublicoJson)
    }
}

/** Mensaje de la conversación OpenAI-compatible (`system`, `user` o `assistant`). */
@Serializable
private data class ChatMessageDto(
    @SerialName("role") val role: String,
    @SerialName("content") val content: String,
)

/** Petición de `chat/completions` (subconjunto OpenAI-compatible). */
@Serializable
private data class ChatCompletionRequestDto(
    @SerialName("model") val model: String,
    @SerialName("messages") val messages: List<ChatMessageDto>,
    @SerialName("temperature") val temperature: Double,
)

/** Opción de la respuesta de chat; solo interesa su [message]. */
@Serializable
private data class ChatChoiceDto(
    @SerialName("message") val message: ChatMessageDto,
)

/** Respuesta de `chat/completions`; las claves desconocidas se ignoran. */
@Serializable
private data class ChatCompletionResponseDto(
    @SerialName("choices") val choices: List<ChatChoiceDto>,
)

/** Tiempo máximo por petición de generación (ms). */
private const val HTTP_REQUEST_TIMEOUT_MS = 60_000L

/** Tiempo máximo de inactividad del socket de generación (ms). */
private const val HTTP_SOCKET_TIMEOUT_MS = 60_000L

/** Tiempo máximo para establecer la conexión con OpenCode (ms). */
private const val HTTP_CONNECT_TIMEOUT_MS = 10_000L
