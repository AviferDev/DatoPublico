package es.aviferdev.datopublico.backend.persistence

import es.aviferdev.datopublico.backend.ingesta.publicacion.Publicacion
import java.sql.ResultSet
import java.time.LocalDate
import javax.sql.DataSource

/**
 * Elemento del feed diario: la [publication] del BOE junto a su [summary]
 * ciudadano ya **publicado**.
 *
 * Es un valor **interno** (sin sufijo: no es DTO de transporte ni `Entity`).
 */
data class FeedItem(val publication: Publicacion, val summary: SummaryEntity)

/**
 * Lectura del **feed diario** en la tabla `resumen` (FT00026).
 *
 * Expone, para una fecha ISO-8601, las publicaciones del día que tienen un
 * resumen en `estado = 'publicado'` (token que escribe FT00023). Es una interfaz
 * de **solo lectura**: no anticipa filtros de búsqueda (FT00028) ni el detalle
 * (FT00027).
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
}

/**
 * Implementación JDBC de [FeedRepository] sobre un [DataSource].
 *
 * Una sola consulta combina `publicacion ⋈ resumen` y filtra `estado =
 * 'publicado'`; el `JOIN` garantiza 1:1. La fecha se enlaza como texto con el
 * *cast* `?::date` (patrón de FT00012/FT00017) y el SQL está **parametrizado**.
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
        ).filter { item -> item.publication.categoria != null }

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
    }
}
