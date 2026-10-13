package es.aviferdev.datopublico.feature.detail.data

import es.aviferdev.datopublico.feature.detail.domain.DetailLoadResult

/**
 * Puerto de datos del detalle de una publicación.
 *
 * Es la frontera que implementará FT00035 con el cliente HTTP real y la caché
 * offline sobre el contrato de `:shared`. Devuelve un resultado **tipado**
 * ([DetailLoadResult]) en vez de lanzar excepciones, para que los fallos y las
 * publicaciones inexistentes se modelen como estado de UI.
 */
interface DetailRepository {
    /**
     * Carga el detalle de la publicación indicada.
     *
     * @param id identificador oficial del BOE.
     * @return el detalle obtenido, o un resultado tipado de no encontrada/fallo;
     *   nunca propaga excepciones a la UI.
     */
    suspend fun load(id: String): DetailLoadResult
}
