package es.aviferdev.datopublico.feature.detail.domain

import es.aviferdev.datopublico.feature.detail.data.DetailRepository

/**
 * Caso de uso: obtener el detalle de una publicación por su identificador.
 *
 * La UI (el `DetailStateHolder`) llama siempre a este caso de uso y **nunca** al
 * repositorio directamente, según la arquitectura por capas de la skill
 * `compose-multiplatform-architecture`.
 *
 * @param repository puerto de datos del detalle.
 */
class GetPublicationDetail(private val repository: DetailRepository) {
    /**
     * Carga el detalle de la publicación indicada.
     *
     * @param id identificador oficial del BOE.
     * @return [DetailLoadResult.Success] con el detalle, [DetailLoadResult.NotFound]
     *   si la publicación no existe, o [DetailLoadResult.Failure] si el
     *   repositorio no pudo responder.
     */
    suspend operator fun invoke(id: String): DetailLoadResult = repository.load(id)
}
