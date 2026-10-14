package es.aviferdev.datopublico.feature.feed.data

import es.aviferdev.datopublico.core.network.ApiResult
import es.aviferdev.datopublico.core.network.currentIsoDate
import es.aviferdev.datopublico.feature.feed.data.cache.FeedCache
import es.aviferdev.datopublico.feature.feed.data.remote.FeedApi
import es.aviferdev.datopublico.feature.feed.data.remote.FeedResponseDto
import es.aviferdev.datopublico.feature.feed.data.remote.toDailyFeed
import es.aviferdev.datopublico.feature.feed.domain.FeedLoadResult

/**
 * Repositorio del feed sobre el **cliente HTTP** y la **cache offline** (FT00035).
 *
 * Implementa el puerto [FeedRepository] de FT00032: pide el feed del día a
 * [feedApi]; en **éxito** mapea el DTO a dominio, **escribe la cache** y devuelve
 * [FeedLoadResult.Success]; en **fallo** lee la cache y, si existe, devuelve el
 * feed cacheado como [FeedLoadResult.Success] (contenido visible sin red) y, si no
 * existe, [FeedLoadResult.Failure]. Misma semántica que el repositorio en memoria:
 * un día vacío es `Success(feed vacío)` (la UI lo muestra como `Empty`), no un
 * fallo. Nunca lanza excepciones hacia la UI.
 *
 * @param feedApi cliente del feed diario.
 * @param cache cache offline del último feed.
 * @param dateProvider fecha ISO-8601 a pedir; por defecto, la del dispositivo
 *   ([currentIsoDate]); se inyecta en los tests.
 */
class HttpFeedRepository(
    private val feedApi: FeedApi,
    private val cache: FeedCache,
    private val dateProvider: () -> String = { currentIsoDate() },
) : FeedRepository {

    /**
     * Carga el feed del día con respaldo de cache.
     *
     * @return el feed de red (y lo cachea), el feed cacheado si la red falla, o
     *   [FeedLoadResult.Failure] si falla y no hay cache.
     */
    override suspend fun load(): FeedLoadResult {
        val result = when (val apiResult = feedApi.dailyFeed(dateProvider())) {
            is ApiResult.Success -> cacheFeed(apiResult.data)
            is ApiResult.Failure -> cachedOrFailure()
        }
        return result
    }

    /** Mapea el DTO a dominio, lo cachea y devuelve el éxito. */
    private suspend fun cacheFeed(response: FeedResponseDto): FeedLoadResult {
        val feed = response.toDailyFeed()
        cache.write(feed)
        return FeedLoadResult.Success(feed)
    }

    /** Devuelve el feed cacheado o un fallo si todavía no hay cache. */
    private suspend fun cachedOrFailure(): FeedLoadResult {
        val cached = cache.read()
        return cached?.let { feed -> FeedLoadResult.Success(feed) } ?: FeedLoadResult.Failure
    }
}
