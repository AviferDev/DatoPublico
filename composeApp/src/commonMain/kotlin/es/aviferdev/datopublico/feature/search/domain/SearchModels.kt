package es.aviferdev.datopublico.feature.search.domain

import es.aviferdev.datopublico.feature.feed.domain.Publication
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory

/**
 * Modelos de **dominio** de la búsqueda en el cliente.
 *
 * Son modelos propios del cliente (no DTOs de red): los resultados reutilizan
 * [Publication] del feed porque `/v1/buscar` devuelve el **mismo** contrato
 * (`PublicacionDto`) que `/v1/feed` (ADR 0021/0023). Todos los identificadores van
 * en inglés; el KDoc, en castellano.
 *
 * Las fechas son cadenas **ISO-8601** (`aaaa-mm-dd`): en `commonMain` no se usa
 * `java.time` para no romper iOS/Wasm.
 */

/**
 * Filtros **opcionales** de la búsqueda, alineados con los parámetros del
 * endpoint `/v1/buscar` (`categoria`, `desde`, `hasta`).
 *
 * El filtro de «ámbito» (sección/organismo) que menciona el roadmap **no** está
 * aquí: el endpoint no lo expone (ver `docs/specs/api-busqueda.md`).
 *
 * @property category categoría curada por la que acotar; `null` si no se filtra.
 * @property fromDate fecha `desde` inclusive en ISO-8601; `null` si no se filtra.
 * @property toDate fecha `hasta` inclusive en ISO-8601; `null` si no se filtra.
 */
data class SearchFilters(
    val category: PublicationCategory? = null,
    val fromDate: String? = null,
    val toDate: String? = null,
)

/**
 * Resultado de una búsqueda: el **eco** de la consulta y las publicaciones que
 * casan (ya ordenadas por relevancia por el backend).
 *
 * @property query consulta enviada (eco del token `q`).
 * @property publications publicaciones resultado; puede estar vacía.
 */
data class SearchResults(
    val query: String,
    val publications: List<Publication>,
)

/**
 * Resultado **tipado** de buscar: un fallo es un valor esperado ([Failure]), no
 * una excepción, para que la UI lo muestre como estado de error con reintento y
 * la app nunca se cierre.
 */
sealed interface SearchLoadResult {
    /** El repositorio devolvió los resultados (que pueden venir vacíos). */
    data class Success(val results: SearchResults) : SearchLoadResult

    /** El repositorio no pudo completar la búsqueda. */
    data object Failure : SearchLoadResult
}

/**
 * Comprueba si [value] es una fecha ISO-8601 con la forma `aaaa-mm-dd`.
 *
 * Es una función **pura** y libre de plataforma (no usa `java.time`): valida solo
 * la forma (4-2-2 dígitos), no el calendario. El ViewModel la usa para **omitir**
 * fechas inválidas sin enviarlas ni provocar un error.
 *
 * @param value cadena a comprobar.
 * @return `true` si tiene la forma `aaaa-mm-dd`; `false` en caso contrario.
 */
internal fun isIsoDate(value: String): Boolean {
    val parts = value.split("-")
    val hasShape = parts.size == 3 &&
        parts[0].length == 4 &&
        parts[1].length == 2 &&
        parts[2].length == 2
    val isAllDigits = parts.all { part -> part.isNotEmpty() && part.all { it.isDigit() } }
    return hasShape && isAllDigits
}
