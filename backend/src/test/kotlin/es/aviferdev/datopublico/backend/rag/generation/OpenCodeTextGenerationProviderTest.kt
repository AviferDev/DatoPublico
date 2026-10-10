package es.aviferdev.datopublico.backend.rag.generation

import es.aviferdev.datopublico.serialization.DatoPublicoJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tests de gate (sin red) de [OpenCodeTextGenerationProvider] con [MockEngine]:
 * cabecera `Bearer`, modelo y mensajes `system`/`user`, parseo de
 * `choices[0].message.content` y errores tipados (escenario 7 del spec).
 */
class OpenCodeTextGenerationProviderTest {

    private val config = OpenCodeConfig(
        apiKey = "clave-de-prueba",
        baseUrl = "https://opencode.test/v1",
        model = "modelo-de-prueba",
        temperature = 0.0,
    )

    private val request = GenerationRequest(systemPrompt = "sistema", userPrompt = "usuario")

    @Test
    fun `sends bearer model messages and parses the first choice content`() = runTest {
        var captured: HttpRequestData? = null
        val provider = providerCon(
            MockEngine { httpRequest ->
                captured = httpRequest
                respond(
                    content = RESPONSE_OK,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        )

        val response = provider.generate(request)

        val http = checkNotNull(captured)
        assertEquals(HttpMethod.Post, http.method)
        assertEquals("https://opencode.test/v1/chat/completions", http.url.toString())
        assertEquals("Bearer clave-de-prueba", http.headers[HttpHeaders.Authorization])
        val body = (http.body as TextContent).text
        val json = DatoPublicoJson.parseToJsonElement(body).jsonObject
        assertEquals("modelo-de-prueba", json.getValue("model").jsonPrimitive.content)
        val messages = json.getValue("messages").jsonArray
        assertEquals("system", messages[0].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("sistema", messages[0].jsonObject.getValue("content").jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("usuario", messages[1].jsonObject.getValue("content").jsonPrimitive.content)
        assertEquals("resumen generado", response.text)
    }

    @Test
    fun `a non 200 status throws TextGenerationException`() = runTest {
        val provider = providerCon(MockEngine { respondError(HttpStatusCode.TooManyRequests, "sin cuota") })

        val error = assertFailsWith<TextGenerationException> { provider.generate(request) }

        assertTrue(error.message.orEmpty().contains("429"), error.message.orEmpty())
    }

    @Test
    fun `a malformed body throws TextGenerationException`() = runTest {
        val provider = providerCon(
            MockEngine {
                respond(
                    content = "no es json",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        )

        assertFailsWith<TextGenerationException> { provider.generate(request) }
    }

    @Test
    fun `a response without choices throws TextGenerationException`() = runTest {
        val provider = providerCon(
            MockEngine {
                respond(
                    content = """{"choices":[]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        )

        assertFailsWith<TextGenerationException> { provider.generate(request) }
    }

    @Test
    fun `a network failure throws TextGenerationException`() = runTest {
        val provider = providerCon(MockEngine { throw IOException("sin conexión") })

        val error = assertFailsWith<TextGenerationException> { provider.generate(request) }

        assertTrue(error.cause is IOException)
    }

    private fun providerCon(engine: MockEngine): OpenCodeTextGenerationProvider = OpenCodeTextGenerationProvider(
        httpClient = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(DatoPublicoJson) }
        },
        config = config,
    )

    private companion object {
        const val RESPONSE_OK =
            """{"choices":[{"message":{"role":"assistant","content":"resumen generado"}}]}"""
    }
}
