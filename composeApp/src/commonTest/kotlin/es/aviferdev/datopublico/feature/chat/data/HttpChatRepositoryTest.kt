package es.aviferdev.datopublico.feature.chat.data

import es.aviferdev.datopublico.core.network.ApiConfig
import es.aviferdev.datopublico.core.network.configureDatoPublicoClient
import es.aviferdev.datopublico.feature.chat.data.remote.ChatApi
import es.aviferdev.datopublico.feature.chat.domain.ChatLoadResult
import es.aviferdev.datopublico.feature.chat.domain.ChatStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests del repositorio HTTP del chat (FT00037) con [MockEngine] (sin red): el
 * `200` mapea el DTO a dominio (con y sin citas), el `POST` viaja a `/v1/chat`
 * con la consulta en el cuerpo, y los errores (servidor, red y cuerpo inválido)
 * se traducen a `Failure`.
 */
class HttpChatRepositoryTest {

    private val config = ApiConfig(baseUrl = "http://api.test", requestTimeoutMillis = 1_000)

    @Test
    fun answeredResponseIsMappedToDomain() = runTest {
        val repository = repositoryWith(MockEngine { respondJson(ANSWERED_JSON, HttpStatusCode.OK) })

        val result = repository.ask("plazo")

        val success = assertIs<ChatLoadResult.Success>(result)
        assertEquals(ChatStatus.ANSWERED, success.answer.status)
        assertEquals(1, success.answer.citations.size)
        assertEquals("BOE-A-2026-21002", success.answer.citations.first().publicationId)
    }

    @Test
    fun unfoundedResponseHasNoCitations() = runTest {
        val repository = repositoryWith(MockEngine { respondJson(UNFOUNDED_JSON, HttpStatusCode.OK) })

        val result = repository.ask("clima")

        val success = assertIs<ChatLoadResult.Success>(result)
        assertEquals(ChatStatus.UNFOUNDED, success.answer.status)
        assertTrue(success.answer.citations.isEmpty())
    }

    @Test
    fun serverErrorReturnsFailure() = runTest {
        val repository = repositoryWith(
            MockEngine { respondJson("""{"codigo":"error_interno","mensaje":"fallo"}""", HttpStatusCode.InternalServerError) },
        )

        assertEquals(ChatLoadResult.Failure, repository.ask("plazo"))
    }

    @Test
    fun networkErrorReturnsFailure() = runTest {
        val repository = repositoryWith(MockEngine { throw IllegalStateException("sin conexión") })

        assertEquals(ChatLoadResult.Failure, repository.ask("plazo"))
    }

    @Test
    fun invalidBodyReturnsFailure() = runTest {
        val repository = repositoryWith(MockEngine { respondJson("no es json", HttpStatusCode.OK) })

        assertEquals(ChatLoadResult.Failure, repository.ask("plazo"))
    }

    @Test
    fun queryTravelsAsPostToTheChatPath() = runTest {
        var captured: HttpRequestData? = null
        val repository = repositoryWith(
            MockEngine { request ->
                captured = request
                respondJson(ANSWERED_JSON, HttpStatusCode.OK)
            },
        )

        repository.ask("¿Cuál es el plazo?")

        val request = checkNotNull(captured)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://api.test/v1/chat", request.url.toString())
        assertTrue((request.body as TextContent).text.contains("consulta"))
    }

    /** Repositorio con un [MockEngine] y la configuración de cliente común. */
    private fun repositoryWith(engine: MockEngine): HttpChatRepository {
        val api = ChatApi(HttpClient(engine) { configureDatoPublicoClient(config) }, config)
        return HttpChatRepository(chatApi = api)
    }

    /** Responde [body] como JSON con el estado [status]. */
    private fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode) =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    private companion object {
        /** Respuesta `200` con estado `respondida` y una cita. */
        const val ANSWERED_JSON = """
        {
          "consulta": "plazo",
          "estado": "respondida",
          "respuesta": "La convocatoria ofrece 1.250 plazas de acceso libre.",
          "citas": [
            {
              "publicacionId": "BOE-A-2026-21002",
              "titulo": "Convocatoria de empleo público",
              "urlOficial": "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21002"
            }
          ]
        }
        """

        /** Respuesta `200` con estado `sin_fundamento` y sin citas. */
        const val UNFOUNDED_JSON = """
        {
          "consulta": "clima",
          "estado": "sin_fundamento",
          "respuesta": "El BOE no contiene información que permita confirmar ni refutar.",
          "citas": []
        }
        """
    }
}
