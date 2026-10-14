package es.aviferdev.datopublico.feature.feed.data.remote

import es.aviferdev.datopublico.core.network.ApiConfig
import es.aviferdev.datopublico.core.network.ApiFailure
import es.aviferdev.datopublico.core.network.ApiResult
import es.aviferdev.datopublico.core.network.configureDatoPublicoClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests del cliente del feed (FT00035) con [MockEngine] (sin red): `200` que
 * decodifica el cuerpo y viaja la fecha en la query, errores de servidor tipados
 * (`400`/`404`/`429`/`500`/`502`), cuerpo inválido y excepción del motor.
 */
class FeedApiTest {

    private val config = ApiConfig(baseUrl = "http://api.test", requestTimeoutMillis = 1_000)

    @Test
    fun okResponseDecodesTheFeedAndSendsTheDate() = runTest {
        var captured: HttpRequestData? = null
        val api = apiWith(
            MockEngine { request ->
                captured = request
                respondJson(FEED_JSON, HttpStatusCode.OK)
            }
        )

        val result = api.dailyFeed("2026-10-13")

        val success = assertIs<ApiResult.Success<FeedResponseDto>>(result)
        assertEquals("2026-10-13", success.data.date)
        assertEquals(FeedCategoryDto.NORMATIVE_REGULATION, success.data.publications.first().category)
        assertEquals(
            listOf(FeedHighlightReasonDto.NORMATIVE_RANK),
            success.data.highlights.first().reasons,
        )
        assertEquals("http://api.test/v1/feed?fecha=2026-10-13", checkNotNull(captured).url.toString())
    }

    @Test
    fun serverErrorsBecomeTypedServerFailures() = runTest {
        val cases = listOf(
            HttpStatusCode.BadRequest to "solicitud_invalida",
            HttpStatusCode.NotFound to "no_encontrado",
            HttpStatusCode.TooManyRequests to "demasiadas_peticiones",
            HttpStatusCode.InternalServerError to "error_interno",
            HttpStatusCode.BadGateway to "servicio_no_disponible",
        )

        cases.forEach { (status, code) ->
            val api = apiWith(MockEngine { respondJson(errorJson(code), status) })

            val result = api.dailyFeed("2026-10-13")

            val failure = assertIs<ApiResult.Failure>(result)
            assertEquals(ApiFailure.Server(status.value, code), failure.reason)
        }
    }

    @Test
    fun serverErrorWithoutTypedBodyKeepsStatusAndNullCode() = runTest {
        val api = apiWith(MockEngine { respond(content = "", status = HttpStatusCode.BadGateway) })

        val result = api.dailyFeed("2026-10-13")

        val failure = assertIs<ApiResult.Failure>(result)
        assertEquals(ApiFailure.Server(502, null), failure.reason)
    }

    @Test
    fun invalidJsonBecomesInvalidBody() = runTest {
        val api = apiWith(MockEngine { respondJson("{ no es json", HttpStatusCode.OK) })

        val result = api.dailyFeed("2026-10-13")

        val failure = assertIs<ApiResult.Failure>(result)
        assertEquals(ApiFailure.InvalidBody, failure.reason)
    }

    @Test
    fun engineExceptionBecomesNetworkFailure() = runTest {
        val api = apiWith(MockEngine { throw IllegalStateException("sin conexión") })

        val result = api.dailyFeed("2026-10-13")

        val failure = assertIs<ApiResult.Failure>(result)
        assertEquals(ApiFailure.Network, failure.reason)
    }

    /** Construye la API con un [MockEngine] y la misma configuración de cliente. */
    private fun apiWith(engine: MockEngine): FeedApi =
        FeedApi(HttpClient(engine) { configureDatoPublicoClient(config) }, config)

    /** Responde [body] como JSON con el estado [status]. */
    private fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode) =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    /** Cuerpo de error tipado del contrato para el token [code]. */
    private fun errorJson(code: String): String = """{"codigo":"$code","mensaje":"mensaje genérico"}"""

    private companion object {
        /** Respuesta `200` de ejemplo del contrato `GET /v1/feed`. */
        const val FEED_JSON = """
        {
          "fecha": "2026-10-13",
          "publicaciones": [
            {
              "id": "BOE-A-2026-21001",
              "titulo": "Norma de prueba",
              "fechaPublicacion": "2026-10-13",
              "categoria": "normas_y_legislacion",
              "urlOficial": "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21001",
              "resumen": {"queCambia": "Resumen corto de prueba."},
              "plazo": null
            }
          ],
          "destacados": [
            {
              "publicacion": {
                "id": "BOE-A-2026-21001",
                "titulo": "Norma de prueba",
                "fechaPublicacion": "2026-10-13",
                "categoria": "normas_y_legislacion",
                "urlOficial": "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21001"
              },
              "motivos": ["rango_normativo"]
            }
          ],
          "pagina": 1,
          "tamanoPagina": 20,
          "total": 1,
          "totalPaginas": 1
        }
        """
    }
}
