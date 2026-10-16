package es.aviferdev.datopublico.backend.persistence

import es.aviferdev.datopublico.backend.ingestion.publication.Publication
import es.aviferdev.datopublico.backend.rag.review.SummaryState
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.LocalDate
import javax.sql.DataSource

/**
 * Elemento del feed diario: la [publication] del BOE junto a su [summary]
 * ciudadano ya **publicado**.
 *
 * Es un valor **interno** (sin sufijo: no es DTO de transporte ni `Entity`).
 */
data class FeedItem(val publication: Publication, val summary: SummaryEntity)

/**
 * Lectura del **feed diario** en la tabla `resumen` (FT00026).
 *
 * Expone, para una fecha ISO-8601, las publicaciones del día que tienen un
 * resumen en `estado = 'publicado'` (token que escribe FT00023) y, desde FT00028,
 * las publicaciones de un **lote de identificadores** con resumen publicado (para
 * resolver los fragmentos de una búsqueda). Es una interfaz de **solo lectura**:
 * no anticipa filtros de búsqueda en SQL (el filtro semántico lo aplica
 * `rag/retrieval`) ni el detalle (FT00027).
 */
interface FeedRepository {
    /**
     * Lista las publicaciones con resumen **publicado** de [publicationDate]
     * (fecha ISO-8601), ordenadas por `id` ascendente.
     *
     * @param publicationDate fecha ISO-8601 (`2026-10-11`).
     * @return elementos del feed del día; vacío si no hay ninguno publicado.
     */
    fun listPublishedByDate(publicationDate: String): List<FeedItem>

    /**
     * Lista las publicaciones con resumen **publicado** cuyos identificadores
     * están en [ids] (lectura por lote, sin orden garantizado).
     *
     * Se usa para resolver en una sola consulta los fragmentos que devuelve la
     * búsqueda; el orden de relevancia lo reimpone el servicio. El filtro
     * `estado = 'publicado'` y la exclusión de categoría nula son los mismos que
     * en [listPublishedByDate].
     *
     * @param ids identificadores oficiales; si está vacío devuelve lista vacía
     *   **sin** abrir conexión.
     * @return los elementos publicados presentes; vacío si ninguno casa.
     */
    fun listPublishedByIds(ids: List<String>): List<FeedItem>
}

/**
 * Implementación JDBC de [FeedRepository] sobre un [DataSource].
 *
 * Una sola consulta combina `publicacion ⋈ resumen` y filtra `estado =
 * 'publicado'`; el `JOIN` garantiza 1:1. La fecha se enlaza como texto con el
 * *cast* `?::date` (patrón de FT00012/FT00017) y el SQL está **parametrizado**.
 * [listPublishedByIds] reutiliza las mismas columnas y el mismo mapeo, con el
 * lote enlazado como array (`ANY(?)`) y el token `SummaryState.PUBLISHED`.
 *
 * La lectura es **bloqueante** (JDBC) y debe invocarse fuera del hilo de eventos
 * HTTP (el servicio la envuelve en `Dispatchers.IO`). Se devuelve el **día
 * completo**, sin `LIMIT/OFFSET`: los destacados necesitan todo el conjunto y el
 * volumen diario del BOE es de cientos; la paginación se hace en memoria.
 *
 * Una fila sin `categoria` (columna nullable en `V2`, siempre rellena por
 * FT00012) es **dato corrupto** y se **excluye** del feed: no se inventa una
 * categoría y un solo registro corrupto no impide servir el resto del día.
 */
class FeedRepositoryJdbc(private val dataSource: DataSource) : FeedRepository {

    override fun listPublishedByDate(publicationDate: String): List<FeedItem> =
        dataSource.queryRows(
            SELECT_PUBLISHED_BY_DATE,
            { statement -> statement.setString(1, publicationDate) },
            { rows -> rows.toFeedItem() },
        ).filter { item -> item.publication.category != null }

    override fun listPublishedByIds(ids: List<String>): List<FeedItem> =
        if (ids.isEmpty()) {
            emptyList()
        } else {
            dataSource.queryRows(
                SELECT_PUBLISHED_BY_IDS,
                { statement -> statement.bindIds(ids) },
                { rows -> rows.toFeedItem() },
            ).filter { item -> item.publication.category != null }
        }

    /** Asigna el lote de identificadores (`ANY(?)`) y el token del estado publicado. */
    private fun PreparedStatement.bindIds(ids: List<String>) {
        setArray(1, connection.createArrayOf("text", ids.toTypedArray()))
        setString(2, SummaryState.PUBLISHED.persistedValue)
    }

    /** Reconstruye el [FeedItem] de la fila combinada reutilizando los mappers. */
    private fun ResultSet.toFeedItem(): FeedItem = FeedItem(
        publication = toFeedPublicationEntity().toDomain(),
        summary = toSummaryEntity(),
    )

    /**
     * Lee las columnas de `publicacion` (alias `p_`) como [PublicationEntity].
     *
     * Se usan alias distintos de los de `resumen` para poder reutilizar
     * [toSummaryEntity] (que lee `id`/`publicacion_id`…) en la misma fila.
     */
    private fun ResultSet.toFeedPublicationEntity(): PublicationEntity = PublicationEntity(
        id = getString("p_id"),
        title = getString("p_titulo"),
        publicationDate = getObject("p_fecha_publicacion", LocalDate::class.java).toString(),
        organization = getString("p_organismo"),
        section = getString("p_seccion"),
        heading = getString("p_epigrafe"),
        category = getString("p_categoria"),
        body = getString("p_texto"),
        officialUrl = getString("p_url_oficial"),
        xmlUrl = getString("p_url_xml"),
        pdfUrl = getString("p_url_pdf"),
        rank = getString("p_rango"),
        deadlineDate = getObject("p_plazo_fecha_limite", LocalDate::class.java)?.toString(),
        deadlineDescription = getString("p_plazo_descripcion"),
    )

    private companion object {
        const val PUBLICATION_COLUMNS =
            "p.id AS p_id, p.titulo AS p_titulo, p.fecha_publicacion AS p_fecha_publicacion, " +
                "p.organismo AS p_organismo, p.seccion AS p_seccion, p.epigrafe AS p_epigrafe, " +
                "p.categoria AS p_categoria, p.texto AS p_texto, " +
                "p.url_oficial AS p_url_oficial, p.url_xml AS p_url_xml, " +
                "p.url_pdf AS p_url_pdf, p.rango AS p_rango, " +
                "p.plazo_fecha_limite AS p_plazo_fecha_limite, " +
                "p.plazo_descripcion AS p_plazo_descripcion"

        const val SUMMARY_COLUMNS =
            "r.id, r.publicacion_id, r.que_cambia, r.a_quien_afecta, r.cifras_clave, " +
                "r.fuente_oficial, r.aviso_ia, r.estado, r.confianza"

        const val SELECT_PUBLISHED_BY_DATE = """
            SELECT $PUBLICATION_COLUMNS, $SUMMARY_COLUMNS
            FROM publicacion p
            JOIN resumen r ON r.publicacion_id = p.id
            WHERE p.fecha_publicacion = ?::date AND r.estado = 'publicado'
            ORDER BY p.id
        """

        const val SELECT_PUBLISHED_BY_IDS = """
            SELECT $PUBLICATION_COLUMNS, $SUMMARY_COLUMNS
            FROM publicacion p
            JOIN resumen r ON r.publicacion_id = p.id
            WHERE p.id = ANY(?::text[]) AND r.estado = ?
        """
    }
}
