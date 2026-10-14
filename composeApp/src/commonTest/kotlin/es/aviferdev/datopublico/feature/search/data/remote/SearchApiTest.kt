package es.aviferdev.datopublico.feature.search.data.remote

import es.aviferdev.datopublico.core.network.ApiConfig
import es.aviferdev.datopublico.core.network.ApiFailure
import es.aviferdev.datopublico.core.network.ApiResult
import es.aviferdev.datopublico.core.network.configureDatoPublicoClient
import es.aviferdev.datopublico.feature.feed.data.remote.FeedCategoryDto
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory
import es.aviferdev.datopublico.feature.search.domain.SearchFilters
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
 * Tests del cliente de la búsqueda (FT00036) con [MockEngine] (sin red): `200` que
 * decodifica el cuerpo y envía la consulta, la categoría como token de wire y el
 * rango de fechas; la omisión de los filtros ausentes; los errores de servidor
 * tipados; el cuerpo inválido y la excepción del motor.
 */
class SearchApiTest {

    private val config = ApiConfig(baseUrl = "http://api.test", requestTimeoutMillis = 1_000)

    @Test
    fun okResponseDecodesResultsAndSendsEveryParameter() = runTest {
        var captured: HttpRequestData? = null
        val api = apiWith(
            MockEngine { request ->
                captured = request
                respondJson(RESULTS_JSON, HttpStatusCode.OK)
            },
        )
        val filters = SearchFilters(
            category = PublicationCategory.GRANTS_AND_SUBSIDIES,
            fromDate = "2026-01-01",
            toDate = "2026-12-31",
        )

        val result = api.search(query = "becas", filters = filters, limit = 10)

        val success = assertIs<ApiResult.Success<SearchResponseDto>>(result)
        assertEquals("becas", success.data.query)
        assertEquals(FeedCategoryDto.GRANTS_AND_SUBSIDIES, success.data.results.first().category)
        assertEquals(
            "http://api.test/v1/buscar" +
                "?q=becas" +
                "&categoria=becas_subvenciones_y_ayudas" +
                "&desde=2026-01-01" +
                "&hasta=2026-12-31" +
                "&limite=10",
            checkNotNull(captured).url.toString(),
        )
    }

    @Test
    fun absentFiltersDoNotTravelInTheQuery() = runTest {
        var captured: HttpRequestData? = null
        val api = apiWith(
            MockEngine { request ->
                captured = request
                respondJson(RESULTS_JSON, HttpStatusCode.OK)
            },
        )

        api.search(query = "becas", filters = SearchFilters(), limit = 10)

        assertEquals("http://api.test/v1/buscar?q=becas&limite=10", checkNotNull(captured).url.toString())
    }

    @Test
    fun serverErrorBecomesTypedServerFailure() = runTest {
        val api = apiWith(
            MockEngine {
                respondJson("""{"codigo":"solicitud_invalida","mensaje":"consulta inválida"}""", HttpStatusCode.BadRequest)
            },
        )

        val result = api.search(query = "becas", filters = SearchFilters(), limit = 10)

        val failure = assertIs<ApiResult.Failure>(result)
        assertEquals(ApiFailure.Server(400, "solicitud_invalida"), failure.reason)
    }

    @Test
    fun invalidJsonBecomesInvalidBody() = runTest {
        val api = apiWith(MockEngine { respondJson("{ no es json", HttpStatusCode.OK) })

        val result = api.search(query = "becas", filters = SearchFilters(), limit = 10)

        val failure = assertIs<ApiResult.Failure>(result)
        assertEquals(ApiFailure.InvalidBody, failure.reason)
    }

    @Test
    fun engineExceptionBecomesNetworkFailure() = runTest {
        val api = apiWith(MockEngine { throw IllegalStateException("sin conexión") })

        val result = api.search(query = "becas", filters = SearchFilters(), limit = 10)

        val failure = assertIs<ApiResult.Failure>(result)
        assertEquals(ApiFailure.Network, failure.reason)
    }

    /** Construye la API con un [MockEngine] y la misma configuración de cliente. */
    private fun apiWith(engine: MockEngine): SearchApi =
        SearchApi(HttpClient(engine) { configureDatoPublicoClient(config) }, config)

    /** Responde [body] como JSON con el estado [status]. */
    private fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode) =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    private companion object {
        /** Respuesta `200` de ejemplo del contrato `GET /v1/buscar`. */
        const val RESULTS_JSON = """
        {
          "q": "becas",
          "resultados": [
            {
              "id": "BOE-A-2026-31001",
              "titulo": "Becas para jóvenes investigadores",
              "fechaPublicacion": "2026-09-20",
              "categoria": "becas_subvenciones_y_ayudas",
              "urlOficial": "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-31001",
              "resumen": {"queCambia": "Becas de movilidad para jóvenes investigadores."},
              "plazo": {"fechaLimite": "2026-10-20"}
            }
          ]
        }
        """
    }
}
