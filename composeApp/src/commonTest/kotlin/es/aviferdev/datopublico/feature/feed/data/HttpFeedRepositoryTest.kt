package es.aviferdev.datopublico.feature.feed.data

import es.aviferdev.datopublico.core.network.ApiConfig
import es.aviferdev.datopublico.core.network.configureDatoPublicoClient
import es.aviferdev.datopublico.feature.feed.data.cache.FeedCache
import es.aviferdev.datopublico.feature.feed.data.cache.InMemoryFeedCache
import es.aviferdev.datopublico.feature.feed.data.remote.FeedApi
import es.aviferdev.datopublico.feature.feed.domain.DailyFeed
import es.aviferdev.datopublico.feature.feed.domain.FeedLoadResult
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
 * Tests del repositorio HTTP del feed (FT00035): el éxito cachea y devuelve el
 * feed; el fallo de red respalda a la cache si existe y devuelve `Failure` si no;
 * y la fecha inyectada viaja en la query.
 *
 * Usan [MockEngine] (sin red) y una cache en memoria.
 */
class HttpFeedRepositoryTest {

    private val config = ApiConfig(baseUrl = "http://api.test", requestTimeoutMillis = 1_000)

    @Test
    fun successfulLoadCachesTheFeedAndReturnsIt() = runTest {
        val cache = InMemoryFeedCache()
        val repository = repositoryWith(
            engine = MockEngine { respondJson(FEED_JSON, HttpStatusCode.OK) },
            cache = cache,
        )

        val result = repository.load()

        val success = assertIs<FeedLoadResult.Success>(result)
        assertEquals("2026-10-13", success.feed.date)
        assertEquals(success.feed, cache.read())
    }

    @Test
    fun failureWithoutCacheReturnsFailure() = runTest {
        val repository = repositoryWith(
            engine = MockEngine { throw IllegalStateException("sin conexión") },
            cache = InMemoryFeedCache(),
        )

        val result = repository.load()

        assertEquals(FeedLoadResult.Failure, result)
    }

    @Test
    fun failureWithCacheReturnsTheCachedFeed() = runTest {
        val cachedFeed = DailyFeed(date = "2026-10-12")
        val cache = InMemoryFeedCache()
        cache.write(cachedFeed)
        val repository = repositoryWith(
            engine = MockEngine { throw IllegalStateException("sin conexión") },
            cache = cache,
        )

        val result = repository.load()

        assertEquals(FeedLoadResult.Success(cachedFeed), result)
    }

    @Test
    fun serverErrorFallsBackToTheCacheToo() = runTest {
        val cachedFeed = DailyFeed(date = "2026-10-12")
        val cache = InMemoryFeedCache()
        cache.write(cachedFeed)
        val repository = repositoryWith(
            engine = MockEngine { respondJson("""{"codigo":"error_interno","mensaje":"fallo"}""", HttpStatusCode.InternalServerError) },
            cache = cache,
        )

        val result = repository.load()

        assertEquals(FeedLoadResult.Success(cachedFeed), result)
    }

    @Test
    fun injectedDateTravelsInTheQuery() = runTest {
        var captured: HttpRequestData? = null
        val repository = repositoryWith(
            engine = MockEngine { request ->
                captured = request
                respondJson(FEED_JSON, HttpStatusCode.OK)
            },
            cache = InMemoryFeedCache(),
            dateProvider = { "2026-01-02" },
        )

        repository.load()

        assertEquals("http://api.test/v1/feed?fecha=2026-01-02", checkNotNull(captured).url.toString())
    }

    /** Repositorio con un [MockEngine], una cache y una fecha inyectable. */
    private fun repositoryWith(
        engine: MockEngine,
        cache: FeedCache,
        dateProvider: () -> String = { "2026-10-13" },
    ): HttpFeedRepository {
        val api = FeedApi(HttpClient(engine) { configureDatoPublicoClient(config) }, config)
        return HttpFeedRepository(feedApi = api, cache = cache, dateProvider = dateProvider)
    }

    /** Responde [body] como JSON con el estado [status]. */
    private fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode) =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    private companion object {
        /** Respuesta `200` mínima del contrato `GET /v1/feed`. */
        const val FEED_JSON = """
        {
          "fecha": "2026-10-13",
          "publicaciones": [
            {
              "id": "BOE-A-2026-21001",
              "titulo": "Norma de prueba",
              "fechaPublicacion": "2026-10-13",
              "categoria": "normas_y_legislacion",
              "urlOficial": "https://www.boe.es/diario_boe/txt.php?id=BOE-A-2026-21001"
            }
          ],
          "destacados": [],
          "pagina": 1,
          "tamanoPagina": 20,
          "total": 1,
          "totalPaginas": 1
        }
        """
    }
}
