package es.aviferdev.datopublico.feature.detail.domain

import es.aviferdev.datopublico.feature.feed.domain.Deadline
import es.aviferdev.datopublico.feature.feed.domain.PublicationCategory

/**
 * Modelos de **dominio** del detalle de una publicación en el cliente.
 *
 * Son modelos propios del cliente (no DTOs de red): el mapeo desde el contrato de
 * `:shared` (`PublicacionDto`/`ResumenDto`/`PlazoDto` → [PublicationDetail]) llega
 * con el cliente HTTP real en FT00035. Todos los identificadores van en inglés; el
 * KDoc, en castellano.
 *
 * Las fechas ([PublicationDetail.publishedDate] y [Deadline.limitDate]) son
 * cadenas **ISO-8601** (`aaaa-mm-dd`): en `commonMain` no se usa `java.time` para
 * no romper iOS/Wasm.
 */

/**
 * Detalle de una publicación tal y como lo muestra la pantalla.
 *
 * @property id identificador oficial del BOE (`BOE-A-aaaa-nnnnn`).
 * @property title título oficial de la disposición.
 * @property category categoría curada de la publicación.
 * @property publishedDate fecha de publicación en ISO-8601.
 * @property summary resumen ciudadano estructurado («¿Qué cambia?», «¿A quién
 *   afecta?» y «Cifras clave»).
 * @property deadline plazo de solicitud, solo en convocatorias; `null` si no
 *   aplica.
 * @property officialSourceUrl enlace a la fuente oficial del BOE.
 * @property showsAiNotice `true` si el resumen es generado por IA y, por tanto,
 *   hay que mostrar el aviso. En las publicaciones reales el contrato lo fija a
 *   `true` (`avisoIA`), así que el aviso es siempre visible.
 */
data class PublicationDetail(
    val id: String,
    val title: String,
    val category: PublicationCategory,
    val publishedDate: String,
    val summary: CitizenSummary,
    val deadline: Deadline?,
    val officialSourceUrl: String,
    val showsAiNotice: Boolean,
)

/**
 * Resumen ciudadano estructurado de una publicación.
 *
 * @property whatChanges bloque «¿Qué cambia?» (no vacío).
 * @property whoIsAffected bloque «¿A quién afecta?» (no vacío).
 * @property keyFigures cifras clave del resumen; puede estar vacía.
 */
data class CitizenSummary(
    val whatChanges: String,
    val whoIsAffected: String,
    val keyFigures: List<String>,
)

/**
 * Resultado **tipado** de cargar el detalle: un fallo o una publicación
 * inexistente son valores esperados ([Failure]/[NotFound]), no excepciones, para
 * que la UI los muestre como estado gestionado y la app nunca se cierre.
 */
sealed interface DetailLoadResult {
    /** El repositorio devolvió el detalle de la publicación. */
    data class Success(val detail: PublicationDetail) : DetailLoadResult

    /** El repositorio no conoce la publicación (espeja el `404` del endpoint). */
    data object NotFound : DetailLoadResult

    /** El repositorio no pudo devolver el detalle. */
    data object Failure : DetailLoadResult
}
