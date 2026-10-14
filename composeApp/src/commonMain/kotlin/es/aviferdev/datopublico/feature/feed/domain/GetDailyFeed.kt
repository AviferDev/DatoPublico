package es.aviferdev.datopublico.feature.feed.domain

import es.aviferdev.datopublico.feature.feed.data.FeedRepository

/**
 * Caso de uso: obtener el feed diario.
 *
 * La UI (el `FeedViewModel`) llama siempre a este caso de uso y **nunca** al
 * repositorio directamente, según la arquitectura por capas de la skill
 * `compose-multiplatform-architecture`.
 *
 * @param repository puerto de datos del feed.
 */
class GetDailyFeed(private val repository: FeedRepository) {
    /**
     * Carga el feed.
     *
     * @return [FeedLoadResult.Success] con el feed (posiblemente vacío) o
     *   [FeedLoadResult.Failure] si el repositorio no pudo responder.
     */
    suspend operator fun invoke(): FeedLoadResult = repository.load()
}
