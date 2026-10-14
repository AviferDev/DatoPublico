package es.aviferdev.datopublico.feature.search.data

import es.aviferdev.datopublico.core.network.ApiConfig
import es.aviferdev.datopublico.core.network.configureDatoPublicoClient
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory
import es.aviferdev.datopublico.feature.search.data.remote.SearchApi
import es.aviferdev.datopublico.feature.search.domain.SearchFilters
import es.aviferdev.datopublico.feature.search.domain.SearchLoadResult
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
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * Tests del repositorio HTTP de la búsqueda (FT00036): el éxito mapea los DTOs a
 * dominio (consulta, categoría, plazo y sin destacado), el tope inyectado viaja en
 * la query y los fallos de red/servidor devuelven un fallo tipado.
 *
 * Usan [MockEngine] (sin red).
 */
class HttpSearchRepositoryTest {

    private val config = ApiConfig(baseUrl = "http://api.test", requestTimeoutMillis = 1_000)

    @Test
    fun successMapsTheResultsToDomain() = runTest {
        val repository = repositoryWith(MockEngine { respondJson(RESULTS_JSON, HttpStatusCode.OK) })

        val result = repository.search("becas", SearchFilters())

        val success = assertIs<SearchLoadResult.Success>(result)
        assertEquals("becas", success.results.query)
        val publication = success.results.publications.single()
        assertEquals("BOE-A-2026-31001", publication.id)
        assertEquals(PublicationCategory.GRANTS_AND_SUBSIDIES, publication.category)
        assertEquals("2026-10-20", publication.deadline?.limitDate)
        assertFalse(publication.isHighlighted)
    }

    @Test
    fun injectedLimitTravelsInTheQuery() = runTest {
        var captured: HttpRequestData? = null
        val repository = repositoryWith(
            engine = MockEngine { request ->
                captured = request
                respondJson(RESULTS_JSON, HttpStatusCode.OK)
            },
            limit = 5,
        )

        repository.search("becas", SearchFilters())

        assertEquals(
            "http://api.test/v1/buscar?q=becas&limite=5",
            checkNotNull(captured).url.toString(),
        )
    }

    @Test
    fun networkFailureReturnsFailure() = runTest {
        val repository = repositoryWith(MockEngine { throw IllegalStateException("sin conexión") })

        val result = repository.search("becas", SearchFilters())

        assertEquals(SearchLoadResult.Failure, result)
    }

    @Test
    fun serverErrorReturnsFailure() = runTest {
        val repository = repositoryWith(
            MockEngine {
                respondJson("""{"codigo":"error_interno","mensaje":"fallo"}""", HttpStatusCode.InternalServerError)
            },
        )

        val result = repository.search("becas", SearchFilters())

        assertEquals(SearchLoadResult.Failure, result)
    }

    /** Repositorio con un [MockEngine] y un tope inyectable. */
    private fun repositoryWith(engine: MockEngine, limit: Int = 10): HttpSearchRepository {
        val api = SearchApi(HttpClient(engine) { configureDatoPublicoClient(config) }, config)
        return HttpSearchRepository(searchApi = api, limit = limit)
    }

    /** Responde [body] como JSON con el estado [status]. */
    private fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode) =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    private companion object {
        /** Respuesta `200` mínima del contrato `GET /v1/buscar`. */
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
