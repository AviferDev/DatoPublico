package es.aviferdev.datopublico.feature.detail.data

import es.aviferdev.datopublico.core.network.ApiConfig
import es.aviferdev.datopublico.core.network.configureDatoPublicoClient
import es.aviferdev.datopublico.feature.detail.data.remote.DetailApi
import es.aviferdev.datopublico.feature.detail.domain.DetailLoadResult
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory
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
import kotlin.test.assertTrue

/**
 * Tests del repositorio HTTP del detalle (FT00035) con [MockEngine] (sin red): el
 * `200` mapea el DTO al dominio; el `404` es «no encontrada»; otros estados y los
 * fallos de red son `Failure`; y el `id` viaja en la ruta.
 */
class HttpDetailRepositoryTest {

    private val config = ApiConfig(baseUrl = "http://api.test", requestTimeoutMillis = 1_000)

    @Test
    fun successfulLoadMapsTheDetail() = runTest {
        val repository = repositoryWith(MockEngine { respondJson(DETAIL_JSON, HttpStatusCode.OK) })

        val result = repository.load("BOE-A-2026-21321")

        val success = assertIs<DetailLoadResult.Success>(result)
        val detail = success.detail
        assertEquals("BOE-A-2026-21321", detail.id)
        assertEquals("Resolución de prueba", detail.title)
        assertEquals(PublicationCategory.APPOINTMENTS_AND_DISMISSALS, detail.category)
        assertEquals("2026-10-14", detail.publishedDate)
        assertEquals("Se resuelve la convocatoria.", detail.summary.whatChanges)
        assertEquals("A la persona adjudicataria.", detail.summary.whoIsAffected)
        assertEquals(listOf("1 puesto", "Nivel 22"), detail.summary.keyFigures)
        assertEquals("2026-10-30", detail.deadline?.limitDate)
        assertEquals("https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21321", detail.officialSourceUrl)
        assertTrue(detail.showsAiNotice)
    }

    @Test
    fun aNotFoundResponseIsReportedAsNotFound() = runTest {
        val repository = repositoryWith(
            MockEngine { respondJson("""{"codigo":"no_encontrado","mensaje":"no"}""", HttpStatusCode.NotFound) },
        )

        val result = repository.load("BOE-A-2026-00000")

        assertEquals(DetailLoadResult.NotFound, result)
    }

    @Test
    fun aServerErrorIsReportedAsFailure() = runTest {
        val repository = repositoryWith(
            MockEngine { respondJson("""{"codigo":"error_interno","mensaje":"fallo"}""", HttpStatusCode.InternalServerError) },
        )

        val result = repository.load("BOE-A-2026-21321")

        assertEquals(DetailLoadResult.Failure, result)
    }

    @Test
    fun aNetworkFailureIsReportedAsFailure() = runTest {
        val repository = repositoryWith(MockEngine { throw IllegalStateException("sin conexión") })

        val result = repository.load("BOE-A-2026-21321")

        assertEquals(DetailLoadResult.Failure, result)
    }

    @Test
    fun theIdTravelsInThePath() = runTest {
        var captured: HttpRequestData? = null
        val repository = repositoryWith(
            MockEngine { request ->
                captured = request
                respondJson(DETAIL_JSON, HttpStatusCode.OK)
            },
        )

        repository.load("BOE-A-2026-21321")

        assertEquals(
            "http://api.test/v1/publicacion/BOE-A-2026-21321",
            checkNotNull(captured).url.toString(),
        )
    }

    /** Repositorio del detalle sobre un [MockEngine] con la configuración de test. */
    private fun repositoryWith(engine: MockEngine): HttpDetailRepository {
        val api = DetailApi(HttpClient(engine) { configureDatoPublicoClient(config) }, config)
        return HttpDetailRepository(api)
    }

    /** Responde [body] como JSON con el estado [status]. */
    private fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode) =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    private companion object {
        /** Respuesta `200` mínima del contrato `GET /v1/publicacion/{id}`. */
        const val DETAIL_JSON = """
        {
          "id": "BOE-A-2026-21321",
          "titulo": "Resolución de prueba",
          "fechaPublicacion": "2026-10-14",
          "organismo": "MINISTERIO DE LA PRESIDENCIA",
          "seccion": "II.A",
          "epigrafe": "Destinos",
          "categoria": "nombramientos_y_ceses",
          "urlOficial": "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21321",
          "resumen": {
            "queCambia": "Se resuelve la convocatoria.",
            "aQuienAfecta": "A la persona adjudicataria.",
            "cifrasClave": ["1 puesto", "Nivel 22"],
            "fuenteOficial": "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21321",
            "avisoIA": true
          },
          "plazo": { "fechaLimite": "2026-10-30" }
        }
        """
    }
}
